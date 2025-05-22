package com.github.build;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/**
 * @author noavarice
 * @since 1.0.0
 */
@DisplayName("Simple build tests")
class SimpleBuildTest {

  @DisplayName("Compiling hello world project works")
  @TestFactory
  DynamicTest[] compilingHelloWorldWorks(@TempDir final Path tempDir) {
    setupHelloWorld(tempDir);
    final var project = new Project(
        new Project.Id("hello-world"),
        Path.of("."),
        Set.of(new SourceSet("main", SourceSet.Type.PROD, Set.of())),
        Project.ArtifactLayout.DEFAULT
    );

    final Path classFile = tempDir.resolve("build/classes/main/org/example/HelloWorld.class");
    assumeTrue(Files.notExists(classFile));
    return new DynamicTest[]{
        dynamicTest(
            "Check build succeeds",
            () -> assertDoesNotThrow(() -> Build.compile(tempDir, project))
        ),
        dynamicTest(
            "Check class file generated",
            () -> assertTrue(Files.exists(classFile))
        ),
    };
  }

  private void setupHelloWorld(final Path tempDir) {
    // language=java
    final var text = """
        package org.example;
                  
        public class HelloWorld {
          public static void main(final String[] args){
            System.out.println("Hello, world!");
          }
        }
        """;
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
