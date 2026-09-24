package com.github.build;

import com.github.build.compile.CompileService;
import com.github.build.compile.CompilerOptions;
import com.github.build.deps.DependencyConstraints;
import com.github.build.deps.DependencyService;
import com.github.build.deps.DependencyServiceImpl;
import com.github.build.deps.GroupArtifact;
import com.github.build.deps.GroupArtifactVersion;
import com.github.build.deps.LocalRepository;
import com.github.build.deps.RemoteRepositoryImpl;
import com.github.build.deps.maven.MavenArtifactResolverDependencyService;
import com.github.build.jar.JarService;
import com.github.build.test.TestResults;
import com.github.build.test.TestService;
import com.github.build.test.junit.JUnitTestArgs;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

public class BuildItself {

  private static final Logger log = LoggerFactory.getLogger(BuildItself.class);

  private static final DependencyService dependencyService = mavenArtifactResolver();

  private static final TestService testService = new TestService(dependencyService);

  private static final BuildService buildService = new BuildService(
      new CompileService(),
      dependencyService,
      new JarService()
  );

  private static final ProjectService projectService = new ProjectService();

  public static void main(final String[] args) {
    final Path workdir;
    if (args.length == 0) {
      workdir = Path.of("").toAbsolutePath();
    } else {
      workdir = Path.of(args[0]);
    }

    final DependencyConstraints junitBom = dependencyService.getConstraints(
        GroupArtifactVersion.parse("org.junit:junit-bom:6.0.1")
    );

    final CompilerOptions java21 = CompilerOptions
        .builder()
        .release("21")
        .build();

    final Project projectTestUtils = createProjectTestUtils(junitBom);
    final Project projectLib = createProjectLib(junitBom, projectTestUtils);

    buildService.clean(workdir, projectTestUtils);
    buildService.clean(workdir, projectLib);

    if (!buildTestUtils(workdir, projectTestUtils, java21)) {
      log.info("Build failed");
      System.exit(1);
      return;
    }

    if (!buildLib(workdir, projectLib, java21)) {
      log.info("Build failed");
      System.exit(1);
      return;
    }

    log.info("Build finished successfully");
  }

  private static Project createProjectTestUtils(final DependencyConstraints junitBom) {
    final var main = new MainSourceSetArgs(
        SourceSet.Id.MAIN.toString(),
        Set.of(Path.of("src", "main", "java")),
        Set.of(Path.of("src", "main", "resources")),
        List.of(
            GroupArtifactVersion.parse("org.jspecify:jspecify:1.0.0"),
            GroupArtifactVersion.parse(
                "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.20.0"
            )
        ),
        List.of(
            GroupArtifactVersion.parse(
                "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.20.0"
            )
        ),
        DependencyConstraints.EMPTY
    );
    final var test = new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        Set.of(Path.of("src", "test", "java")),
        Set.of(Path.of("src", "test", "resources")),
        List.of(),
        List.of(GroupArtifact.parse("org.junit.jupiter:junit-jupiter-engine")),
        junitBom
    );
    return projectService.create(
        "com.github.build", "build-system-test-utils", "0.1.0",
        builder -> builder
            .withPath(Path.of("test-utils"))
            .withSourceSets(main, test)
    );
  }

  private static Project createProjectLib(
      final DependencyConstraints junitBom,
      final Project projectTestUtils
  ) {
    final var main = new MainSourceSetArgs(
        SourceSet.Id.MAIN.toString(),
        Set.of(
            Path.of("src", "main", "java"),
            Path.of("build", "generated-sources", "xjc")
        ),
        Set.of(Path.of("src", "main", "resources")),
        List.of(
            GroupArtifactVersion.parse("org.apache.maven:maven-artifact:3.9.12"),
            GroupArtifactVersion.parse("org.apache.maven:maven-model:3.9.12"),
            GroupArtifactVersion.parse("org.jspecify:jspecify:1.0.0"),
            GroupArtifactVersion.parse("org.slf4j:slf4j-api:2.0.17"),
            GroupArtifactVersion.parse("org.apache.maven:maven-resolver-provider:3.9.9"),
            GroupArtifactVersion.parse("org.apache.maven.resolver:maven-resolver-supplier:1.9.22"),
            GroupArtifactVersion.parse("jakarta.xml.bind:jakarta.xml.bind-api:4.0.2"),
            GroupArtifactVersion.parse("org.junit.platform:junit-platform-launcher:1.13.4")
        ),
        List.of(
            GroupArtifactVersion.parse("org.apache.maven:maven-artifact:3.9.12"),
            GroupArtifactVersion.parse("org.apache.maven:maven-model:3.9.12")
        ),
        DependencyConstraints.EMPTY
    );

    final var testCompileAndRun = new ArrayList<TestSourceSetDependency>();
    testCompileAndRun.add(main);
    testCompileAndRun.add(projectTestUtils);
    testCompileAndRun.add(GroupArtifactVersion.parse(
        "org.apache.maven:maven-resolver-provider:3.9.9"
    ));
    testCompileAndRun.add(GroupArtifactVersion.parse(
        "org.apache.maven.resolver:maven-resolver-supplier:1.9.22"
    ));
    testCompileAndRun.add(GroupArtifact.parse("org.junit.jupiter:junit-jupiter-api"));
    testCompileAndRun.add(GroupArtifact.parse("org.junit.jupiter:junit-jupiter-params"));
    testCompileAndRun.add(GroupArtifactVersion.parse("org.assertj:assertj-core:3.27.3"));
    testCompileAndRun.add(GroupArtifactVersion.parse("ch.qos.logback:logback-classic:1.5.21"));
    testCompileAndRun.add(GroupArtifactVersion.parse(
        "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.20.0"
    ));

    final var testRuntime = new ArrayList<TestSourceSetDependency>(testCompileAndRun);
    testRuntime.add(GroupArtifact.parse("org.junit.jupiter:junit-jupiter-engine"));
    testRuntime.add(GroupArtifactVersion.parse("com.sun.xml.bind:jaxb-impl:4.0.5"));

    final var test = new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        Set.of(Path.of("src", "test", "java")),
        Set.of(Path.of("src", "test", "resources")),
        testCompileAndRun,
        testRuntime,
        junitBom
    );
    return projectService.create(
        "com.github.build", "build-system-lib", "0.1.0",
        builder -> builder
            .withPath(Path.of("lib"))
            .withSourceSets(main, test)
    );
  }

  private static boolean buildTestUtils(
      final Path workdir,
      final Project project,
      final CompilerOptions compilerOptions
  ) {
    log.info("Building {}", project.artifactId());
    final boolean mainCompiled = buildService.compileMain(workdir, project, compilerOptions);
    if (!mainCompiled) {
      return false;
    }

    buildService.copyResources(workdir, project, SourceSet.Id.MAIN);
    buildService.createJar(workdir, project, Map.of(), null);

    final boolean testCompiled = buildService.compileTest(workdir, project, compilerOptions);
    if (!testCompiled) {
      return false;
    }

    buildService.copyResources(workdir, project, SourceSet.Id.TEST);

    final String buildRuntimePathStr = System.getProperty("buildRuntimePath");
    final List<Path> buildRuntimePath = Stream
        .of(buildRuntimePathStr.split(",", -1))
        .map(Path::of)
        .toList();
    final var testArgs = new JUnitTestArgs(buildRuntimePath, ClassLoader.getSystemClassLoader());
    final TestResults results = testService.withJUnit(workdir, project, testArgs);
    return results.testsFailedCount() <= 0;
  }

  private static boolean buildLib(
      final Path workdir,
      final Project project,
      final CompilerOptions compilerOptions
  ) {
    log.info("Building {}", project.artifactId());
    generateSourcesFromMavenXsd(workdir, project);
    final boolean mainCompiled = buildService.compileMain(workdir, project, compilerOptions);
    if (!mainCompiled) {
      return false;
    }

    buildService.copyResources(workdir, project, SourceSet.Id.MAIN);
    buildService.createJar(workdir, project, Map.of(), null);

    final boolean testCompiled = buildService.compileTest(workdir, project, compilerOptions);
    if (!testCompiled) {
      return false;
    }

    buildService.copyResources(workdir, project, SourceSet.Id.TEST);

    final String buildRuntimePathStr = System.getProperty("buildRuntimePath");
    final List<Path> buildRuntimePath = Stream
        .of(buildRuntimePathStr.split(",", -1))
        .map(Path::of)
        .toList();
    final var testArgs = new JUnitTestArgs(buildRuntimePath, ClassLoader.getSystemClassLoader());
    final TestResults results = testService.withJUnit(workdir, project, testArgs);
    return results.testsFailedCount() <= 0;
  }

  private static void generateSourcesFromMavenXsd(final Path workdir, final Project project) {
    log.info("[project={}] Generating Maven XSD sources with XJC", project.artifactId());

    final var errorReceiver = new LoggingErrorReceiver();
    final var files = List.of(
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
        httpClient
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
    // keep provided dependencies in the collected graph; resolveCompileClasspath
    // only exposes them as direct dependencies of the compiled project
    session.setDependencySelector(new AndDependencySelector(
        new ScopeDependencySelector("test"),
        new OptionalDependencySelector(),
        new ExclusionDependencySelector()
    ));

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
