package com.github.build;

import com.github.build.compile.CompileService;
import com.github.build.deps.DependencyConstraints;
import com.github.build.deps.DependencyService;
import com.github.build.deps.DependencyServiceImpl;
import com.github.build.deps.GroupArtifactVersion;
import com.github.build.deps.LocalRepository;
import com.github.build.deps.MavenArtifactResolverDependencyService;
import com.github.build.deps.RemoteRepositoryImpl;
import com.github.build.jar.JarService;
import com.github.build.test.JUnitTestArgs;
import com.github.build.test.TestResults;
import com.github.build.test.TestService;
import com.sun.codemodel.CodeWriter;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.writer.FilterCodeWriter;
import com.sun.tools.xjc.AbortException;
import com.sun.tools.xjc.ErrorReceiver;
import com.sun.tools.xjc.Language;
import com.sun.tools.xjc.ModelLoader;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.model.Model;
import com.sun.tools.xjc.outline.Outline;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import tools.jackson.databind.ObjectMapper;

public class BuildItself {

  private static final Logger log = LoggerFactory.getLogger(BuildItself.class);

  public static void main(final String[] args) {
    final Path workdir;
    if (args.length == 0) {
      workdir = Path.of("").toAbsolutePath();
    } else {
      workdir = Path.of(args[0]);
    }

    final var compileService = new CompileService();

    final DependencyService dependencyService = mavenArtifactResolver();
    final var testService = new TestService(dependencyService);
    final var jarService = new JarService();
    final BuildService service = new BuildService(compileService, dependencyService, jarService);
    final DependencyConstraints junitBom = dependencyService.getConstraints(
        GroupArtifactVersion.parse("org.junit:junit-bom:6.0.1")
    );
    final var main = SourceSet
        .withMainDefaults()
        .withSourceDir(Path.of("build").resolve("generated-sources").resolve("xjc"))
        .compileAndRunWith("org.apache.maven:maven-artifact:3.9.11")
        .compileWith(
            "org.jspecify:jspecify:1.0.0",
            "org.slf4j:slf4j-api:2.0.17",
            "org.apache.maven:maven-resolver-provider:3.9.9",
            "org.apache.maven.resolver:maven-resolver-supplier:1.9.22",
            "jakarta.xml.bind:jakarta.xml.bind-api:4.0.2",
            "org.junit.platform:junit-platform-launcher:1.13.4",
            "tools.jackson.core:jackson-databind:3.0.3"
        )
        .build();
    final var test = SourceSet
        .withTestDefaults()
        .withDependencyConstraints(junitBom)
        .compileAndRunWith(main)
        .compileAndRunWith(
            "org.apache.maven:maven-resolver-provider:3.9.9",
            "org.apache.maven.resolver:maven-resolver-supplier:1.9.22",
            "org.junit.jupiter:junit-jupiter-api",
            "org.junit.jupiter:junit-jupiter-params",
            "org.assertj:assertj-core:3.27.3",
            "ch.qos.logback:logback-classic:1.5.21",
            "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.20.0",
            "tools.jackson.core:jackson-databind:3.0.3"
        )
        .runWith(
            "org.junit.jupiter:junit-jupiter-engine",
            "com.sun.xml.bind:jaxb-impl:4.0.5"
        )
        .build();
    final var project = Project
        .withId("build-system-lib")
        .withPath(Path.of("lib"))
        .withSourceSet(main)
        .withSourceSet(test)
        .build();

    generateSourcesFromMavenXsd(workdir, project);
    final boolean mainCompiled = service.compileMain(workdir, project);
    if (!mainCompiled) {
      log.error("Build failed");
      System.exit(1);
      return;
    }
    service.copyResources(workdir, project, SourceSet.Id.MAIN);

    final boolean testCompiled = service.compileTest(workdir, project);
    if (!testCompiled) {
      log.error("Build failed");
      System.exit(1);
      return;
    }
    service.copyResources(workdir, project, SourceSet.Id.TEST);

    final String buildRuntimePathStr = System.getProperty("buildRuntimePath");
    final Path buildRuntimePath = Path.of(buildRuntimePathStr);
    final var testArgs = new JUnitTestArgs(
        Set.of(buildRuntimePath),
        ClassLoader.getPlatformClassLoader()
    );
    final TestResults results = testService.withJUnit(workdir, project, testArgs);
    if (results.testsFailedCount() > 0) {
      log.error("Build failed");
      System.exit(1);
    }
  }

  private static void generateSourcesFromMavenXsd(final Path workdir, final Project project) {
    log.info("[project={}] Generating Maven XSD sources with XJC", project.id());

    final var errorReceiver = new LoggingErrorReceiver();
    final var files = List.of(
        workdir
            .resolve(project.path())
            .resolve("maven-schemas")
            .resolve("maven-4.0.0.xsd"),
        workdir
            .resolve(project.path())
            .resolve("maven-schemas")
            .resolve("repository-metadata-1.1.0.xsd")
    );
    final Path targetDir = workdir
        .resolve(project.path())
        .resolve(project.artifactLayout().rootDir())
        .resolve("generated-sources")
        .resolve("xjc");
    try {
      Files.createDirectories(targetDir);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    for (final Path xsdPath : files) {
      final Options options;
      {
        options = new Options();

        final var inputSource = new InputSource();
        inputSource.setSystemId(xsdPath.toString());
        options.addGrammar(inputSource);
        options.targetDir = targetDir.toFile();
        options.encoding = StandardCharsets.UTF_8.name();
        options.setSchemaLanguage(Language.XMLSCHEMA);
      }

      final Model model = ModelLoader.load(options, new JCodeModel(), errorReceiver);
      if (model == null) {
        throw new IllegalStateException();
      }

      options.classNameReplacer.forEach(model.codeModel::addClassNameReplacer);
      final Outline outline = model.generateCode(model.options, errorReceiver);
      if (outline == null) {
        throw new IllegalStateException();
      }

      final CodeWriter codeWriter;
      try {
        codeWriter = outline.getModel().options.createCodeWriter();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }

      try {
        outline.getModel().codeModel.build(new LoggingCodeWriter(codeWriter));
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  private static final class LoggingErrorReceiver extends ErrorReceiver {

    @Override
    public void error(final SAXParseException exception) throws AbortException {
      log.error("Failed to parse Maven XSD", exception);
    }

    @Override
    public void fatalError(final SAXParseException exception) throws AbortException {
      log.error("Failed to parse Maven XSD", exception);
    }

    @Override
    public void warning(final SAXParseException exception) throws AbortException {
      log.warn("Failed to parse Maven XSD", exception);
    }

    @Override
    public void info(final SAXParseException exception) {
      log.info("Failed to parse Maven XSD", exception);
    }
  }

  private static final class LoggingCodeWriter extends FilterCodeWriter {

    public LoggingCodeWriter(final CodeWriter core) {
      super(core);
    }

    @Override
    public Writer openSource(final JPackage pkg, final String fileName) throws IOException {
      log.debug("XJC writing source {} {}", pkg, fileName);
      return super.openSource(pkg, fileName);
    }

    @Override
    public OutputStream openBinary(final JPackage pkg, final String fileName) throws IOException {
      log.debug("XJC writing binary {} {}", pkg, fileName);
      return super.openBinary(pkg, fileName);
    }
  }

  private static DependencyService nativeDependencyService() {
    final var httpClient = HttpClient.newHttpClient();
    final String nexusHost = Objects.requireNonNullElse(
        System.getenv("NEXUS_HOST"),
        "localhost"
    );
    final var nexusDocker = new RemoteRepositoryImpl(
        URI.create("http://" + nexusHost + ":8081/repository/maven-central"),
        httpClient,
        new ObjectMapper()
    );

    final Path localRepositoryBasePath;
    try {
      localRepositoryBasePath = Files.createTempDirectory("build-local");
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    try {
      Files.createDirectory(localRepositoryBasePath);
    } catch (final IOException e) {
      if (!(e instanceof FileAlreadyExistsException)) {
        throw new UncheckedIOException(e);
      }
    }
    final var localRepository = new LocalRepository(
        localRepositoryBasePath,
        Map.of("sha256", "SHA-256")
    );
    return new DependencyServiceImpl(
        List.of(nexusDocker),
        localRepository
    );
  }

  private static DependencyService mavenArtifactResolver() {
    final RepositorySystem repoSystem = new RepositorySystemSupplier().get();
    final DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
    session.setSystemProperty("java.version", "21");

    final Path localRepositoryBasePath;
    try {
      localRepositoryBasePath = Files.createTempDirectory("build-local");
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    final var localRepo = new org.eclipse.aether.repository.LocalRepository(
        localRepositoryBasePath.toFile()
    );
    final var manager = repoSystem.newLocalRepositoryManager(session, localRepo);
    session.setLocalRepositoryManager(manager);

    final String nexusHost = Objects.requireNonNullElse(
        System.getenv("NEXUS_HOST"),
        "localhost"
    );
    final List<org.eclipse.aether.repository.RemoteRepository> repositories = List.of(
        new org.eclipse.aether.repository.RemoteRepository
            .Builder("nexus", "default", "http://" + nexusHost + ":8081/repository/maven-central")
            .build()
    );
    return new MavenArtifactResolverDependencyService(repoSystem, session, repositories);
  }
}
