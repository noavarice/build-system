package com.github.build;

import static java.util.stream.Collectors.toUnmodifiableMap;

import com.github.build.compile.CompilationResult;
import com.github.build.compile.JavaCompileArgs;
import com.github.build.compile.JavaCompileService;
import com.github.build.compile.JavaCompilerOptions;
import com.github.build.deps.Dependency;
import com.github.build.deps.DependencyConstraints;
import com.github.build.deps.DependencyService;
import com.github.build.deps.GroupArtifact;
import com.github.build.deps.GroupArtifactVersion;
import com.github.build.deps.maven.MavenArtifactResolverDependencyService;
import com.github.build.deps.maven.ProjectWorkspaceReader;
import com.github.build.jar.JarArgs;
import com.github.build.jar.JarManifest;
import com.github.build.jar.JarService;
import com.github.build.test.TestResults;
import com.github.build.test.TestService;
import com.github.build.test.junit.JUnitTestArgs;
import com.github.build.util.FileUtils;
import com.github.build.util.JavaCommandBuilder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author noavarice
 * @since 1.0.0
 */
public final class BuildSpringSecurity {

  private static final Logger log = LoggerFactory.getLogger(BuildSpringSecurity.class);

  private BuildSpringSecurity() {
  }

  public static void main(final String[] args) {
    final Path workdir;
    if (args.length == 0) {
      workdir = Path.of("").toAbsolutePath();
    } else {
      workdir = Path.of(args[0]).toAbsolutePath();
    }

    final var projectService = new ProjectService();
    final var dependencyService = mavenArtifactResolver(workdir, projectService);
    final var testService = new TestService(dependencyService);
    final var jarService = new JarService();
    final BuildService service = new BuildService(
        new JavaCompileService(), dependencyService, jarService
    );

    final DependencyConstraints platform = getPlatform(dependencyService);
    // TODO: consider setting this default via ProjectService
    final var artifactLayout = new Project.ArtifactLayout(
        Path.of("build-system"),
        Path.of("classes"),
        Path.of("resources")
    );
    final Project crypto = createProjectCrypto(projectService, platform, artifactLayout);
    final Project core = createProjectCore(projectService, platform, artifactLayout, crypto);
    final Project acl = createProjectAcl(projectService, platform, artifactLayout, core);
    final Project data = createProjectData(projectService, platform, artifactLayout, core);
    final Project web = createProjectWeb(projectService, platform, artifactLayout, core);
    final Project messaging =
        createProjectMessaging(projectService, platform, artifactLayout, core, web);
    final Project access =
        createProjectAccess(projectService, platform, artifactLayout, core, web, acl, messaging);
    final Project aspects = createProjectAspects(projectService, platform, artifactLayout, access);
    final Project cas = createProjectCas(
        projectService, platform, artifactLayout, core, web
    );
    final Project oauth2Core = createProjectOauth2Core(
        projectService, platform, artifactLayout, core
    );
    final Project taglibs = createProjectTaglibs(
        projectService, platform, artifactLayout, core, web, acl, access
    );
    final Project kerberosCore = createProjectKerberosCore(
        projectService, platform, artifactLayout, core
    );
    final Project kerberosTest = createProjectKerberosTest(
        projectService, platform, artifactLayout
    );
    final Project ldap = createProjectLdap(projectService, platform, artifactLayout, core);
    final Project saml2 = createProjectSaml2(projectService, platform, artifactLayout, web);
    final Project oauth2Jose = createProjectOauth2Jose(
        projectService, platform, artifactLayout, core, oauth2Core
    );
    final Project oauth2Client = createProjectOauth2Client(
        projectService, platform, artifactLayout, core, web, oauth2Core, oauth2Jose
    );
    final Project oauth2ResourceServer = createProjectOauth2ResourceServer(
        projectService, platform, artifactLayout, core, web, oauth2Core, oauth2Jose
    );
    final Project oauth2AuthorizationServer = createProjectOauth2AuthorizationServer(
        projectService, platform, artifactLayout, web, oauth2Core, oauth2Jose, oauth2ResourceServer
    );
    final Project rsocket = createProjectRsocket(
        projectService, platform, artifactLayout, core, oauth2ResourceServer
    );
    final Project webauthn = createProjectWebauthn(
        projectService, platform, artifactLayout, core, web
    );
    final Project config = createProjectConfig(
        projectService, platform, artifactLayout, core, data, web, messaging, access, ldap, saml2,
        oauth2Client, oauth2Jose, oauth2ResourceServer, oauth2AuthorizationServer, rsocket, webauthn
    );
    final Project test = createProjectTest(
        projectService, platform, artifactLayout, core, web, oauth2Jose, oauth2Client,
        oauth2ResourceServer, config
    );
    final List<Project> projects = List.of(crypto, core, acl, data, web, messaging, access, aspects,
        cas, oauth2Core, taglibs, kerberosCore, kerberosTest, ldap, saml2, oauth2Jose, oauth2Client,
        oauth2ResourceServer, oauth2AuthorizationServer, rsocket, webauthn, config, test);
    // These modules currently build main source sets only: their test source sets need build
    // features the project does not support yet (generated sources, extra source sets,
    // classifier-qualified runtime artifacts, Gradle-style highest-version conflict resolution).
    // See BLOCKED_MODULES.md.
    final Set<Project> projectsWithoutTests = Set.of(ldap, saml2, oauth2Jose, oauth2Client,
        oauth2ResourceServer, oauth2AuthorizationServer, rsocket, webauthn, config, test);

    projects.forEach(project -> service.clean(workdir, project));

    final Path license = workdir.resolve("LICENSE.txt");
    final var compilerOptions = JavaCompilerOptions
        .builder()
        .release("17")
        .parameters(true)
        .build();
    final var springCore = GroupArtifact.parse("org.springframework:spring-core");
    final String springCoreVersion = platform.getConstraint(springCore);

    final var mockitoGa = GroupArtifact.parse("org.mockito:mockito-core");
    final GroupArtifactVersion mockito = mockitoGa.withVersion(platform.getConstraint(mockitoGa));

    final var jacoco = GroupArtifactVersion.parse("org.jacoco:org.jacoco.agent:0.8.9");

    final var mockitoPath = dependencyService.fetchToLocal(mockito, null);
    final var jacocoPath = dependencyService.fetchToLocal(jacoco, "runtime");

    // TODO: call dedicated ProjectService method that enforces project build order
    for (final Project project : projects) {
      log.info("[project={}] Compiling main source set", project.artifactId());
      final List<Path> mainClassDirectories;
      if (project == aspects) {
        final CompilationResult aspectJ = service.compileAspectJ(
            workdir, project.mainSourceSet()
        );
        if (!aspectJ.successful()) {
          log.error("Build failed");
          System.exit(1);
          return;
        }
        mainClassDirectories = List.of(aspectJ.outputDirectory());
      } else if (project == config) {
        final CompilationResult kotlin = service.compileKotlin(workdir, project.mainSourceSet());
        final CompilationResult java = service.compileJava(
            workdir, project.mainSourceSet(), compilerOptions
        );
        if (!kotlin.successful() || !java.successful()) {
          log.error("Build failed");
          System.exit(1);
          return;
        }
        mainClassDirectories = List.of(kotlin.outputDirectory(), java.outputDirectory());
      } else {
        final CompilationResult java = service.compileJava(
            workdir, project.mainSourceSet(), compilerOptions
        );
        if (!java.successful()) {
          log.error("Build failed");
          System.exit(1);
          return;
        }
        mainClassDirectories = List.of(java.outputDirectory());
      }
      service.copyResources(workdir, project, SourceSet.Id.MAIN);
      if (project == core) {
        generateSpringVersionsFile(workdir, project, springCoreVersion);
      }
      if (project == saml2) {
        // saml2 adds the opensaml5Main source set output to its main jar; main output wins on
        // duplicate classes (one shared Saml2Utils class).
        if (!compileOpenSaml5Main(workdir, project, service, compilerOptions)) {
          log.error("Build failed");
          System.exit(1);
          return;
        }
      }

      final var additionalEntries = new HashMap<Path, JarArgs.Content>();
      additionalEntries.put(Path.of("META-INF/LICENSE.txt"), new JarArgs.Content.File(license));
      if (project == web) {
        // web depends on JS module - building it and add output to JAR
        final Path jsDistDir = buildJsModule(workdir);
        final Map<Path, JarArgs.Content> jsFiles = prepareJsFilesForJar(jsDistDir);
        additionalEntries.putAll(jsFiles);
      }

      final var manifest = JarManifest
          .builder()
          .setVersion("1.0")
          .setCreatedBy(
              System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ')'
          )
          .setImplementationTitle(project.artifactId())
          .setImplementationVersion(project.version())
          .build();
      service.createJar(
          workdir, project.mainSourceSet(), mainClassDirectories, additionalEntries, manifest
      );

      if (projectsWithoutTests.contains(project)) {
        log.info("[project={}] Skipping test source set (not supported yet)",
            project.artifactId());
        continue;
      }

      log.info("[project={}] Compiling test source set", project.artifactId());
      final SourceSet testSourceSet = project.testSourceSet();

      buildExtraSourceSets(testSourceSet, compilerOptions, workdir, service);

      final List<Path> testClassDirectories;
      if (project == aspects) {
        final CompilationResult aspectJ = service.compileAspectJ(
            workdir, project.testSourceSet()
        );
        if (!aspectJ.successful()) {
          log.error("Build failed");
          System.exit(1);
          return;
        }
        testClassDirectories = List.of(aspectJ.outputDirectory());
      } else if (project == config) {
        final CompilationResult kotlin = service.compileKotlin(workdir, project.testSourceSet());
        final CompilationResult java = service.compileJava(
            workdir, project.testSourceSet(), compilerOptions
        );
        if (!kotlin.successful() || !java.successful()) {
          log.error("Build failed");
          System.exit(1);
          return;
        }
        testClassDirectories = List.of(kotlin.outputDirectory(), java.outputDirectory());
      } else {
        final CompilationResult java = service.compileJava(
            workdir, project.testSourceSet(), compilerOptions
        );
        if (!java.successful()) {
          log.error("Build failed");
          System.exit(1);
          return;
        }
        testClassDirectories = List.of(java.outputDirectory());
      }
      service.copyResources(workdir, project, SourceSet.Id.TEST);
      service.createJar(workdir, project.testSourceSet(), testClassDirectories, Map.of(), null);

      final String buildRuntimePathStr = System.getProperty("buildRuntimePath");
      final List<Path> buildRuntimePath = Stream
          .of(buildRuntimePathStr.split(",", -1))
          .map(Path::of)
          .map(Path::toAbsolutePath)
          .toList();
      final var testArgs = new JUnitTestArgs(buildRuntimePath, ClassLoader.getSystemClassLoader());
      log.info("[project={}] Running tests", project.artifactId());

      final var jacocoExecReportPath = workdir
          .resolve(project.path())
          .resolve(project.artifactLayout().rootDir())
          .resolve("jacoco")
          .resolve("test.exec");
      final List<JavaCommandBuilder.Agent> agents = List.of(
          new JavaCommandBuilder.Agent(mockitoPath, null),
          new JavaCommandBuilder.Agent(jacocoPath, "destfile=" + jacocoExecReportPath)
      );
      final TestResults results = testService.withJUnitAsProcess(
          workdir,
          project,
          testArgs,
          agents,
          List.of("springSecurityVersion=7.0.0", "springVersion=" + springCoreVersion),
          Duration.ofMinutes(10)
      );

      log.info("[project={}] {} tests succeeded, {} tests failed, {} tests skipped",
          project.artifactId(),
          results.testsSucceededCount(),
          results.testsFailedCount(),
          results.testsSkippedCount()
      );
      if (results.testsFailedCount() > 0) {
        log.error("Build failed");
        System.exit(1);
      }
    }
  }

  private static Path buildJsModule(final Path workdir) {
    final Path jsModuleDir = workdir.resolve("javascript");

    runProcess(jsModuleDir, "npm", "install");
    runProcess(jsModuleDir, "npm", "run", "assemble");

    final Path distDir = jsModuleDir.resolve("build").resolve("dist");
    if (!Files.isDirectory(distDir)) {
      throw new IllegalStateException("JS build succeeded but dist directory is not found");
    }

    return distDir;
  }

  private static void runProcess(final Path directory, final String... command) {
    final ProcessBuilder processBuilder = new ProcessBuilder()
        .command(command)
        .directory(directory.toFile())
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE);

    final Process process;
    try {
      process = processBuilder.start();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    final String commandStr = String.join(" ", command);
    final long pid = process.pid();
    log.debug("[pid={}] {} started", pid, commandStr);

    Thread.ofVirtual().start(() -> {
      try (final BufferedReader reader = process.inputReader(StandardCharsets.UTF_8)) {
        reader.lines().forEach(line -> log.info("[pid={}] {}: {}", pid, commandStr, line));
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    });

    Thread.ofVirtual().start(() -> {
      try (final BufferedReader reader = process.errorReader(StandardCharsets.UTF_8)) {
        reader.lines().forEach(line -> log.error("[pid={}] {}: {}", pid, commandStr, line));
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    });

    final boolean exited;
    try {
      exited = process.waitFor(JS_BUILD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }

    if (!exited) {
      log.error("[pid={}] {}:  timed out, destroying", pid, commandStr);
      process.destroyForcibly();
      throw new IllegalStateException();
    }

    final int exitValue = process.exitValue();
    if (exitValue != 0) {
      throw new IllegalStateException("JS build process " + pid + " failed with code " + exitValue);
    }
  }

  private static final Duration JS_BUILD_TIMEOUT = Duration.ofSeconds(120);

  private static Map<Path, JarArgs.Content> prepareJsFilesForJar(final Path jsDistDir) {
    final var baseJarPath = Path.of("org").resolve("springframework").resolve("security");
    try (final Stream<Path> files = Files.list(jsDistDir)) {
      return files
          .filter(Files::isRegularFile)
          .collect(toUnmodifiableMap(
              file -> {
                final Path relative = jsDistDir.relativize(file);
                return baseJarPath.resolve(relative);
              },
              JarArgs.Content.File::new
          ));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void buildExtraSourceSets(final SourceSet testSourceSet,
      final JavaCompilerOptions compilerOptions, final Path workdir,
      final BuildService service) {
    final var processedExtraSourceSets = new HashSet<SourceSet>();
    for (final Dependency dependency : testSourceSet.compileDependencies()) {
      if (dependency instanceof Dependency.OnSourceSet sourceSetDependency) {
        final SourceSet extraSourceSet = sourceSetDependency.sourceSet();
        if (extraSourceSet.id().equals(SourceSet.Id.MAIN)) {
          continue;
        }

        if (processedExtraSourceSets.contains(extraSourceSet)) {
          continue;
        }

        buildExtraSourceSet(extraSourceSet, workdir, service, compilerOptions);
        processedExtraSourceSets.add(extraSourceSet);
      }
    }
  }

  private static void buildExtraSourceSet(final SourceSet extraSourceSet, final Path workdir,
      final BuildService service, final JavaCompilerOptions compilerOptions) {
    final var project = extraSourceSet.project();
    log.debug("[project={}] Compiling extra source set {} first", project.artifactId(),
        extraSourceSet.id());

    final CompilationResult compiled = service.compileJava(
        workdir, extraSourceSet, compilerOptions
    );
    if (!compiled.successful()) {
      log.error("Build failed");
      System.exit(1);
      return;
    }

    service.copyResources(workdir, extraSourceSet);
    service.createJar(
        workdir, extraSourceSet, List.of(compiled.outputDirectory()), Map.of(), null
    );
  }

  private static final String OPEN_SAML5_SOURCE_SET = "opensaml5Main";

  /**
   * Compiles the {@code opensaml5Main} source set of the saml2 module and merges its classes into
   * the main output.
   *
   * <p>Gradle compiles this source set separately (it depends on the main output and shares a single
   * class with it: {@code Saml2Utils}), then adds it to the main jar with
   * {@code DuplicatesStrategy.EXCLUDE}, so the main output wins on collisions.
   */
  private static boolean compileOpenSaml5Main(
      final Path workdir,
      final Project project,
      final BuildService service,
      final JavaCompilerOptions compilerOptions
  ) {
    final Path sourceDir = workdir
        .resolve(project.path())
        .resolve("src")
        .resolve(OPEN_SAML5_SOURCE_SET)
        .resolve("java");
    if (!Files.isDirectory(sourceDir)) {
      return true;
    }

    final Set<Path> sources = collectJavaSources(sourceDir);
    if (sources.isEmpty()) {
      return true;
    }

    log.info("[project={}] Compiling {} source set ({} files)",
        project.artifactId(), OPEN_SAML5_SOURCE_SET, sources.size());

    final Path mainClassesDir = javaClassesDir(workdir, project, SourceSet.Id.MAIN);
    final Path sourceSetClassesDir =
        javaClassesDir(workdir, project, new SourceSet.Id(OPEN_SAML5_SOURCE_SET));
    final Set<Path> classpath = new HashSet<>(
        service.compileClasspath(workdir, project.mainSourceSet())
    );
    classpath.add(mainClassesDir);

    final CompilationResult compiled = service.compileJava(new JavaCompileArgs(
        sources, sourceSetClassesDir, classpath, compilerOptions
    ));
    if (!compiled.successful()) {
      return false;
    }

    mergeClasses(compiled.outputDirectory(), mainClassesDir);
    return true;
  }

  private static Set<Path> collectJavaSources(final Path sourceDir) {
    try (final Stream<Path> files = Files.walk(sourceDir)) {
      return files
          .filter(Files::isRegularFile)
          .filter(file -> file.getFileName().toString().endsWith(".java"))
          .collect(Collectors.toUnmodifiableSet());
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Copies files from {@code sourceDir} to {@code targetDir}, never overwriting existing files.
   */
  private static void mergeClasses(final Path sourceDir, final Path targetDir) {
    for (final Path file : FileUtils.listAll(sourceDir, Comparator.naturalOrder())) {
      if (Files.isDirectory(file)) {
        continue;
      }

      // the main output wins on duplicate classes
      final Path target = targetDir.resolve(sourceDir.relativize(file));
      if (Files.exists(target)) {
        continue;
      }

      try {
        Files.createDirectories(target.getParent());
        Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  private static Path javaClassesDir(
      final Path workdir,
      final Project project,
      final SourceSet.Id sourceSetId
  ) {
    return workdir
        .resolve(project.path())
        .resolve(project.artifactLayout().rootDir())
        .resolve(project.artifactLayout().classesDir())
        .resolve(sourceSetId.toString())
        .resolve(Language.JAVA.directory());
  }

  private static void generateSpringVersionsFile(
      final Path workdir,
      final Project project,
      final String springCoreVersion
  ) {
    final Path filePath = workdir
        .resolve(project.path())
        .resolve(project.artifactLayout().rootDir())
        .resolve(project.artifactLayout().resourcesDir())
        .resolve("main")
        .resolve("META-INF")
        .resolve("spring-security.versions");

    final var properties = new Properties();
    properties.setProperty("org.springframework:spring-core", springCoreVersion);

    try (final var out = Files.newOutputStream(filePath, StandardOpenOption.CREATE)) {
      properties.store(out, null);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static DependencyConstraints getPlatform(final DependencyService service) {
    return service
        .getConstraints(
            GroupArtifactVersion.parse("org.springframework:spring-framework-bom:7.0.9"),
            GroupArtifactVersion.parse("io.projectreactor:reactor-bom:2025.0.7"),
            GroupArtifactVersion.parse("org.springframework.data:spring-data-bom:2025.1.7"),
            GroupArtifactVersion.parse("io.rsocket:rsocket-bom:1.1.5"),
            GroupArtifactVersion.parse("org.junit:junit-bom:6.0.3"),
            GroupArtifactVersion.parse("org.mockito:mockito-bom:5.17.0"),
            GroupArtifactVersion.parse("org.jetbrains.kotlin:kotlin-bom:2.2.21"),
            GroupArtifactVersion.parse("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.10.2"),
            GroupArtifactVersion.parse("com.fasterxml.jackson:jackson-bom:2.20.2"),
            GroupArtifactVersion.parse("tools.jackson:jackson-bom:3.0.4")
        )
        .copy()
        .withExactVersion(
            "ch.qos.logback:logback-classic:1.5.38",
            "com.google.inject:guice:3.0",
            "com.nimbusds:nimbus-jose-jwt:10.4",
            "com.nimbusds:oauth2-oidc-sdk:11.26.1",
            "com.squareup.okhttp3:mockwebserver:3.14.9",
            "com.squareup.okhttp3:okhttp:3.14.9",
            "com.unboundid:unboundid-ldapsdk:7.0.5",
            "commons-collections:commons-collections:3.2.2",
            "io.mockk:mockk:1.14.11",
            "org.apache.kerby:kerb-simplekdc:2.1.2",
            "io.micrometer:context-propagation:1.1.4",
            "io.micrometer:micrometer-observation:1.16.7",
            "jakarta.annotation:jakarta.annotation-api:3.0.0",
            "jakarta.inject:jakarta.inject-api:2.0.1",
            "jakarta.servlet.jsp.jstl:jakarta.servlet.jsp.jstl-api:3.0.2",
            "jakarta.servlet.jsp:jakarta.servlet.jsp-api:4.0.0",
            "jakarta.servlet:jakarta.servlet-api:6.1.0",
            "jakarta.xml.bind:jakarta.xml.bind-api:4.0.5",
            "jakarta.persistence:jakarta.persistence-api:3.2.0",
            "jakarta.websocket:jakarta.websocket-api:2.2.0",
            "jakarta.websocket:jakarta.websocket-client-api:2.2.0",
            "ldapsdk:ldapsdk:4.1",
            "net.sourceforge.htmlunit:htmlunit:2.70.0",
            "org.htmlunit:htmlunit:4.11.1",
            "org.apache.httpcomponents.client5:httpclient5:5.5.2",
            "org.aspectj:aspectjrt:1.9.25.1",
            "org.aspectj:aspectjweaver:1.9.25.1",
            "org.assertj:assertj-core:3.27.7",
            "org.bouncycastle:bcpkix-jdk18on:1.80.2",
            "org.bouncycastle:bcprov-jdk18on:1.80.2",
            "org.eclipse.jetty:jetty-server:11.0.26",
            "org.eclipse.jetty:jetty-servlet:11.0.26",
            "org.hamcrest:hamcrest:2.2",
            "org.hibernate.orm:hibernate-core:7.0.10.Final",
            "org.hsqldb:hsqldb:2.7.4",
            "com.jayway.jsonpath:json-path:2.9.0",
            "org.apereo.cas.client:cas-client-core:4.0.4",
            "org.opensaml:opensaml-saml-api:5.1.6",
            "org.opensaml:opensaml-saml-impl:5.1.6",
            "org.python:jython:2.5.3",
            "org.seleniumhq.selenium:htmlunit3-driver:4.30.0",
            "org.seleniumhq.selenium:selenium-java:4.31.0",
            "org.seleniumhq.selenium:selenium-support:3.141.59",
            "org.skyscreamer:jsonassert:1.5.3",
            "org.slf4j:log4j-over-slf4j:1.7.36",
            "org.slf4j:slf4j-api:2.0.19",
            "org.springframework.ldap:spring-ldap-core:4.0.5",
            "org.synchronoss.cloud:nio-multipart-parser:1.1.0",
            "org.apache.maven.resolver:maven-resolver-connector-basic:1.9.27",
            "org.apache.maven.resolver:maven-resolver-impl:1.9.27",
            "org.apache.maven.resolver:maven-resolver-transport-http:1.9.27",
            "org.apache.maven:maven-resolver-provider:3.9.16",
            "org.instancio:instancio-junit:3.7.1",
            "com.password4j:password4j:1.8.4"
        )
        .build();
  }

  private static Project createProjectCas(
      final ProjectService projectService,
      final DependencyConstraints platform,
      final Project.ArtifactLayout artifactLayout,
      final Project core,
      final Project web
  ) {
    final List<MainSourceSetDependency> mainCommonDependencies = List.of(
        core,
        web,
        GroupArtifact.parse("org.apereo.cas.client:cas-client-core"),
        GroupArtifact.parse("org.springframework:spring-aop"),
        GroupArtifact.parse("org.springframework:spring-context"),
        GroupArtifact.parse("org.springframework:spring-core"),
        GroupArtifact.parse("org.springframework:spring-jdbc"),
        GroupArtifact.parse("org.springframework:spring-tx")
    );
    final List<MainSourceSetDependency> mainOptionalDependencies = List.of(
        GroupArtifact.parse("com.fasterxml.jackson.core:jackson-databind"),
        GroupArtifact.parse("tools.jackson.core:jackson-databind")
    );
    final var mainCompileDependencies = new ArrayList<>(mainCommonDependencies);
    mainCompileDependencies.addAll(mainOptionalDependencies);
    mainCompileDependencies.add(GroupArtifact.parse("jakarta.servlet:jakarta.servlet-api"));

    final var main = new MainSourceSetArgs(
        "main",
        SourceRoots.javaSources(Set.of(Path.of("src", "main", "java"))),
        Set.of(Path.of("src", "main", "resources")),
        mainCompileDependencies,
        mainCommonDependencies,
        platform
    );

    final List<TestSourceSetDependency> testCompileDependencies = Stream
        .of(
            main,

            // Referencing web module test source sets, which we build ourselves.
            // Web tests are not only a JAR - they pull their dependencies
            // transitively, including core tests.
            core.testSourceSet(),
            web.testSourceSet(),

            GroupArtifact.parse("org.assertj:assertj-core"),
            GroupArtifact.parse("org.junit.jupiter:junit-jupiter-api"),
            GroupArtifact.parse("org.junit.jupiter:junit-jupiter-params"),
            GroupArtifact.parse("org.junit.jupiter:junit-jupiter-engine"),
            GroupArtifact.parse("org.mockito:mockito-core"),
            GroupArtifact.parse("org.mockito:mockito-junit-jupiter"),
            GroupArtifact.parse("org.springframework:spring-test"),
            GroupArtifact.parse("org.skyscreamer:jsonassert")
        )
        .collect(Collectors.toList());

    for (final MainSourceSetDependency dependency : mainOptionalDependencies) {
      final TestSourceSetDependency testDependency = switch (dependency) {
        case LocalJar localJar -> localJar;
        case Project project -> project;
        case GroupArtifact groupArtifact -> groupArtifact;
        case GroupArtifactVersion groupArtifactVersion -> groupArtifactVersion;
        case SourceSet sourceSet -> sourceSet;
      };
      testCompileDependencies.add(testDependency);
    }

    final var testRuntimeDependencies = new ArrayList<>(testCompileDependencies);
    testRuntimeDependencies.add(GroupArtifact.parse("org.junit.platform:junit-platform-launcher"));

    final var test = new TestSourceSetArgs(
        "test",
        SourceRoots.javaSources(Set.of(Path.of("src", "test", "java"))),
        Set.of(Path.of("src", "test", "resources")),
        testCompileDependencies,
        testRuntimeDependencies,
        platform
    );
    return projectService.create(
        "org.springframework.security",
        "spring-security-cas",
        "7.0.0",
        projectBuilder -> projectBuilder
            .withPath(Path.of("cas"))
            .withArtifactLayout(artifactLayout)
            .withSourceSets(main, test)
    );
  }

  private static Project createProjectMessaging(
      final ProjectService projectService,
      final DependencyConstraints platform,
      final Project.ArtifactLayout artifactLayout,
      final Project core,
      final Project web
  ) {
    final List<MainSourceSetDependency> api = List.of(
        core,
        // api
        GroupArtifact.parse("org.springframework:spring-beans"),
        GroupArtifact.parse("org.springframework:spring-context"),
        GroupArtifact.parse("org.springframework:spring-core"),
        GroupArtifact.parse("org.springframework:spring-expression"),
        GroupArtifact.parse("org.springframework:spring-messaging")
    );
    final List<MainSourceSetDependency> compileOnly = List.of(
        // optional
        web,
        GroupArtifact.parse("io.projectreactor:reactor-core"),
        GroupArtifact.parse("org.springframework:spring-web"),
        GroupArtifact.parse("org.springframework:spring-websocket")
    );
    final var main = new MainSourceSetArgs(
        SourceSet.Id.MAIN.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "main", "java"))),
        Set.of(Path.of("src", "main", "resources")),
        Stream.concat(api.stream(), compileOnly.stream()).toList(),
        api,
        platform
    );

    final List<TestSourceSetDependency> testCompileAndRun = new ArrayList<>(List.of(
        main,
        GroupArtifact.parse("org.assertj:assertj-core"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-api"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-params"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-engine"),
        GroupArtifact.parse("org.mockito:mockito-core"),
        GroupArtifact.parse("org.mockito:mockito-junit-jupiter"),
        GroupArtifact.parse("org.springframework:spring-test"),
        GroupArtifact.parse("org.slf4j:slf4j-api"),
        GroupArtifact.parse("org.slf4j:log4j-over-slf4j"),
        GroupArtifact.parse("ch.qos.logback:logback-classic"),
        core.testSourceSet()
    ));
    for (final MainSourceSetDependency d : compileOnly) {
      switch (d) {
        case LocalJar localJar -> testCompileAndRun.add(localJar);
        case Project project -> testCompileAndRun.add(project);
        case GroupArtifact groupArtifact -> testCompileAndRun.add(groupArtifact);
        case GroupArtifactVersion groupArtifactVersion ->
            testCompileAndRun.add(groupArtifactVersion);
        case SourceSet sourceSet -> testCompileAndRun.add(sourceSet);
      }
    }

    final var testRuntime = new ArrayList<>(testCompileAndRun);
    testRuntime.add(GroupArtifact.parse("org.hsqldb:hsqldb"));
    testRuntime.add(GroupArtifact.parse("org.junit.platform:junit-platform-launcher"));

    final var test = new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "test", "java"))),
        Set.of(Path.of("src", "test", "resources")),
        testCompileAndRun,
        testRuntime,
        platform
    );
    return projectService.create(
        "org.springframework.security",
        "spring-security-messaging",
        "7.0.0",
        projectBuilder -> projectBuilder
            .withPath(Path.of("messaging"))
            .withArtifactLayout(artifactLayout)
            .withSourceSets(main, test)
    );
  }

  private static Project createProjectWeb(
      final ProjectService projectService,
      final DependencyConstraints platform,
      final Project.ArtifactLayout artifactLayout, final Project core
  ) {
    final List<MainSourceSetDependency> api = List.of(
        core,
        // api
        GroupArtifact.parse("org.springframework:spring-core"),
        GroupArtifact.parse("org.springframework:spring-aop"),
        GroupArtifact.parse("org.springframework:spring-beans"),
        GroupArtifact.parse("org.springframework:spring-context"),
        GroupArtifact.parse("org.springframework:spring-expression"),
        GroupArtifact.parse("org.springframework:spring-web")
    );
    final List<MainSourceSetDependency> compileOnly = List.of(
        // optional
        GroupArtifact.parse("com.fasterxml.jackson.core:jackson-databind"),
        GroupArtifact.parse("io.micrometer:context-propagation"),
        GroupArtifact.parse("io.projectreactor:reactor-core"),
        GroupArtifact.parse("org.springframework:spring-jdbc"),
        GroupArtifact.parse("org.springframework:spring-tx"),
        GroupArtifact.parse("org.springframework:spring-webflux"),
        GroupArtifact.parse("org.springframework:spring-webmvc"),
        GroupArtifact.parse("tools.jackson.core:jackson-databind"),
        GroupArtifactVersion.parse("com.webauthn4j:webauthn4j-core:0.31.6.RELEASE"),

        // provided
        GroupArtifact.parse("jakarta.servlet:jakarta.servlet-api")
    );
    final var main = new MainSourceSetArgs(
        SourceSet.Id.MAIN.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "main", "java"))),
        Set.of(Path.of("src", "main", "resources")),
        Stream.concat(api.stream(), compileOnly.stream()).toList(),
        api,
        platform
    );

    final List<TestSourceSetDependency> testCompileAndRun = new ArrayList<>(List.of(
        main,
        GroupArtifact.parse("io.projectreactor:reactor-test"),
        GroupArtifact.parse("jakarta.xml.bind:jakarta.xml.bind-api"),
        GroupArtifact.parse("jakarta.websocket:jakarta.websocket-api"),
        GroupArtifact.parse("jakarta.websocket:jakarta.websocket-client-api"),
        GroupArtifact.parse("org.hamcrest:hamcrest"),
        GroupArtifact.parse("org.mockito:mockito-core"),
        GroupArtifact.parse("org.skyscreamer:jsonassert"),
        GroupArtifact.parse("org.springframework:spring-webflux"),
        GroupArtifact.parse("org.synchronoss.cloud:nio-multipart-parser"),
        GroupArtifact.parse("org.assertj:assertj-core"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-api"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-params"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-engine"),
        GroupArtifact.parse("org.mockito:mockito-core"),
        GroupArtifact.parse("org.mockito:mockito-junit-jupiter"),
        GroupArtifact.parse("org.springframework:spring-test"),
        GroupArtifact.parse("com.squareup.okhttp3:mockwebserver"),
        // web tests depend on core's tests
        core.testSourceSet()
    ));
    for (final MainSourceSetDependency d : compileOnly) {
      switch (d) {
        case LocalJar localJar -> testCompileAndRun.add(localJar);
        case Project project -> testCompileAndRun.add(project);
        case GroupArtifact groupArtifact -> testCompileAndRun.add(groupArtifact);
        case GroupArtifactVersion groupArtifactVersion ->
            testCompileAndRun.add(groupArtifactVersion);
        case SourceSet sourceSet -> testCompileAndRun.add(sourceSet);
      }
    }

    final var testRuntime = new ArrayList<>(testCompileAndRun);
    testRuntime.add(GroupArtifact.parse("org.hsqldb:hsqldb"));
    testRuntime.add(GroupArtifact.parse("org.junit.platform:junit-platform-launcher"));

    final var test = new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "test", "java"))),
        Set.of(Path.of("src", "test", "resources")),
        testCompileAndRun,
        testRuntime,
        platform
    );
    return projectService.create(
        "org.springframework.security",
        "spring-security-web",
        "7.0.0",
        projectBuilder -> projectBuilder
            .withPath(Path.of("web"))
            .withArtifactLayout(artifactLayout)
            .withSourceSets(main, test)
    );
  }

  private static Project createProjectData(
      final ProjectService projectService,
      final DependencyConstraints platform,
      final Project.ArtifactLayout artifactLayout, final Project core
  ) {
    final var mainDependencies = List.<MainSourceSetDependency>of(
        core,
        GroupArtifact.parse("jakarta.xml.bind:jakarta.xml.bind-api"),
        GroupArtifact.parse("org.springframework.data:spring-data-commons"),
        GroupArtifact.parse("org.springframework:spring-core")
    );
    final var main = new MainSourceSetArgs(
        SourceSet.Id.MAIN.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "main", "java"))),
        Set.of(Path.of("src", "main", "resources")),
        mainDependencies,
        mainDependencies,
        platform
    );

    final var testCompileClasspath = List.<TestSourceSetDependency>of(
        main,
        GroupArtifact.parse("org.assertj:assertj-core"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-api"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-params"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-engine"),
        GroupArtifact.parse("org.mockito:mockito-core"),
        GroupArtifact.parse("org.mockito:mockito-junit-jupiter"),
        GroupArtifact.parse("org.springframework:spring-test")
    );
    final var testRuntimeClasspath = new ArrayList<>(testCompileClasspath);
    testRuntimeClasspath.add(GroupArtifact.parse("org.junit.platform:junit-platform-launcher"));

    // TODO: find out why JUnit cannot find tests
    final var test = new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "test", "java"))),
        Set.of(Path.of("src", "test", "resources")),
        testCompileClasspath,
        testRuntimeClasspath,
        platform
    );
    return projectService.create(
        "org.springframework.security",
        "spring-security-data",
        "7.0.0",
        projectBuilder -> projectBuilder
            .withPath(Path.of("data"))
            .withArtifactLayout(artifactLayout)
            .withSourceSets(main, test)
    );
  }

  private static Project createProjectAcl(
      final ProjectService projectService,
      final DependencyConstraints platform,
      final Project.ArtifactLayout artifactLayout,
      final Project core
  ) {
    final List<MainSourceSetDependency> mainDependencies = List.of(
        core,
        GroupArtifact.parse("org.springframework:spring-aop"),
        GroupArtifact.parse("org.springframework:spring-context"),
        GroupArtifact.parse("org.springframework:spring-core"),
        GroupArtifact.parse("org.springframework:spring-jdbc"),
        GroupArtifact.parse("org.springframework:spring-tx")
    );
    final var main = new MainSourceSetArgs(
        "main",
        SourceRoots.javaSources(Set.of(Path.of("src", "main", "java"))),
        Set.of(Path.of("src", "main", "resources")),
        mainDependencies,
        mainDependencies,
        platform
    );

    final List<TestSourceSetDependency> testCompileDependencies = List.of(
        main,
        GroupArtifact.parse("org.assertj:assertj-core"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-api"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-params"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-engine"),
        GroupArtifact.parse("org.mockito:mockito-core"),
        GroupArtifact.parse("org.mockito:mockito-junit-jupiter"),
        GroupArtifact.parse("org.springframework:spring-beans"),
        GroupArtifact.parse("org.springframework:spring-context-support"),
        GroupArtifact.parse("org.springframework:spring-test")
    );

    final var testRuntimeDependencies = new ArrayList<>(testCompileDependencies);
    testRuntimeDependencies.add(GroupArtifact.parse("org.hsqldb:hsqldb"));
    testRuntimeDependencies.add(GroupArtifact.parse("org.junit.platform:junit-platform-launcher"));

    final var test = new TestSourceSetArgs(
        "test",
        SourceRoots.javaSources(Set.of(Path.of("src", "test", "java"))),
        Set.of(Path.of("src", "test", "resources")),
        testCompileDependencies,
        testRuntimeDependencies,
        platform
    );
    return projectService.create(
        "org.springframework.security",
        "spring-security-acl",
        "7.0.0",
        projectBuilder -> projectBuilder
            .withPath(Path.of("acl"))
            .withArtifactLayout(artifactLayout)
            .withSourceSets(main, test)
    );
  }

  private static Project createProjectAccess(
      final ProjectService projectService,
      final DependencyConstraints platform,
      final Project.ArtifactLayout artifactLayout,
      final Project core,
      final Project web,
      final Project acl,
      final Project messaging
  ) {
    final List<MainSourceSetDependency> api = List.of(
        core,
        // api
        GroupArtifact.parse("io.micrometer:micrometer-observation"),
        GroupArtifact.parse("jakarta.annotation:jakarta.annotation-api"),
        GroupArtifact.parse("org.springframework:spring-aop"),
        GroupArtifact.parse("org.springframework:spring-beans"),
        GroupArtifact.parse("org.springframework:spring-context"),
        GroupArtifact.parse("org.springframework:spring-core"),
        GroupArtifact.parse("org.springframework:spring-expression"),
        GroupArtifact.parse("org.springframework:spring-messaging")
    );
    final List<MainSourceSetDependency> compileOnly = List.of(
        // optional projects
        web,
        acl,
        messaging,

        // optional
        GroupArtifact.parse("com.fasterxml.jackson.core:jackson-databind"),
        GroupArtifact.parse("io.micrometer:context-propagation"),
        GroupArtifact.parse("io.projectreactor:reactor-core"),
        GroupArtifact.parse("org.aspectj:aspectjrt"),
        GroupArtifact.parse("org.jetbrains.kotlinx:kotlinx-coroutines-reactor"),
        GroupArtifact.parse("org.springframework:spring-jdbc"),
        GroupArtifact.parse("org.springframework:spring-tx"),
        GroupArtifact.parse("org.springframework:spring-webmvc"),
        GroupArtifact.parse("tools.jackson.core:jackson-databind"),

        // provided
        GroupArtifact.parse("jakarta.servlet:jakarta.servlet-api")
    );
    final var main = new MainSourceSetArgs(
        SourceSet.Id.MAIN.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "main", "java"))),
        Set.of(Path.of("src", "main", "resources")),
        Stream.concat(api.stream(), compileOnly.stream()).toList(),
        api,
        platform
    );

    final List<TestSourceSetDependency> testCompileClasspath = Stream
        .of(
            main,
            web.testSourceSet(),
            GroupArtifact.parse("commons-collections:commons-collections"),
            GroupArtifact.parse("io.projectreactor:reactor-test"),
            GroupArtifact.parse("org.assertj:assertj-core"),
            GroupArtifact.parse("org.junit.jupiter:junit-jupiter-api"),
            GroupArtifact.parse("org.junit.jupiter:junit-jupiter-params"),
            GroupArtifact.parse("org.junit.jupiter:junit-jupiter-engine"),
            GroupArtifact.parse("org.mockito:mockito-core"),
            GroupArtifact.parse("org.mockito:mockito-junit-jupiter"),
            GroupArtifact.parse("org.springframework:spring-core-test"),
            GroupArtifact.parse("org.springframework:spring-test"),
            GroupArtifact.parse("org.skyscreamer:jsonassert")
        )
        .collect(Collectors.toList());
    for (final MainSourceSetDependency d : compileOnly) {
      switch (d) {
        case LocalJar localJar -> testCompileClasspath.add(localJar);
        case Project project -> testCompileClasspath.add(project);
        case GroupArtifact groupArtifact -> testCompileClasspath.add(groupArtifact);
        case GroupArtifactVersion groupArtifactVersion ->
            testCompileClasspath.add(groupArtifactVersion);
        case SourceSet sourceSet -> testCompileClasspath.add(sourceSet);
      }
    }

    final var testRuntimeClasspath = new ArrayList<>(testCompileClasspath);
    testRuntimeClasspath.add(GroupArtifact.parse("org.hsqldb:hsqldb"));
    testRuntimeClasspath.add(GroupArtifact.parse("org.junit.platform:junit-platform-launcher"));

    final var test = new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "test", "java"))),
        Set.of(Path.of("src", "test", "resources")),
        testCompileClasspath,
        testRuntimeClasspath,
        platform
    );
    return projectService.create(
        "org.springframework.security",
        "spring-security-access",
        "7.0.0",
        projectBuilder -> projectBuilder
            .withPath(Path.of("access"))
            .withArtifactLayout(artifactLayout)
            .withSourceSets(main, test)
    );
  }

  private static Project createProjectAspects(
      final ProjectService projectService,
      final DependencyConstraints platform,
      final Project.ArtifactLayout artifactLayout,
      final Project access
  ) {
    final List<MainSourceSetDependency> api = List.of(
        access,
        GroupArtifact.parse("org.aspectj:aspectjrt"),
        GroupArtifact.parse("org.springframework:spring-aop"),
        GroupArtifact.parse("org.springframework:spring-beans"),
        GroupArtifact.parse("org.springframework:spring-context"),
        GroupArtifact.parse("org.springframework:spring-core")
    );
    final var main = new MainSourceSetArgs(
        SourceSet.Id.MAIN.toString(),
        SourceRoots.EMPTY,
        SourceRoots.EMPTY,
        SourceRoots.aspectjSources(Set.of(Path.of("src", "main", "java"))),
        Set.of(Path.of("src", "main", "resources")),
        api,
        api,
        platform
    );

    final var testCompileClasspath = List.<TestSourceSetDependency>of(
        main,
        GroupArtifact.parse("org.assertj:assertj-core"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-api"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-params"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-engine"),
        GroupArtifact.parse("org.mockito:mockito-core"),
        GroupArtifact.parse("org.mockito:mockito-junit-jupiter"),
        GroupArtifact.parse("org.springframework:spring-aop"),
        GroupArtifact.parse("org.springframework:spring-test")
    );
    final var testRuntimeClasspath = new ArrayList<>(testCompileClasspath);
    testRuntimeClasspath.add(GroupArtifact.parse("org.junit.platform:junit-platform-launcher"));

    final var test = new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        SourceRoots.EMPTY,
        SourceRoots.EMPTY,
        SourceRoots.aspectjSources(Set.of(Path.of("src", "test", "java"))),
        Set.of(Path.of("src", "test", "resources")),
        testCompileClasspath,
        testRuntimeClasspath,
        platform
    );
    return projectService.create(
        "org.springframework.security",
        "spring-security-aspects",
        "7.0.0",
        projectBuilder -> projectBuilder
            .withPath(Path.of("aspects"))
            .withArtifactLayout(artifactLayout)
            .withSourceSets(main, test)
    );
  }

  private static Project createProjectConfig(
      final ProjectService projectService,
      final DependencyConstraints platform,
      final Project.ArtifactLayout artifactLayout,
      final Project core,
      final Project data,
      final Project web,
      final Project messaging,
      final Project access,
      final Project ldap,
      final Project saml2,
      final Project oauth2Client,
      final Project oauth2Jose,
      final Project oauth2ResourceServer,
      final Project oauth2AuthorizationServer,
      final Project rsocket,
      final Project webauthn
  ) {
    final List<MainSourceSetDependency> api = List.of(
        core,
        GroupArtifact.parse("org.springframework:spring-aop"),
        GroupArtifact.parse("org.springframework:spring-beans"),
        GroupArtifact.parse("org.springframework:spring-context"),
        GroupArtifact.parse("org.springframework:spring-core"),
        GroupArtifact.parse("org.jetbrains.kotlin:kotlin-stdlib")
    );
    // Optional dependencies are compile-only for consumers but are required to compile this
    // module.
    final List<MainSourceSetDependency> optional = List.of(
        access,
        data,
        messaging,
        web,
        ldap,
        saml2,
        oauth2Client,
        oauth2Jose,
        oauth2ResourceServer,
        oauth2AuthorizationServer,
        rsocket,
        webauthn,
        GroupArtifact.parse("io.projectreactor:reactor-core"),
        GroupArtifact.parse("org.aspectj:aspectjweaver"),
        GroupArtifact.parse("org.springframework:spring-jdbc"),
        GroupArtifact.parse("org.springframework:spring-messaging"),
        GroupArtifact.parse("org.springframework:spring-tx"),
        GroupArtifact.parse("org.springframework:spring-web"),
        GroupArtifact.parse("org.springframework:spring-webflux"),
        GroupArtifact.parse("org.springframework:spring-webmvc"),
        GroupArtifact.parse("org.springframework:spring-websocket"),
        GroupArtifact.parse("org.jetbrains.kotlin:kotlin-reflect"),
        GroupArtifact.parse("jakarta.annotation:jakarta.annotation-api")
    );
    final List<MainSourceSetDependency> provided = List.of(
        GroupArtifact.parse("jakarta.servlet:jakarta.servlet-api")
    );
    final var mainCompileDependencies = new ArrayList<>(api);
    mainCompileDependencies.addAll(optional);
    mainCompileDependencies.addAll(provided);

    final var main = new MainSourceSetArgs(
        SourceSet.Id.MAIN.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "main", "java"))),
        SourceRoots.kotlinSources(Set.of(Path.of("src", "main", "kotlin"))),
        SourceRoots.EMPTY,
        Set.of(Path.of("src", "main", "resources")),
        mainCompileDependencies,
        api,
        platform
    );

    // TODO: config tests depend on the test source sets of access/core/web/test/ldap/oauth2-*/
    // saml2/webauthn, which are not built yet. Enable once those are available.
    final var test = new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "test", "java"))),
        SourceRoots.kotlinSources(Set.of(Path.of("src", "test", "kotlin"))),
        SourceRoots.EMPTY,
        Set.of(Path.of("src", "test", "resources")),
        List.of(main),
        List.of(main),
        platform
    );
    return projectService.create(
        "org.springframework.security",
        "spring-security-config",
        "7.0.0",
        projectBuilder -> projectBuilder
            .withPath(Path.of("config"))
            .withArtifactLayout(artifactLayout)
            .withSourceSets(main, test)
    );
  }

  private static Project createProjectTest(
      final ProjectService projectService,
      final DependencyConstraints platform,
      final Project.ArtifactLayout artifactLayout,
      final Project core,
      final Project web,
      final Project oauth2Jose,
      final Project oauth2Client,
      final Project oauth2ResourceServer,
      final Project config
  ) {
    final List<MainSourceSetDependency> api = List.of(
        core,
        web,
        GroupArtifact.parse("org.springframework:spring-core"),
        GroupArtifact.parse("org.springframework:spring-test")
    );
    final List<MainSourceSetDependency> optional = List.of(
        oauth2Client,
        oauth2Jose,
        oauth2ResourceServer,
        config,
        GroupArtifact.parse("io.projectreactor:reactor-core"),
        GroupArtifact.parse("org.springframework:spring-webmvc"),
        GroupArtifact.parse("org.springframework:spring-webflux")
    );
    final List<MainSourceSetDependency> provided = List.of(
        GroupArtifact.parse("jakarta.servlet:jakarta.servlet-api")
    );
    final var mainCompileDependencies = new ArrayList<>(api);
    mainCompileDependencies.addAll(optional);
    mainCompileDependencies.addAll(provided);

    final var main = new MainSourceSetArgs(
        SourceSet.Id.MAIN.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "main", "java"))),
        Set.of(Path.of("src", "main", "resources")),
        mainCompileDependencies,
        api,
        platform
    );

    // TODO: test tests depend on the config test source set and on test classes from
    // oauth2-core/oauth2-client/oauth2-jose, which are not built yet.
    final var test = new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "test", "java"))),
        Set.of(Path.of("src", "test", "resources")),
        List.of(main),
        List.of(main),
        platform
    );
    return projectService.create(
        "org.springframework.security",
        "spring-security-test",
        "7.0.0",
        projectBuilder -> projectBuilder
            .withPath(Path.of("test"))
            .withArtifactLayout(artifactLayout)
            .withSourceSets(main, test)
    );
  }

  private static Project createProjectLdap(
      final ProjectService projectService,
      final DependencyConstraints platform,
      final Project.ArtifactLayout artifactLayout,
      final Project core
  ) {
    final List<MainSourceSetDependency> api = List.of(
        core,
        GroupArtifact.parse("org.springframework:spring-beans"),
        GroupArtifact.parse("org.springframework:spring-context"),
        GroupArtifact.parse("org.springframework:spring-core"),
        GroupArtifact.parse("org.springframework:spring-tx"),
        GroupArtifact.parse("org.springframework.ldap:spring-ldap-core")
    );
    final List<MainSourceSetDependency> optional = List.of(
        GroupArtifact.parse("com.fasterxml.jackson.core:jackson-databind"),
        GroupArtifact.parse("ldapsdk:ldapsdk"),
        GroupArtifact.parse("com.unboundid:unboundid-ldapsdk"),
        GroupArtifact.parse("tools.jackson.core:jackson-databind")
    );
    final var mainCompileDependencies = new ArrayList<>(api);
    mainCompileDependencies.addAll(optional);

    final var main = new MainSourceSetArgs(
        SourceSet.Id.MAIN.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "main", "java"))),
        Set.of(Path.of("src", "main", "resources")),
        mainCompileDependencies,
        api,
        platform
    );

    final var test = new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "test", "java"))),
        Set.of(Path.of("src", "test", "resources")),
        List.of(main),
        List.of(main),
        platform
    );
    return projectService.create(
        "org.springframework.security",
        "spring-security-ldap",
        "7.0.0",
        projectBuilder -> projectBuilder
            .withPath(Path.of("ldap"))
            .withArtifactLayout(artifactLayout)
            .withSourceSets(main, test)
    );
  }

  private static Project createProjectSaml2(
      final ProjectService projectService,
      final DependencyConstraints platform,
      final Project.ArtifactLayout artifactLayout,
      final Project web
  ) {
    final List<MainSourceSetDependency> api = List.of(
        web,
        GroupArtifact.parse("org.opensaml:opensaml-saml-api"),
        GroupArtifact.parse("org.opensaml:opensaml-saml-impl")
    );
    final List<MainSourceSetDependency> optional = List.of(
        GroupArtifact.parse("com.fasterxml.jackson.core:jackson-databind"),
        GroupArtifact.parse("org.springframework:spring-jdbc"),
        GroupArtifact.parse("tools.jackson.core:jackson-databind")
    );
    final List<MainSourceSetDependency> provided = List.of(
        GroupArtifact.parse("jakarta.servlet:jakarta.servlet-api")
    );
    final var mainCompileDependencies = new ArrayList<>(api);
    mainCompileDependencies.addAll(optional);
    mainCompileDependencies.addAll(provided);

    // src/opensaml5Main/java is compiled separately (see compileOpenSaml5Main): it depends on the
    // main output and shares one class (Saml2Utils) with it.
    final var main = new MainSourceSetArgs(
        SourceSet.Id.MAIN.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "main", "java"))),
        Set.of(Path.of("src", "main", "resources")),
        mainCompileDependencies,
        api,
        platform
    );

    final var test = new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "test", "java"))),
        Set.of(Path.of("src", "test", "resources")),
        List.of(main),
        List.of(main),
        platform
    );
    return projectService.create(
        "org.springframework.security",
        "spring-security-saml2-service-provider",
        "7.0.0",
        projectBuilder -> projectBuilder
            .withPath(Path.of("saml2", "saml2-service-provider"))
            .withArtifactLayout(artifactLayout)
            .withSourceSets(main, test)
    );
  }

  private static Project createProjectOauth2Jose(
      final ProjectService projectService,
      final DependencyConstraints platform,
      final Project.ArtifactLayout artifactLayout,
      final Project core,
      final Project oauth2Core
  ) {
    final List<MainSourceSetDependency> api = List.of(
        core,
        oauth2Core,
        GroupArtifact.parse("org.springframework:spring-core"),
        GroupArtifact.parse("com.nimbusds:nimbus-jose-jwt")
    );
    final List<MainSourceSetDependency> optional = List.of(
        GroupArtifact.parse("io.projectreactor:reactor-core"),
        GroupArtifact.parse("org.springframework:spring-webflux")
    );
    final var mainCompileDependencies = new ArrayList<>(api);
    mainCompileDependencies.addAll(optional);

    final var main = new MainSourceSetArgs(
        SourceSet.Id.MAIN.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "main", "java"))),
        Set.of(Path.of("src", "main", "resources")),
        mainCompileDependencies,
        api,
        platform
    );

    final var test = new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "test", "java"))),
        Set.of(Path.of("src", "test", "resources")),
        List.of(main),
        List.of(main),
        platform
    );
    return projectService.create(
        "org.springframework.security",
        "spring-security-oauth2-jose",
        "7.0.0",
        projectBuilder -> projectBuilder
            .withPath(Path.of("oauth2", "oauth2-jose"))
            .withArtifactLayout(artifactLayout)
            .withSourceSets(main, test)
    );
  }

  private static Project createProjectOauth2Client(
      final ProjectService projectService,
      final DependencyConstraints platform,
      final Project.ArtifactLayout artifactLayout,
      final Project core,
      final Project web,
      final Project oauth2Core,
      final Project oauth2Jose
  ) {
    final List<MainSourceSetDependency> api = List.of(
        core,
        oauth2Core,
        web,
        GroupArtifact.parse("org.springframework:spring-core"),
        GroupArtifact.parse("com.nimbusds:oauth2-oidc-sdk")
    );
    final List<MainSourceSetDependency> optional = List.of(
        oauth2Jose,
        GroupArtifact.parse("io.projectreactor:reactor-core"),
        GroupArtifact.parse("org.springframework:spring-webflux"),
        GroupArtifact.parse("com.fasterxml.jackson.core:jackson-databind"),
        GroupArtifact.parse("com.fasterxml.jackson.datatype:jackson-datatype-jsr310"),
        GroupArtifact.parse("org.springframework:spring-jdbc"),
        GroupArtifact.parse("org.springframework:spring-r2dbc"),
        GroupArtifact.parse("tools.jackson.core:jackson-databind")
    );
    final List<MainSourceSetDependency> provided = List.of(
        GroupArtifact.parse("jakarta.servlet:jakarta.servlet-api")
    );
    final var mainCompileDependencies = new ArrayList<>(api);
    mainCompileDependencies.addAll(optional);
    mainCompileDependencies.addAll(provided);

    final var main = new MainSourceSetArgs(
        SourceSet.Id.MAIN.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "main", "java"))),
        Set.of(Path.of("src", "main", "resources")),
        mainCompileDependencies,
        api,
        platform
    );

    final var test = new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "test", "java"))),
        Set.of(Path.of("src", "test", "resources")),
        List.of(main),
        List.of(main),
        platform
    );
    return projectService.create(
        "org.springframework.security",
        "spring-security-oauth2-client",
        "7.0.0",
        projectBuilder -> projectBuilder
            .withPath(Path.of("oauth2", "oauth2-client"))
            .withArtifactLayout(artifactLayout)
            .withSourceSets(main, test)
    );
  }

  private static Project createProjectOauth2ResourceServer(
      final ProjectService projectService,
      final DependencyConstraints platform,
      final Project.ArtifactLayout artifactLayout,
      final Project core,
      final Project web,
      final Project oauth2Core,
      final Project oauth2Jose
  ) {
    final List<MainSourceSetDependency> api = List.of(
        core,
        oauth2Core,
        web,
        GroupArtifact.parse("org.springframework:spring-core")
    );
    final List<MainSourceSetDependency> optional = List.of(
        oauth2Jose,
        GroupArtifact.parse("com.nimbusds:oauth2-oidc-sdk"),
        GroupArtifact.parse("io.projectreactor:reactor-core"),
        GroupArtifact.parse("org.springframework:spring-webflux")
    );
    final List<MainSourceSetDependency> provided = List.of(
        GroupArtifact.parse("jakarta.servlet:jakarta.servlet-api")
    );
    final var mainCompileDependencies = new ArrayList<>(api);
    mainCompileDependencies.addAll(optional);
    mainCompileDependencies.addAll(provided);

    final var main = new MainSourceSetArgs(
        SourceSet.Id.MAIN.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "main", "java"))),
        Set.of(Path.of("src", "main", "resources")),
        mainCompileDependencies,
        api,
        platform
    );

    final var test = new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "test", "java"))),
        Set.of(Path.of("src", "test", "resources")),
        List.of(main),
        List.of(main),
        platform
    );
    return projectService.create(
        "org.springframework.security",
        "spring-security-oauth2-resource-server",
        "7.0.0",
        projectBuilder -> projectBuilder
            .withPath(Path.of("oauth2", "oauth2-resource-server"))
            .withArtifactLayout(artifactLayout)
            .withSourceSets(main, test)
    );
  }

  private static Project createProjectOauth2AuthorizationServer(
      final ProjectService projectService,
      final DependencyConstraints platform,
      final Project.ArtifactLayout artifactLayout,
      final Project web,
      final Project oauth2Core,
      final Project oauth2Jose,
      final Project oauth2ResourceServer
  ) {
    final List<MainSourceSetDependency> api = List.of(
        web,
        oauth2Core,
        oauth2Jose,
        oauth2ResourceServer,
        GroupArtifact.parse("org.springframework:spring-core"),
        GroupArtifact.parse("com.nimbusds:nimbus-jose-jwt"),
        GroupArtifact.parse("tools.jackson.core:jackson-databind")
    );
    final List<MainSourceSetDependency> optional = List.of(
        GroupArtifact.parse("com.fasterxml.jackson.core:jackson-databind"),
        GroupArtifact.parse("com.fasterxml.jackson.datatype:jackson-datatype-jsr310"),
        GroupArtifact.parse("org.springframework:spring-jdbc")
    );
    final List<MainSourceSetDependency> provided = List.of(
        GroupArtifact.parse("jakarta.servlet:jakarta.servlet-api")
    );
    final var mainCompileDependencies = new ArrayList<>(api);
    mainCompileDependencies.addAll(optional);
    mainCompileDependencies.addAll(provided);

    final var main = new MainSourceSetArgs(
        SourceSet.Id.MAIN.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "main", "java"))),
        Set.of(Path.of("src", "main", "resources")),
        mainCompileDependencies,
        api,
        platform
    );

    final var test = new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "test", "java"))),
        Set.of(Path.of("src", "test", "resources")),
        List.of(main),
        List.of(main),
        platform
    );
    return projectService.create(
        "org.springframework.security",
        "spring-security-oauth2-authorization-server",
        "7.0.0",
        projectBuilder -> projectBuilder
            .withPath(Path.of("oauth2", "oauth2-authorization-server"))
            .withArtifactLayout(artifactLayout)
            .withSourceSets(main, test)
    );
  }

  private static Project createProjectRsocket(
      final ProjectService projectService,
      final DependencyConstraints platform,
      final Project.ArtifactLayout artifactLayout,
      final Project core,
      final Project oauth2ResourceServer
  ) {
    final List<MainSourceSetDependency> api = List.of(
        core,
        GroupArtifact.parse("io.rsocket:rsocket-core")
    );
    final List<MainSourceSetDependency> optional = List.of(
        oauth2ResourceServer,
        GroupArtifact.parse("org.springframework:spring-messaging")
    );
    final var mainCompileDependencies = new ArrayList<>(api);
    mainCompileDependencies.addAll(optional);

    final var main = new MainSourceSetArgs(
        SourceSet.Id.MAIN.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "main", "java"))),
        Set.of(Path.of("src", "main", "resources")),
        mainCompileDependencies,
        api,
        platform
    );

    final var test = new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "test", "java"))),
        Set.of(Path.of("src", "test", "resources")),
        List.of(main),
        List.of(main),
        platform
    );
    return projectService.create(
        "org.springframework.security",
        "spring-security-rsocket",
        "7.0.0",
        projectBuilder -> projectBuilder
            .withPath(Path.of("rsocket"))
            .withArtifactLayout(artifactLayout)
            .withSourceSets(main, test)
    );
  }

  private static Project createProjectWebauthn(
      final ProjectService projectService,
      final DependencyConstraints platform,
      final Project.ArtifactLayout artifactLayout,
      final Project core,
      final Project web
  ) {
    final List<MainSourceSetDependency> api = List.of(
        core,
        web,
        GroupArtifact.parse("org.springframework:spring-core"),
        GroupArtifact.parse("org.springframework:spring-beans"),
        GroupArtifact.parse("org.springframework:spring-web"),
        GroupArtifactVersion.parse("com.webauthn4j:webauthn4j-core:0.31.6.RELEASE")
    );
    final List<MainSourceSetDependency> optional = List.of(
        GroupArtifact.parse("org.springframework:spring-jdbc"),
        GroupArtifact.parse("org.springframework:spring-tx"),
        GroupArtifact.parse("tools.jackson.core:jackson-databind"),
        GroupArtifact.parse("com.fasterxml.jackson.core:jackson-databind")
    );
    final List<MainSourceSetDependency> provided = List.of(
        GroupArtifact.parse("jakarta.servlet:jakarta.servlet-api")
    );
    final var mainCompileDependencies = new ArrayList<>(api);
    mainCompileDependencies.addAll(optional);
    mainCompileDependencies.addAll(provided);

    final var main = new MainSourceSetArgs(
        SourceSet.Id.MAIN.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "main", "java"))),
        Set.of(Path.of("src", "main", "resources")),
        mainCompileDependencies,
        api,
        platform
    );

    final var test = new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "test", "java"))),
        Set.of(Path.of("src", "test", "resources")),
        List.of(main),
        List.of(main),
        platform
    );
    return projectService.create(
        "org.springframework.security",
        "spring-security-webauthn",
        "7.0.0",
        projectBuilder -> projectBuilder
            .withPath(Path.of("webauthn"))
            .withArtifactLayout(artifactLayout)
            .withSourceSets(main, test)
    );
  }

  private static Project createProjectCrypto(
      final ProjectService projectService,
      final DependencyConstraints platform,
      final Project.ArtifactLayout artifactLayout) {
    final var mainDependencies = List.<MainSourceSetDependency>of(
        GroupArtifact.parse("org.springframework:spring-core"),
        GroupArtifact.parse("org.bouncycastle:bcpkix-jdk18on"),
        GroupArtifact.parse("com.password4j:password4j")
    );
    final var main = new MainSourceSetArgs(
        "main",
        SourceRoots.javaSources(Set.of(Path.of("src", "main", "java"))),
        Set.of(Path.of("src", "main", "resources")),
        mainDependencies,
        mainDependencies,
        platform
    );

    final var testFixtures = new ExtraSourceSetArgs(
        "testFixtures",
        SourceRoots.javaSources(Set.of(Path.of("src", "testFixtures", "java"))),
        Set.of(Path.of("src", "testFixtures", "resources")),
        List.of(GroupArtifact.parse("org.assertj:assertj-core")),
        List.of(GroupArtifact.parse("org.assertj:assertj-core")),
        platform
    );
    final var testCompileClasspath = List.<TestSourceSetDependency>of(
        main,
        testFixtures,
        GroupArtifact.parse("org.assertj:assertj-core"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-api"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-params"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-engine"),
        GroupArtifact.parse("org.mockito:mockito-core"),
        GroupArtifact.parse("org.mockito:mockito-junit-jupiter"),
        GroupArtifact.parse("org.springframework:spring-test")
    );

    final var testRuntimeClasspath = new ArrayList<>(testCompileClasspath);
    testRuntimeClasspath.add(GroupArtifact.parse("org.junit.platform:junit-platform-launcher"));

    final var test = new TestSourceSetArgs(
        "test",
        SourceRoots.javaSources(Set.of(Path.of("src", "test", "java"))),
        Set.of(Path.of("src", "test", "resources")),
        testCompileClasspath,
        testRuntimeClasspath,
        platform
    );
    return projectService.create(
        "org.springframework.security",
        "spring-security-crypto",
        "7.0.0",
        projectBuilder -> projectBuilder
            .withPath(Path.of("crypto"))
            .withArtifactLayout(artifactLayout)
            .withSourceSets(main, test)
    );
  }

  /**
   * Adds a main source set dependency to a test classpath.
   *
   * <p>Optional and provided dependencies are not transitive, so they have to be repeated in the
   * test source set to be available at test compile and runtime.
   */
  private static void addTestDependency(
      final List<TestSourceSetDependency> testDependencies,
      final MainSourceSetDependency dependency
  ) {
    final TestSourceSetDependency testDependency = switch (dependency) {
      case LocalJar localJar -> localJar;
      case Project project -> project;
      case GroupArtifact groupArtifact -> groupArtifact;
      case GroupArtifactVersion groupArtifactVersion -> groupArtifactVersion;
      case SourceSet sourceSet -> sourceSet;
    };
    testDependencies.add(testDependency);
  }

  private static Project createProjectOauth2Core(
      final ProjectService projectService,
      final DependencyConstraints platform,
      final Project.ArtifactLayout artifactLayout,
      final Project core
  ) {
    final List<MainSourceSetDependency> api = List.of(
        core,
        GroupArtifact.parse("org.springframework:spring-core"),
        GroupArtifact.parse("org.springframework:spring-web")
    );
    final List<MainSourceSetDependency> optional = List.of(
        GroupArtifact.parse("com.fasterxml.jackson.core:jackson-databind"),
        GroupArtifact.parse("com.nimbusds:oauth2-oidc-sdk"),
        GroupArtifact.parse("org.springframework:spring-webflux")
    );
    final var main = new MainSourceSetArgs(
        SourceSet.Id.MAIN.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "main", "java"))),
        Set.of(Path.of("src", "main", "resources")),
        Stream.concat(api.stream(), optional.stream()).toList(),
        api,
        platform
    );

    final List<TestSourceSetDependency> testCompileAndRun = new ArrayList<>(List.of(
        main,
        GroupArtifact.parse("org.assertj:assertj-core"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-api"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-params"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-engine"),
        GroupArtifact.parse("org.mockito:mockito-core"),
        GroupArtifact.parse("org.mockito:mockito-junit-jupiter"),
        GroupArtifact.parse("org.springframework:spring-test")
    ));
    for (final MainSourceSetDependency d : optional) {
      addTestDependency(testCompileAndRun, d);
    }

    final var testRuntime = new ArrayList<>(testCompileAndRun);
    testRuntime.add(GroupArtifact.parse("org.junit.platform:junit-platform-launcher"));

    final var test = new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "test", "java"))),
        Set.of(Path.of("src", "test", "resources")),
        testCompileAndRun,
        testRuntime,
        platform
    );
    return projectService.create(
        "org.springframework.security",
        "spring-security-oauth2-core",
        "7.0.0",
        projectBuilder -> projectBuilder
            .withPath(Path.of("oauth2", "oauth2-core"))
            .withArtifactLayout(artifactLayout)
            .withSourceSets(main, test)
    );
  }

  private static Project createProjectTaglibs(
      final ProjectService projectService,
      final DependencyConstraints platform,
      final Project.ArtifactLayout artifactLayout,
      final Project core,
      final Project web,
      final Project acl,
      final Project access
  ) {
    final List<MainSourceSetDependency> api = List.of(
        acl,
        core,
        web,
        GroupArtifact.parse("org.springframework:spring-aop"),
        GroupArtifact.parse("org.springframework:spring-beans"),
        GroupArtifact.parse("org.springframework:spring-context"),
        GroupArtifact.parse("org.springframework:spring-core"),
        GroupArtifact.parse("org.springframework:spring-expression"),
        GroupArtifact.parse("org.springframework:spring-web")
    );
    final List<MainSourceSetDependency> provided = List.of(
        GroupArtifact.parse("jakarta.servlet.jsp:jakarta.servlet.jsp-api"),
        GroupArtifact.parse("jakarta.servlet:jakarta.servlet-api")
    );
    final var main = new MainSourceSetArgs(
        SourceSet.Id.MAIN.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "main", "java"))),
        Set.of(Path.of("src", "main", "resources")),
        Stream.concat(api.stream(), provided.stream()).toList(),
        api,
        platform
    );

    final List<TestSourceSetDependency> testCompileAndRun = new ArrayList<>(List.of(
        main,
        access,
        GroupArtifact.parse("org.assertj:assertj-core"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-api"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-params"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-engine"),
        GroupArtifact.parse("org.mockito:mockito-core"),
        GroupArtifact.parse("org.mockito:mockito-junit-jupiter"),
        GroupArtifact.parse("org.springframework:spring-test")
    ));
    for (final MainSourceSetDependency d : provided) {
      addTestDependency(testCompileAndRun, d);
    }

    final var testRuntime = new ArrayList<>(testCompileAndRun);
    testRuntime.add(GroupArtifact.parse("jakarta.servlet.jsp.jstl:jakarta.servlet.jsp.jstl-api"));
    testRuntime.add(GroupArtifact.parse("org.junit.platform:junit-platform-launcher"));

    final var test = new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "test", "java"))),
        Set.of(Path.of("src", "test", "resources")),
        testCompileAndRun,
        testRuntime,
        platform
    );
    return projectService.create(
        "org.springframework.security",
        "spring-security-taglibs",
        "7.0.0",
        projectBuilder -> projectBuilder
            .withPath(Path.of("taglibs"))
            .withArtifactLayout(artifactLayout)
            .withSourceSets(main, test)
    );
  }

  private static Project createProjectKerberosCore(
      final ProjectService projectService,
      final DependencyConstraints platform,
      final Project.ArtifactLayout artifactLayout,
      final Project core
  ) {
    final List<MainSourceSetDependency> api = List.of(core);
    final var main = new MainSourceSetArgs(
        SourceSet.Id.MAIN.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "main", "java"))),
        Set.of(Path.of("src", "main", "resources")),
        api,
        api,
        platform
    );

    final List<TestSourceSetDependency> testCompileAndRun = new ArrayList<>(List.of(
        main,
        GroupArtifact.parse("org.assertj:assertj-core"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter"),
        GroupArtifact.parse("org.mockito:mockito-junit-jupiter")
    ));

    final var testRuntime = new ArrayList<>(testCompileAndRun);
    testRuntime.add(GroupArtifact.parse("org.junit.platform:junit-platform-launcher"));

    final var test = new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "test", "java"))),
        Set.of(Path.of("src", "test", "resources")),
        testCompileAndRun,
        testRuntime,
        platform
    );
    return projectService.create(
        "org.springframework.security",
        "spring-security-kerberos-core",
        "7.0.0",
        projectBuilder -> projectBuilder
            .withPath(Path.of("kerberos", "kerberos-core"))
            .withArtifactLayout(artifactLayout)
            .withSourceSets(main, test)
    );
  }

  private static Project createProjectKerberosTest(
      final ProjectService projectService,
      final DependencyConstraints platform,
      final Project.ArtifactLayout artifactLayout
  ) {
    final List<MainSourceSetDependency> api = List.of(
        GroupArtifact.parse("org.apache.kerby:kerb-simplekdc"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter")
    );
    final var main = new MainSourceSetArgs(
        SourceSet.Id.MAIN.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "main", "java"))),
        Set.of(Path.of("src", "main", "resources")),
        api,
        api,
        platform
    );

    final List<TestSourceSetDependency> testCompileAndRun = new ArrayList<>(List.of(
        main,
        GroupArtifact.parse("org.assertj:assertj-core"),
        GroupArtifact.parse("org.mockito:mockito-junit-jupiter"),
        GroupArtifact.parse("org.springframework:spring-test")
    ));

    final var testRuntime = new ArrayList<>(testCompileAndRun);
    testRuntime.add(GroupArtifact.parse("org.junit.platform:junit-platform-launcher"));

    final var test = new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "test", "java"))),
        Set.of(Path.of("src", "test", "resources")),
        testCompileAndRun,
        testRuntime,
        platform
    );
    return projectService.create(
        "org.springframework.security",
        "spring-security-kerberos-test",
        "7.0.0",
        projectBuilder -> projectBuilder
            .withPath(Path.of("kerberos", "kerberos-test"))
            .withArtifactLayout(artifactLayout)
            .withSourceSets(main, test)
    );
  }

  private static Project createProjectCore(
      final ProjectService projectService,
      final DependencyConstraints platform,
      final Project.ArtifactLayout artifactLayout, final Project crypto
  ) {
    final List<MainSourceSetDependency> api = List.of(
        crypto,
        // api
        GroupArtifact.parse("org.springframework:spring-aop"),
        GroupArtifact.parse("org.springframework:spring-beans"),
        GroupArtifact.parse("org.springframework:spring-context"),
        GroupArtifact.parse("org.springframework:spring-core"),
        GroupArtifact.parse("org.springframework:spring-expression"),
        GroupArtifact.parse("io.micrometer:micrometer-observation")
    );
    final List<MainSourceSetDependency> optional = List.of(
        GroupArtifact.parse("com.fasterxml.jackson.core:jackson-databind"),
        GroupArtifact.parse("io.micrometer:context-propagation"),
        GroupArtifact.parse("io.projectreactor:reactor-core"),
        GroupArtifact.parse("jakarta.annotation:jakarta.annotation-api"),
        GroupArtifact.parse("org.aspectj:aspectjrt"),
        GroupArtifact.parse("org.springframework:spring-jdbc"),
        GroupArtifact.parse("org.springframework:spring-tx"),
        GroupArtifact.parse("org.jetbrains.kotlinx:kotlinx-coroutines-reactor"),
        GroupArtifact.parse("tools.jackson.core:jackson-databind")
    );
    final var main = new MainSourceSetArgs(
        SourceSet.Id.MAIN.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "main", "java"))),
        Set.of(Path.of("src", "main", "resources")),
        Stream.concat(api.stream(), optional.stream()).toList(),
        api,
        platform
    );
    final List<TestSourceSetDependency> testCompileAndRun = new ArrayList<>(List.of(
        main,
        GroupArtifact.parse("commons-collections:commons-collections"),
        GroupArtifact.parse("com.fasterxml.jackson.datatype:jackson-datatype-jsr310"),
        GroupArtifact.parse("io.projectreactor:reactor-test"),
        GroupArtifact.parse("org.assertj:assertj-core"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-api"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-params"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-engine"),
        GroupArtifact.parse("org.mockito:mockito-core"),
        GroupArtifact.parse("org.mockito:mockito-junit-jupiter"),
        GroupArtifact.parse("org.springframework:spring-core-test"),
        GroupArtifact.parse("org.springframework:spring-test"),
        GroupArtifact.parse("org.skyscreamer:jsonassert"),
        GroupArtifact.parse("org.springframework:spring-test"),
        GroupArtifact.parse("org.jetbrains.kotlin:kotlin-reflect"),
        GroupArtifact.parse("org.jetbrains.kotlin:kotlin-stdlib-jdk8"),
        GroupArtifact.parse("io.mockk:mockk")
    ));
    for (final MainSourceSetDependency d : optional) {
      switch (d) {
        case LocalJar localJar -> testCompileAndRun.add(localJar);
        case Project project -> testCompileAndRun.add(project);
        case GroupArtifact groupArtifact -> testCompileAndRun.add(groupArtifact);
        case GroupArtifactVersion groupArtifactVersion ->
            testCompileAndRun.add(groupArtifactVersion);
        case SourceSet sourceSet -> testCompileAndRun.add(sourceSet);
      }
    }

    final var testRuntime = new ArrayList<>(testCompileAndRun);
    testRuntime.add(GroupArtifact.parse("org.hsqldb:hsqldb"));
    testRuntime.add(GroupArtifact.parse("org.junit.platform:junit-platform-launcher"));

    final var test = new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        SourceRoots.javaSources(Set.of(Path.of("src", "test", "java"))),
        Set.of(Path.of("src", "test", "resources")),
        testCompileAndRun,
        testRuntime,
        platform
    );
    return projectService.create(
        "org.springframework.security",
        "spring-security-core",
        "7.0.0",
        projectBuilder -> projectBuilder
            .withPath(Path.of("core"))
            .withArtifactLayout(artifactLayout)
            .withSourceSets(main, test)
    );
  }

  private static DependencyService mavenArtifactResolver(final Path workdir,
      final ProjectService projectService) {
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
    session.setWorkspaceReader(new ProjectWorkspaceReader(
        new WorkspaceRepository("build-system"),
        workdir,
        projectService
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
    // OpenSAML 5.x (a transitive dependency of spring-security-saml2-service-provider, which
    // spring-security-config compiles against) is not published to Maven Central. The local Nexus
    // proxies it through the 'shibboleth' repository configured by scripts/nexus-init.sh.
    final List<org.eclipse.aether.repository.RemoteRepository> repositories = List.of(
        new org.eclipse.aether.repository.RemoteRepository
            .Builder("nexus", "default", "http://" + nexusHost + ":8081/repository/maven-central")
            .build(),
        new org.eclipse.aether.repository.RemoteRepository
            .Builder("shibboleth", "default",
                "http://" + nexusHost + ":8081/repository/shibboleth")
            .build()
    );
    return new MavenArtifactResolverDependencyService(repoSystem, session, repositories);
  }
}
