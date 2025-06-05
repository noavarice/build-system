package com.github.build;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/**
 * JAR tests.
 *
 * @author noavarice
 * @since 1.0.0
 */
@DisplayName("JAR tests")
class JarTest {

  @DisplayName("Creating JAR works")
  @TestFactory
  DynamicTest[] creatingJarWorks(@TempDir final Path tempDir) {
    final var jarPath = tempDir.resolve("app.jar");
    final var content = "Hello, world!".getBytes(StandardCharsets.UTF_8);
    final var config = new JarConfig(
        jarPath,
        Map.of(Path.of("README.txt"), new JarConfig.Content.Bytes(content))
    );
    return new DynamicTest[]{
        dynamicTest(
            "Check creating JAR succeeds",
            () -> assertDoesNotThrow(() -> Build.createJar(config))
        ),
        dynamicTest(
            "Check class file generated",
            () -> assertTrue(Files.exists(jarPath))
        ),
    };
  }

  @DisplayName("Creating JAR for project works")
  @TestFactory
  DynamicTest[] creatingJarProjectWorks(@TempDir final Path tempDir) {
    setupHelloWorld(tempDir);
    final var main = new SourceSet(
        new SourceSet.Id("main"),
        List.of(Path.of("src/main/java")),
        List.of(),
        SourceSet.Type.PROD,
        Set.of()
    );
    final var project = new Project(
        new Project.Id("project-jar"),
        Path.of("."),
        Set.of(main),
        Project.ArtifactLayout.DEFAULT
    );

    Build.compile(tempDir, project);

    final var jarPath = tempDir.resolve("build/project-jar.jar");
    assumeFalse(Files.exists(jarPath));

    return new DynamicTest[]{
        dynamicTest(
            "Check creating JAR succeeds",
            () -> assertDoesNotThrow(() -> Build.createJar(tempDir, project))
        ),
        dynamicTest(
            "Check class file generated",
            () -> assertTrue(Files.exists(jarPath))
        ),
    };
  }

  private static void setupHelloWorld(final Path tempDir) {
    final String text;
    try (final var is = JarTest.class.getResourceAsStream("/HelloWorld.java")) {
      final byte[] bytes = Objects.requireNonNull(is).readAllBytes();
      text = new String(bytes, StandardCharsets.UTF_8);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    final Path sourcePath;
    try {
      final var path = Files.createDirectories(tempDir.resolve("src/main/java/org/example"));
      sourcePath = Files.writeString(
          path.resolve("HelloWorld.java"),
          text,
          StandardOpenOption.CREATE
      );
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    assumeTrue(Files.exists(sourcePath));
  }
}
