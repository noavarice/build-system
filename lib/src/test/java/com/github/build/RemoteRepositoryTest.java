package com.github.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.github.build.deps.ArtifactResolutionResult;
import com.github.build.deps.Dependency;
import com.github.build.deps.RemoteRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Optional;
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

  private final URI baseUri = URI.create("https://repo.maven.apache.org/maven2");

  private final HttpClient client = HttpClient.newHttpClient();

  private final RemoteRepository repo = new RemoteRepository(baseUri, client);

  private final Dependency.RemoteExact dep = new Dependency.RemoteExact(
      "org.slf4j",
      "slf4j-api",
      "2.0.17"
  );

  private final Dependency.RemoteExact nonExistentDep = new Dependency.RemoteExact(
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
              assertThatCode(() -> ref.set(repo.download(dep))).doesNotThrowAnyException()
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
      assertThat(repo.download(nonExistentDep)).isEmpty();
    }
  }
}
