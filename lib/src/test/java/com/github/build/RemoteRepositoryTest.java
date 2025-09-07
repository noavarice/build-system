package com.github.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.github.build.deps.ArtifactResolutionResult;
import com.github.build.deps.Dependency;
import com.github.build.deps.Pom;
import com.github.build.deps.RemoteRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * {@link RemoteRepository} test.
 *
 * @author noavarice
 * @since 1.0.0
 */
@DisplayName("Tests for remote repository")
class RemoteRepositoryTest {

  // TODO: setup separate Maven repository (via Testcontainers, for example)
  private final URI baseUri = URI.create("https://repo.maven.apache.org/maven2");

  private final HttpClient client = HttpClient.newHttpClient();

  private final RemoteRepository repo = new RemoteRepository(baseUri, client);

  private final Dependency.Remote.Exact slf4j = new Dependency.Remote.Exact(
      "org.slf4j",
      "slf4j-api",
      "2.0.17"
  );

  private final Dependency.Remote.Exact logbackParent = new Dependency.Remote.Exact(
      "ch.qos.logback",
      "logback-parent",
      "1.5.8"
  );

  private final Dependency.Remote.Exact logbackClassic = new Dependency.Remote.Exact(
      "ch.qos.logback",
      "logback-classic",
      "1.5.18"
  );

  private final Dependency.Remote.Exact nonExistentSlf4j = new Dependency.Remote.Exact(
      "org.slf4j",
      "slf4j-api",
      "1.9.0"
  );

  @DisplayName("Tests for downloading dependency JAR")
  @Nested
  class Downloading {

    @DisplayName("Check downloading dependency jar works")
    @TestFactory
    DynamicTest[] testDownloadingJar() {
      final var ref = new AtomicReference<Optional<ArtifactResolutionResult>>();
      final byte[] expectedBytes = ResourceUtils.read("/slf4j-api-2.0.17.jar");
      return new DynamicTest[]{
          dynamicTest("Check method works", () ->
              assertThatCode(() -> ref.set(repo.download(slf4j))).doesNotThrowAnyException()
          ),
          dynamicTest("Check result is not empty", () -> assertThat(ref.get()).isPresent()),
          dynamicTest("Check actual JAR bytes match", () -> {
            final byte[] actualBytes;
            try (final var stream = ref.get().get().stream()) {
              actualBytes = stream.readAllBytes();
            }
            assertThat(actualBytes).isEqualTo(expectedBytes);
          }),
      };
    }

    @DisplayName("Check downloading non-existent dependency jar works")
    @Test
    void testDownloadingNonExistentJar() {
      assertThat(repo.download(nonExistentSlf4j)).isEmpty();
    }
  }

  @DisplayName("Tests for getting dependency POM")
  @Nested
  class GettingPom {

    @DisplayName("Check downloading non-existent dependency POM works")
    @Test
    void testGettingPomForNonExistentDependency() {
      assertThat(repo.getPom(nonExistentSlf4j.coordinates())).isEmpty();
    }

    @DisplayName("Check downloading existing dependency POM works")
    @TestFactory
    DynamicTest[] testGettingPomForExistingDependencyWorks() {
      final var ref = new AtomicReference<Optional<Pom>>();

      // checking method works beforehand for simplifying actual tests
      assertThatCode(
          () -> ref.set(repo.getPom(logbackParent.coordinates()))
      ).doesNotThrowAnyException();

      final Pom pom = ref.get().orElseThrow();
      return new DynamicTest[]{
          dynamicTest(
              "Check group ID",
              () -> assertThat(pom.groupId()).isEqualTo("ch.qos.logback")
          ),
          dynamicTest(
              "Check artifactId",
              () -> assertThat(pom.artifactId()).isEqualTo("logback-parent")
          ),
          dynamicTest(
              "Check version",
              () -> assertThat(pom.version()).isEqualTo("1.5.8")
          ),
          dynamicTest(
              "Check parent",
              () -> assertThat(pom.parent()).isNull()
          ),
          dynamicTest(
              "Check property count",
              () -> assertThat(pom.properties()).hasSize(39)
          ),
          dynamicTest(
              "Check one property",
              () -> assertThat(pom.properties().get("jdk.version")).isEqualTo("11")
          ),
          dynamicTest(
              "Check dependency count",
              () -> assertThat(pom.dependencies()).hasSize(4)
          ),
          dynamicTest(
              "Check one dependency",
              () -> assertThat(pom.dependencies().getFirst())
                  .isEqualTo(
                      new Pom.Dependency(
                          "org.assertj",
                          "assertj-core",
                          "${assertj-core.version}",
                          Pom.Dependency.Scope.TEST,
                          Set.of(),
                          false
                      )
                  )
          ),
          dynamicTest(
              "Check dependency management entries count",
              () -> assertThat(pom.dependencyManagement()).hasSize(16)
          ),
          dynamicTest(
              "Check one dependency management entry",
              () -> assertThat(pom.dependencyManagement().getFirst())
                  .isEqualTo(
                      new Pom.Dependency(
                          "ch.qos.logback",
                          "logback-core",
                          "${project.version}",
                          Pom.Dependency.Scope.COMPILE,
                          Set.of(),
                          false
                      )
                  )
          ),
      };
    }

    @DisplayName("Check optional dependencies")
    @TestFactory
    DynamicTest[] testOptionalDependencies() {
      final var ref = new AtomicReference<Optional<Pom>>();

      // checking method works beforehand for simplifying actual tests
      assertThatCode(
          () -> ref.set(repo.getPom(logbackClassic.coordinates()))
      ).doesNotThrowAnyException();

      final Pom pom = ref.get().orElseThrow();
      final var compileDependencies = pom.dependencies()
          .stream()
          .filter(d -> d.scope() == Pom.Dependency.Scope.COMPILE)
          .toList();
      return new DynamicTest[]{
          dynamicTest(
              "Check core dependency is required",
              () -> assertThat(compileDependencies.getFirst().optional()).isFalse()
          ),
          dynamicTest(
              "Check SLF4J dependency is required",
              () -> assertThat(compileDependencies.get(1).optional()).isFalse()
          ),
          dynamicTest(
              "Check Jakarta Mail API dependency is optional",
              () -> assertThat(compileDependencies.get(2).optional()).isTrue()
          ),
          dynamicTest(
              "Check Jakarta Activation API dependency is optional",
              () -> assertThat(compileDependencies.get(3).optional()).isTrue()
          ),
          dynamicTest(
              "Check Janino dependency is optional",
              () -> assertThat(compileDependencies.get(4).optional()).isTrue()
          ),
      };
    }
  }
}
