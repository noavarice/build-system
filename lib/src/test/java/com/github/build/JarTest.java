package com.github.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
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
    final var config = new JarArgs(
        jarPath,
        Map.of(Path.of("README.txt"), new JarArgs.Content.Bytes(content))
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
  DynamicTest[] creatingJarProjectWorks(@TempDir final Path tempDir) throws IOException {
    // setup source file
    final Path source = Files.writeString(
        tempDir.resolve("HelloWorld.java"),
        ResourceUtils.readString("/HelloWorld.java")
    );

    // compile sources
    final Path classFile;
    {
      final var args = new CompileArgs(Set.of(source), tempDir.resolve("classes"), Set.of());
      assumeTrue(Build.compile(args));

      classFile = args.classesDir().resolve("org/example/HelloWorld.class");
      assumeThat(classFile).isNotEmptyFile();
    }

    final var args = new JarArgs(
        tempDir.resolve("app.jar"),
        Map.of(Path.of("org/example/HelloWorld.class"), new JarArgs.Content.File(classFile))
    );
    assumeThat(args.path()).doesNotExist();

    return new DynamicTest[]{
        dynamicTest(
            "Check creating JAR succeeds",
            () -> assertThatCode(() -> Build.createJar(args)).doesNotThrowAnyException()
        ),
        dynamicTest(
            "Check JAR generated",
            () -> assertThat(args.path()).isNotEmptyFile()
        ),
    };
  }
}
