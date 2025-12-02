package com.github.build;

import com.github.build.compile.CompileService;
import com.github.build.deps.DependencyService;
import com.github.build.deps.LocalRepository;
import com.github.build.deps.RemoteRepository;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import tools.jackson.databind.ObjectMapper;

public class BuildItself {

  private static final Logger log = LoggerFactory.getLogger(BuildItself.class);

  public static void main(final String[] args) {
    final var compileService = new CompileService();

    final var httpClient = HttpClient.newHttpClient();
    final String nexusHost = Objects.requireNonNullElse(
        System.getenv("NEXUS_HOST"),
        "localhost"
    );
    final var nexusDocker = new RemoteRepository(
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
    final var dependencyService = new DependencyService(
        List.of(nexusDocker),
        localRepository
    );
    final var testService = new TestService(dependencyService);
    final BuildService service = new BuildService(compileService, dependencyService);
    final Path workdir = Path.of(args[0]);
    final var main = SourceSet
        .withMainDefaults()
        .withSourceDir(Path.of("build").resolve("generated-sources").resolve("xjc"))
        .compileWith(
            "org.jspecify:jspecify:1.0.0",
            "org.slf4j:slf4j-api:2.0.17",
            "jakarta.xml.bind:jakarta.xml.bind-api:4.0.2",
            "org.junit.platform:junit-platform-launcher:1.13.4"
        )
        .build();
    final var test = SourceSet
        .withTestDefaults()
        .compileAndRunWith(main)
        .compileAndRunWith(
            "org.junit.jupiter:junit-jupiter-api:5.13.4",
            "org.junit.jupiter:junit-jupiter-params:5.13.4",
            "org.assertj:assertj-core:3.27.3",
            "ch.qos.logback:logback-classic:1.5.21",
            "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.20.0"
        )
        .runWith(
            "org.junit.jupiter:junit-jupiter-engine:5.13.4",
            "org.junit.platform:junit-platform-launcher:1.13.4",
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
    service.compileMain(workdir, project);
    service.copyResources(workdir, project, SourceSet.Id.MAIN);

    service.compileTest(workdir, project);
    service.copyResources(workdir, project, SourceSet.Id.TEST);

    final TestResults results = testService.withJUnit(workdir, project);
    if (results.testsFailedCount() > 0) {
      log.error("Build failed");
      System.exit(1);
    }
  }

  private static void generateSourcesFromMavenXsd(final Path workdir, final Project project) {
    log.info("[project={}] Generating Maven XSD sources with XJC", project.id());

    final var errorReceiver = new LoggingErrorReceiver();

    final Options options;
    {
      options = new Options();

      final Path xsdPath = workdir
          .resolve(project.path())
          .resolve("maven-4.0.0.xsd");

      final var inputSource = new InputSource();
      inputSource.setSystemId(xsdPath.toString());
      options.addGrammar(inputSource);

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
}
