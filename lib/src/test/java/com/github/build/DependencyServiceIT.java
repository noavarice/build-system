package com.github.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.github.build.deps.DependencyConstraints;
import com.github.build.deps.DependencyService;
import com.github.build.deps.GroupArtifactVersion;
import com.github.build.deps.LocalRepository;
import com.github.build.deps.RemoteRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

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

  private final GroupArtifactVersion logbackClassic = GroupArtifactVersion.parse(
      "ch.qos.logback:logback-classic:1.5.18"
  );

  private final GroupArtifactVersion springContext = GroupArtifactVersion.parse(
      "org.springframework:spring-context:6.0.11"
  );

  private final GroupArtifactVersion jacksonParameterNames = GroupArtifactVersion.parse(
      "com.fasterxml.jackson.module:jackson-module-parameter-names:2.15.4"
  );

  private final GroupArtifactVersion springBootWeb = GroupArtifactVersion.parse(
      "org.springframework.boot:spring-boot-starter-web:3.2.5"
  );

  private final Path localRepositoryBasePath;

  private final DependencyService service;

  DependencyServiceIT(@TempDir final Path localRepositoryBasePath) {
    this.localRepositoryBasePath = localRepositoryBasePath;
    final var localRepository = new LocalRepository(
        localRepositoryBasePath,
        Map.of("sha256", "SHA-256")
    );

    final String nexusHost = Objects.requireNonNullElse(
        System.getenv("NEXUS_HOST"),
        "localhost"
    );

    final RemoteRepository mavenCentral = new RemoteRepository(
        URI.create("http://" + nexusHost + ":8081/repository/maven-central"),
        HttpClient.newHttpClient(),
        new ObjectMapper()
    );
    service = new DependencyService(List.of(mavenCentral), localRepository);
  }

  @Nested
  class Resolution {

    @DisplayName("Check resolving transitive dependencies for a single dependency")
    @Test
    void testResolvingTransitiveDependencies() {
      final var ref = new AtomicReference<Set<GroupArtifactVersion>>();
      assertThatCode(
          () -> ref.set(service.resolveTransitive(logbackClassic))
      ).doesNotThrowAnyException();

      final Set<GroupArtifactVersion> actual = ref.get();
      final Set<GroupArtifactVersion> expected = Set.of(
          logbackClassic,
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
          springContext,
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
          jacksonParameterNames,
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
          GroupArtifactVersion.parse(
              "org.springframework.boot:spring-boot-autoconfigure:jar:3.2.5"),
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
          GroupArtifactVersion.parse(
              "org.springframework.boot:spring-boot-starter-tomcat:jar:3.2.5"),
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

    @DisplayName("Check resolving dependencies with hard version requirements")
    @Test
    void testResolvingWithHardVersionRequirements() {
      final var bcpkix = GroupArtifactVersion.parse(
          "org.bouncycastle:bcpkix-jdk18on:1.80"
      );
      assertThatCode(() -> service.resolveTransitive(bcpkix)).doesNotThrowAnyException();
    }
  }

  @DisplayName("""
      Tests for fetching resolved dependencies from remote repositories
      and saving to local repository
      """)
  @Nested
  class FetchingToLocalRepository {

    @DisplayName("Check fetching null dependency set fails")
    @Test
    void testFetchingNullSetFails() {
      assertThrowsExactly(
          NullPointerException.class,
          () -> service.fetchToLocal(null)
      );
    }

    @DisplayName("Check fetching empty dependency set works")
    @Test
    void testFetchingEmptySetWorks() {
      assertThat(service.fetchToLocal(Set.of())).isEmpty();
    }

    @DisplayName("Check fetching single dependency works")
    @TestFactory
    DynamicTest[] testFetchingSingleDependencyWorks() {
      final var gav = GroupArtifactVersion.parse("org.slf4j:slf4j-api:2.0.17");
      final Set<GroupArtifactVersion> dependencies = Set.of(gav);
      final Path expectedJarPath = localRepositoryBasePath.resolve(
          "org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar"
      );
      return new DynamicTest[]{
          dynamicTest(
              "Check JAR is not present yet",
              () -> assertThat(expectedJarPath).doesNotExist()
          ),
          dynamicTest(
              "Check fetching works",
              () -> assertThat(service.fetchToLocal(dependencies)).containsOnly(
                  Map.entry(gav, expectedJarPath)
              )
          ),
      };
    }
  }

  @DisplayName("Getting dependency constraints tests")
  @Nested
  class GetConstraints {

    // TODO: test BOM with parent
    @DisplayName("Check getting constraints from single BOM without parent works")
    @Test
    void testGettingSingleBomWithoutParentWorks() {
      final var springBom = GroupArtifactVersion.parse(
          "org.springframework:spring-framework-bom:7.0.0"
      );
      final DependencyConstraints expected = DependencyConstraints
          .builder()
          .withExactVersion(
              "org.springframework:spring-aop:7.0.0",
              "org.springframework:spring-aspects:7.0.0",
              "org.springframework:spring-beans:7.0.0",
              "org.springframework:spring-context:7.0.0",
              "org.springframework:spring-context-indexer:7.0.0",
              "org.springframework:spring-context-support:7.0.0",
              "org.springframework:spring-core:7.0.0",
              "org.springframework:spring-core-test:7.0.0",
              "org.springframework:spring-expression:7.0.0",
              "org.springframework:spring-instrument:7.0.0",
              "org.springframework:spring-jdbc:7.0.0",
              "org.springframework:spring-jms:7.0.0",
              "org.springframework:spring-messaging:7.0.0",
              "org.springframework:spring-orm:7.0.0",
              "org.springframework:spring-oxm:7.0.0",
              "org.springframework:spring-r2dbc:7.0.0",
              "org.springframework:spring-test:7.0.0",
              "org.springframework:spring-tx:7.0.0",
              "org.springframework:spring-web:7.0.0",
              "org.springframework:spring-webflux:7.0.0",
              "org.springframework:spring-webmvc:7.0.0",
              "org.springframework:spring-websocket:7.0.0"
          )
          .build();
      final DependencyConstraints actual = service.getConstraints(springBom);
      assertEquals(expected, actual);
    }

    @DisplayName("Check getting constraints from multiple BOMs works")
    @Test
    void testGettingMultipleBomWorks() {
      final var springBom = GroupArtifactVersion.parse(
          "org.springframework:spring-framework-bom:7.0.0"
      );
      final var springDataBom = GroupArtifactVersion.parse(
          "org.springframework.data:spring-data-bom:2025.1.0"
      );
      final DependencyConstraints expected = DependencyConstraints
          .builder()
          .withExactVersion(
              // Spring Framework BOM
              "org.springframework:spring-aop:7.0.0",
              "org.springframework:spring-aspects:7.0.0",
              "org.springframework:spring-beans:7.0.0",
              "org.springframework:spring-context:7.0.0",
              "org.springframework:spring-context-indexer:7.0.0",
              "org.springframework:spring-context-support:7.0.0",
              "org.springframework:spring-core:7.0.0",
              "org.springframework:spring-core-test:7.0.0",
              "org.springframework:spring-expression:7.0.0",
              "org.springframework:spring-instrument:7.0.0",
              "org.springframework:spring-jdbc:7.0.0",
              "org.springframework:spring-jms:7.0.0",
              "org.springframework:spring-messaging:7.0.0",
              "org.springframework:spring-orm:7.0.0",
              "org.springframework:spring-oxm:7.0.0",
              "org.springframework:spring-r2dbc:7.0.0",
              "org.springframework:spring-test:7.0.0",
              "org.springframework:spring-tx:7.0.0",
              "org.springframework:spring-web:7.0.0",
              "org.springframework:spring-webflux:7.0.0",
              "org.springframework:spring-webmvc:7.0.0",
              "org.springframework:spring-websocket:7.0.0",

              // Spring Data BOM
              "org.springframework.data:spring-data-cassandra:5.0.0",
              "org.springframework.data:spring-data-commons:4.0.0",
              "org.springframework.data:spring-data-couchbase:6.0.0",
              "org.springframework.data:spring-data-elasticsearch:6.0.0",
              "org.springframework.data:spring-data-jdbc:4.0.0",
              "org.springframework.data:spring-data-r2dbc:4.0.0",
              "org.springframework.data:spring-data-relational:4.0.0",
              "org.springframework.data:spring-data-jpa:4.0.0",
              "org.springframework.data:spring-data-envers:4.0.0",
              "org.springframework.data:spring-data-mongodb:5.0.0",
              "org.springframework.data:spring-data-neo4j:8.0.0",
              "org.springframework.data:spring-data-redis:4.0.0",
              "org.springframework.data:spring-data-rest-webmvc:5.0.0",
              "org.springframework.data:spring-data-rest-core:5.0.0",
              "org.springframework.data:spring-data-rest-hal-explorer:5.0.0",
              "org.springframework.data:spring-data-keyvalue:4.0.0",
              "org.springframework.data:spring-data-ldap:4.0.0"
          )
          .build();
      final DependencyConstraints actual = service.getConstraints(springBom, springDataBom);
      assertEquals(expected, actual);
    }
  }
}
