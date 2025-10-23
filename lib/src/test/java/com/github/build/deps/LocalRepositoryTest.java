package com.github.build.deps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.github.build.ResourceUtils;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/**
 * @author noavarice
 */
@DisplayName("Local repository tests")
class LocalRepositoryTest {

  @DisplayName("Check saving JAR works")
  @TestFactory
  DynamicTest[] savingJarWorks(@TempDir final Path tempDir) {
    final var repository = new LocalRepository(tempDir, Map.of("sha256", "SHA-256"));

    final GroupArtifactVersion gav = GroupArtifactVersion.parse("org.slf4j:slf4j-api:2.0.17");
    final byte[] jarBytes = ResourceUtils.read("/slf4j-api-2.0.17.jar");

    assumeThat(tempDir).isEmptyDirectory();
    final var expectedJarPath = tempDir.resolve(
        "org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar"
    );
    final var expectedHashPath = tempDir.resolve(
        "org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.sha256"
    );
    return new DynamicTest[]{
        dynamicTest(
            "Check method works",
            () -> assertThatCode(() -> repository.saveJar(gav, jarBytes)).doesNotThrowAnyException()
        ),
        dynamicTest(
            "Check JAR created",
            () -> assertThat(expectedJarPath).hasBinaryContent(jarBytes)
        ),
        dynamicTest(
            "Check hash file created",
            () -> assertThat(expectedHashPath).hasContent(
                "7b751d952061954d5abfed7181c1f645d336091b679891591d63329c622eb832"
            )
        ),
    };
  }
}
