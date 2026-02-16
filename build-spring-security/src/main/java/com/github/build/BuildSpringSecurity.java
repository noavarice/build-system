package com.github.build;

import com.github.build.compile.CompileService;
import com.github.build.deps.DependencyConstraints;
import com.github.build.deps.DependencyService;
import com.github.build.deps.GroupArtifactVersion;
import com.github.build.deps.MavenArtifactResolverDependencyService;
import com.github.build.test.JUnitTestArgs;
import com.github.build.test.TestResults;
import com.github.build.test.TestService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
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
      workdir = Path.of(args[0]);
    }

    final var compileService = new CompileService();
    final var dependencyService = mavenArtifactResolver();
    final var testService = new TestService(dependencyService);
    final BuildService service = new BuildService(compileService, dependencyService);

    final DependencyConstraints platform = getPlatform(dependencyService);
    final Set<Project> projects = Set.of(
        createProjectCrypto(platform)
    );

    for (final Project project : projects) {
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

  private static Project createProjectCrypto(final DependencyConstraints platform) {
    final var main = SourceSet
        .withMainDefaults()
        .compileAndRunWith(
            "org.springframework:spring-core",
            "org.bouncycastle:bcpkix-jdk18on",
            "com.password4j:password4j"
        )
        .withDependencyConstraints(platform)
        .build();
    final var test = SourceSet
        .withTestDefaults()
        .compileAndRunWith(main)
        .compileAndRunWith(
            "org.assertj:assertj-core",
            "org.junit.jupiter:junit-jupiter-api",
            "org.junit.jupiter:junit-jupiter-params",
            "org.junit.jupiter:junit-jupiter-engine",
            "org.mockito:mockito-core",
            "org.mockito:mockito-junit-jupiter",
            "org.springframework:spring-test"
        )
        .withDependencyConstraints(platform)
        .build();
    final var artifactLayout = new Project.ArtifactLayout(
        Path.of("build-system"),
        Path.of("classes"),
        Path.of("resources")
    );
    return Project
        .withId("spring-security-crypto")
        .withPath(Path.of("crypto"))
        .withArtifactLayout(artifactLayout)
        .withSourceSet(main)
        .withSourceSet(test)
        .build();
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
