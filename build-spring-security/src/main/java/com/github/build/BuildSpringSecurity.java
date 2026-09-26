package com.github.build;

import com.github.build.compile.CompileService;
import com.github.build.compile.CompilerOptions;
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
import com.github.build.util.JavaCommandBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
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
    final var compileService = new CompileService();
    final var dependencyService = mavenArtifactResolver(workdir, projectService);
    final var testService = new TestService(dependencyService);
    final var jarService = new JarService();
    final BuildService service = new BuildService(compileService, dependencyService, jarService);

    final DependencyConstraints platform = getPlatform(dependencyService);
    final Project crypto = createProjectCrypto(projectService, platform);
    final Project core = createProjectCore(projectService, platform, crypto);
    final Project data = createProjectData(projectService, platform, core);
    final var projects = List.of(crypto, core, data);

    final Path license = workdir.resolve("LICENSE.txt");
    final var compilerOptions = CompilerOptions
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
      final boolean mainCompiled = service.compileMain(workdir, project, compilerOptions);
      if (!mainCompiled) {
        log.error("Build failed");
        System.exit(1);
        return;
      }
      service.copyResources(workdir, project, SourceSet.Id.MAIN);
      if (project == core) {
        generateSpringVersionsFile(workdir, project, springCoreVersion);
      }

      final var additionalEntries = new HashMap<Path, JarArgs.Content>();
      additionalEntries.put(Path.of("META-INF/LICENSE.txt"), new JarArgs.Content.File(license));

      final var manifest = JarManifest
          .builder()
          .setVersion("1.0")
          .setCreatedBy(
              System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ')'
          )
          .setImplementationTitle(project.artifactId())
          .setImplementationVersion(project.version())
          .build();
      service.createJar(workdir, project.mainSourceSet(), additionalEntries, manifest);

      log.info("[project={}] Compiling test source set", project.artifactId());
      final boolean testCompiled = service.compileTest(workdir, project, compilerOptions);
      if (!testCompiled) {
        log.error("Build failed");
        System.exit(1);
        return;
      }
      service.copyResources(workdir, project, SourceSet.Id.TEST);
      service.createJar(workdir, project.testSourceSet(), Map.of(), null);

      final String buildRuntimePathStr = System.getProperty("buildRuntimePath");
      final List<Path> buildRuntimePath = Stream
          .of(buildRuntimePathStr.split(",", -1))
          .map(Path::of)
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
            GroupArtifactVersion.parse("org.springframework:spring-framework-bom:7.0.0"),
            GroupArtifactVersion.parse("io.projectreactor:reactor-bom:2025.0.0"),
            GroupArtifactVersion.parse("org.springframework.data:spring-data-bom:2025.1.0"),
            GroupArtifactVersion.parse("io.rsocket:rsocket-bom:1.1.5"),
            GroupArtifactVersion.parse("org.junit:junit-bom:6.0.1"),
            GroupArtifactVersion.parse("org.mockito:mockito-bom:5.17.0"),
            GroupArtifactVersion.parse("org.jetbrains.kotlin:kotlin-bom:2.2.21"),
            GroupArtifactVersion.parse("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.10.2"),
            GroupArtifactVersion.parse("com.fasterxml.jackson:jackson-bom:2.20.0"),
            GroupArtifactVersion.parse("tools.jackson:jackson-bom:3.0.1")
        )
        .copy()
        .withExactVersion(
            "ch.qos.logback:logback-classic:1.5.20",
            "com.google.inject:guice:3.0",
            "com.nimbusds:nimbus-jose-jwt:10.4",
            "com.nimbusds:oauth2-oidc-sdk:11.26.1",
            "com.squareup.okhttp3:mockwebserver:3.14.9",
            "com.squareup.okhttp3:okhttp:3.14.9",
            "com.unboundid:unboundid-ldapsdk:7.0.3",
            "commons-collections:commons-collections:3.2.2",
            "io.mockk:mockk:1.14.6",
            "io.micrometer:context-propagation:1.1.3",
            "io.micrometer:micrometer-observation:1.14.13",
            "jakarta.annotation:jakarta.annotation-api:3.0.0",
            "jakarta.inject:jakarta.inject-api:2.0.1",
            "jakarta.servlet.jsp.jstl:jakarta.servlet.jsp.jstl-api:3.0.2",
            "jakarta.servlet.jsp:jakarta.servlet.jsp-api:4.0.0",
            "jakarta.servlet:jakarta.servlet-api:6.1.0",
            "jakarta.xml.bind:jakarta.xml.bind-api:4.0.4",
            "jakarta.persistence:jakarta.persistence-api:3.2.0",
            "jakarta.websocket:jakarta.websocket-api:2.2.0",
            "jakarta.websocket:jakarta.websocket-client-api:2.2.0",
            "ldapsdk:ldapsdk:4.1",
            "net.sourceforge.htmlunit:htmlunit:2.70.0",
            "org.htmlunit:htmlunit:4.11.1",
            "org.apache.httpcomponents.client5:httpclient5:5.5.1",
            "org.aspectj:aspectjrt:1.9.25",
            "org.aspectj:aspectjweaver:1.9.25",
            "org.assertj:assertj-core:3.27.6",
            "org.bouncycastle:bcpkix-jdk18on:1.80",
            "org.bouncycastle:bcprov-jdk18on:1.80",
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
            "org.slf4j:slf4j-api:2.0.17",
            "org.springframework.ldap:spring-ldap-core:4.0.0",
            "org.synchronoss.cloud:nio-multipart-parser:1.1.0",
            "org.apache.maven.resolver:maven-resolver-connector-basic:1.9.24",
            "org.apache.maven.resolver:maven-resolver-impl:1.9.24",
            "org.apache.maven.resolver:maven-resolver-transport-http:1.9.24",
            "org.apache.maven:maven-resolver-provider:3.9.11",
            "org.instancio:instancio-junit:3.7.1",
            "com.password4j:password4j:1.8.4"
        )
        .build();
  }

  private static Project createProjectData(
      final ProjectService projectService,
      final DependencyConstraints platform,
      final Project core
  ) {
    final var mainDependencies = List.<MainSourceSetDependency>of(
        core,
        GroupArtifact.parse("jakarta.xml.bind:jakarta.xml.bind-api"),
        GroupArtifact.parse("org.springframework.data:spring-data-commons"),
        GroupArtifact.parse("org.springframework:spring-core")
    );
    final var main = new MainSourceSetArgs(
        SourceSet.Id.MAIN.toString(),
        Set.of(Path.of("src", "main", "java")),
        Set.of(Path.of("src", "main", "resources")),
        mainDependencies,
        mainDependencies,
        platform
    );
    final var testDependencies = List.<TestSourceSetDependency>of(
        main,
        GroupArtifact.parse("org.assertj:assertj-core"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-api"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-params"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-engine"),
        GroupArtifact.parse("org.mockito:mockito-core"),
        GroupArtifact.parse("org.mockito:mockito-junit-jupiter"),
        GroupArtifact.parse("org.springframework:spring-test")
    );
    final var test = new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        Set.of(Path.of("src", "test", "java")),
        Set.of(Path.of("src", "test", "resources")),
        testDependencies,
        testDependencies,
        platform
    );
    final var artifactLayout = new Project.ArtifactLayout(
        Path.of("build-system"),
        Path.of("classes"),
        Path.of("resources")
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

  private static Project createProjectCrypto(
      final ProjectService projectService,
      final DependencyConstraints platform
  ) {
    final var mainDependencies = List.<MainSourceSetDependency>of(
        GroupArtifact.parse("org.springframework:spring-core"),
        GroupArtifact.parse("org.bouncycastle:bcpkix-jdk18on"),
        GroupArtifact.parse("com.password4j:password4j")
    );
    final var main = new MainSourceSetArgs(
        SourceSet.Id.MAIN.toString(),
        Set.of(Path.of("src", "main", "java")),
        Set.of(Path.of("src", "main", "resources")),
        mainDependencies,
        mainDependencies,
        platform
    );
    final var testDependencies = List.<TestSourceSetDependency>of(
        main,
        GroupArtifact.parse("org.assertj:assertj-core"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-api"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-params"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-engine"),
        GroupArtifact.parse("org.mockito:mockito-core"),
        GroupArtifact.parse("org.mockito:mockito-junit-jupiter"),
        GroupArtifact.parse("org.springframework:spring-test")
    );
    final var test = new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        Set.of(Path.of("src", "test", "java")),
        Set.of(Path.of("src", "test", "resources")),
        testDependencies,
        testDependencies,
        platform
    );
    final var artifactLayout = new Project.ArtifactLayout(
        Path.of("build-system"),
        Path.of("classes"),
        Path.of("resources")
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

  private static Project createProjectCore(
      final ProjectService projectService,
      final DependencyConstraints platform,
      final Project crypto
  ) {
    final var mainDependencies = List.<MainSourceSetDependency>of(
        crypto,
        // api
        GroupArtifact.parse("org.springframework:spring-aop"),
        GroupArtifact.parse("org.springframework:spring-beans"),
        GroupArtifact.parse("org.springframework:spring-context"),
        GroupArtifact.parse("org.springframework:spring-core"),
        GroupArtifact.parse("org.springframework:spring-expression"),
        GroupArtifact.parse("io.micrometer:micrometer-observation"),

        // optional
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
        Set.of(Path.of("src", "main", "java")),
        Set.of(Path.of("src", "main", "resources")),
        mainDependencies,
        mainDependencies,
        platform
    );
    final var testCompileAndRun = List.<TestSourceSetDependency>of(
        main,
        GroupArtifact.parse("org.assertj:assertj-core"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-api"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-params"),
        GroupArtifact.parse("org.junit.jupiter:junit-jupiter-engine"),
        GroupArtifact.parse("org.mockito:mockito-core"),
        GroupArtifact.parse("org.mockito:mockito-junit-jupiter"),
        GroupArtifact.parse("org.springframework:spring-test"),
        GroupArtifact.parse("commons-collections:commons-collections"),
        GroupArtifact.parse("com.fasterxml.jackson.datatype:jackson-datatype-jsr310"),
        GroupArtifact.parse("io.projectreactor:reactor-test"),
        GroupArtifact.parse("org.springframework:spring-core-test"),
        GroupArtifact.parse("org.skyscreamer:jsonassert"),
        GroupArtifact.parse("org.springframework:spring-test"),
        GroupArtifact.parse("org.jetbrains.kotlin:kotlin-reflect"),
        GroupArtifact.parse("org.jetbrains.kotlin:kotlin-stdlib-jdk8"),
        GroupArtifact.parse("io.mockk:mockk")
    );
    final var testRuntime = new ArrayList<>(testCompileAndRun);
    testRuntime.add(GroupArtifact.parse("org.hsqldb:hsqldb"));
    testRuntime.add(GroupArtifact.parse("org.junit.platform:junit-platform-launcher"));

    final var test = new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        Set.of(Path.of("src", "test", "java")),
        Set.of(Path.of("src", "test", "resources")),
        testCompileAndRun,
        testRuntime,
        platform
    );
    final var artifactLayout = new Project.ArtifactLayout(
        Path.of("build-system"),
        Path.of("classes"),
        Path.of("resources")
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
    final List<org.eclipse.aether.repository.RemoteRepository> repositories = List.of(
        new org.eclipse.aether.repository.RemoteRepository
            .Builder("nexus", "default", "http://" + nexusHost + ":8081/repository/maven-central")
            .build()
    );
    return new MavenArtifactResolverDependencyService(repoSystem, session, repositories);
  }
}
