package com.github.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.github.build.deps.Dependency;
import com.github.build.deps.DependencyService;
import com.github.build.deps.GroupArtifactVersion;
import com.github.build.deps.RemoteRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * {@link DependencyService} tests.
 *
 * @author noavarice
 * @since 1.0.0
 */
// TODO: setup proxy repository
// TODO: move to integration tests
@DisplayName("Tests for dependency service")
class DependencyServiceIT {

  private final Dependency.Remote.Exact logbackClassic = new Dependency.Remote.Exact(
      "ch.qos.logback",
      "logback-classic",
      "1.5.18"
  );

  private final Dependency.Remote.Exact springContext = new Dependency.Remote.Exact(
      "org.springframework",
      "spring-context",
      "6.0.11"
  );

  private final Dependency.Remote.Exact jacksonParameterNames = new Dependency.Remote.Exact(
      "com.fasterxml.jackson.module",
      "jackson-module-parameter-names",
      "2.15.4"
  );

  private final Dependency.Remote.Exact springBootWeb = new Dependency.Remote.Exact(
      "org.springframework.boot",
      "spring-boot-starter-web",
      "3.2.5"
  );

  private final RemoteRepository mavenCentral = new RemoteRepository(
      // TODO: externalize
      URI.create("http://localhost:8081/repository/maven-central"),
      HttpClient.newHttpClient()
  );

  private final DependencyService service = new DependencyService(List.of(mavenCentral));

  @DisplayName("Check resolving transitive dependencies for a single dependency")
  @Test
  void testResolvingTransitiveDependencies() {
    final var ref = new AtomicReference<Set<GroupArtifactVersion>>();
    assertThatCode(
        () -> ref.set(service.resolveTransitive(logbackClassic))
    ).doesNotThrowAnyException();

    final Set<GroupArtifactVersion> actual = ref.get();
    final Set<GroupArtifactVersion> expected = Set.of(
        logbackClassic.gav(),
        GroupArtifactVersion.parse("ch.qos.logback:logback-core:1.5.18"),
        GroupArtifactVersion.parse("org.slf4j:slf4j-api:2.0.17")
    );

    assertThat(actual).isEqualTo(expected);
  }

  @DisplayName("Check resolving more transitive dependencies for a single dependency")
  @Test
  void testResolvingMoreTransitiveDependencies() {
    final var ref = new AtomicReference<Set<GroupArtifactVersion>>();
    assertThatCode(
        () -> ref.set(service.resolveTransitive(springContext))
    ).doesNotThrowAnyException();

    final Set<GroupArtifactVersion> actual = ref.get();
    final Set<GroupArtifactVersion> expected = Set.of(
        springContext.gav(),
        GroupArtifactVersion.parse("org.springframework:spring-aop:6.0.11"),
        GroupArtifactVersion.parse("org.springframework:spring-beans:6.0.11"),
        GroupArtifactVersion.parse("org.springframework:spring-core:6.0.11"),
        GroupArtifactVersion.parse("org.springframework:spring-expression:6.0.11"),
        GroupArtifactVersion.parse("org.springframework:spring-jcl:6.0.11")
    );

    assertThat(actual).isEqualTo(expected);
  }

  @DisplayName("Check resolving dependency with a chain of parents")
  @Test
  @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  void testResolvingWithChainOfParents() {
    final var ref = new AtomicReference<Set<GroupArtifactVersion>>();
    assertThatCode(
        () -> ref.set(service.resolveTransitive(jacksonParameterNames))
    ).doesNotThrowAnyException();

    final Set<GroupArtifactVersion> actual = ref.get();
    final Set<GroupArtifactVersion> expected = Set.of(
        jacksonParameterNames.gav(),
        GroupArtifactVersion.parse("com.fasterxml.jackson.core:jackson-annotations:2.15.4"),
        GroupArtifactVersion.parse("com.fasterxml.jackson.core:jackson-core:2.15.4"),
        GroupArtifactVersion.parse("com.fasterxml.jackson.core:jackson-databind:2.15.4")
    );

    assertThat(actual).isEqualTo(expected);
  }

  // TODO: copy this test for Graph
  @DisplayName("Check resolving even more transitive dependencies for a single dependency")
  @Test
  @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  void testResolvingEvenMoreTransitiveDependencies() {
    final var ref = new AtomicReference<Set<GroupArtifactVersion>>();
    assertThatCode(
        () -> ref.set(service.resolveTransitive(springBootWeb))
    ).doesNotThrowAnyException();

    final Set<GroupArtifactVersion> actual = ref.get();
    final Set<GroupArtifactVersion> expected = Set.of(
        GroupArtifactVersion.parse("org.springframework.boot:spring-boot-starter-web:jar:3.2.5"),
        GroupArtifactVersion.parse("org.springframework.boot:spring-boot-starter:jar:3.2.5"),
        GroupArtifactVersion.parse("org.springframework.boot:spring-boot:jar:3.2.5"),
        GroupArtifactVersion.parse("org.springframework.boot:spring-boot-autoconfigure:jar:3.2.5"),
        GroupArtifactVersion.parse(
            "org.springframework.boot:spring-boot-starter-logging:jar:3.2.5"),
        GroupArtifactVersion.parse("ch.qos.logback:logback-classic:jar:1.4.14"),
        GroupArtifactVersion.parse("ch.qos.logback:logback-core:jar:1.4.14"),
        GroupArtifactVersion.parse("org.slf4j:slf4j-api:jar:2.0.7"),
        GroupArtifactVersion.parse("org.apache.logging.log4j:log4j-to-slf4j:jar:2.21.1"),
        GroupArtifactVersion.parse("org.apache.logging.log4j:log4j-api:jar:2.21.1"),
        GroupArtifactVersion.parse("org.slf4j:jul-to-slf4j:jar:2.0.13"),
        GroupArtifactVersion.parse("jakarta.annotation:jakarta.annotation-api:jar:2.1.1"),
        GroupArtifactVersion.parse("org.springframework:spring-core:jar:6.1.6"),
        GroupArtifactVersion.parse("org.springframework:spring-jcl:jar:6.1.6"),
        GroupArtifactVersion.parse("org.yaml:snakeyaml:jar:2.2"),
        GroupArtifactVersion.parse("org.springframework.boot:spring-boot-starter-json:jar:3.2.5"),
        GroupArtifactVersion.parse("com.fasterxml.jackson.core:jackson-databind:jar:2.15.4"),
        GroupArtifactVersion.parse("com.fasterxml.jackson.core:jackson-annotations:jar:2.15.4"),
        GroupArtifactVersion.parse("com.fasterxml.jackson.core:jackson-core:jar:2.15.4"),
        GroupArtifactVersion.parse(
            "com.fasterxml.jackson.datatype:jackson-datatype-jdk8:jar:2.15.4"),
        GroupArtifactVersion.parse(
            "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:jar:2.15.4"),
        GroupArtifactVersion.parse(
            "com.fasterxml.jackson.module:jackson-module-parameter-names:jar:2.15.4"),
        GroupArtifactVersion.parse("org.springframework.boot:spring-boot-starter-tomcat:jar:3.2.5"),
        GroupArtifactVersion.parse("org.apache.tomcat.embed:tomcat-embed-core:jar:10.1.20"),
        GroupArtifactVersion.parse("org.apache.tomcat.embed:tomcat-embed-el:jar:10.1.20"),
        GroupArtifactVersion.parse("org.apache.tomcat.embed:tomcat-embed-websocket:jar:10.1.20"),
        GroupArtifactVersion.parse("org.springframework:spring-web:jar:6.1.6"),
        GroupArtifactVersion.parse("org.springframework:spring-beans:jar:6.1.6"),
        GroupArtifactVersion.parse("io.micrometer:micrometer-observation:jar:1.12.5"),
        GroupArtifactVersion.parse("io.micrometer:micrometer-commons:jar:1.12.5"),
        GroupArtifactVersion.parse("org.springframework:spring-webmvc:jar:6.1.6"),
        GroupArtifactVersion.parse("org.springframework:spring-aop:jar:6.1.6"),
        GroupArtifactVersion.parse("org.springframework:spring-context:jar:6.1.6"),
        GroupArtifactVersion.parse("org.springframework:spring-expression:jar:6.1.6")
    );

    final var actualSorted = actual
        .stream()
        .map(c -> c.toString())
        .sorted()
        .toList();
    final var expectedSorted = expected
        .stream()
        .map(c -> c.toString())
        .sorted()
        .toList();
    assertThat(actualSorted).isEqualTo(expectedSorted);
  }
}
