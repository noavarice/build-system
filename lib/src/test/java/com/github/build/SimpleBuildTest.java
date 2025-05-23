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

  @DisplayName("Compiling project without required compile classpath dependency fails")
  @TestFactory
  DynamicTest[] compilingWithoutRequiredDependencyFails(@TempDir final Path tempDir) {
    setupHelloWorldWithPlainJarDep(tempDir, false);

    final var project = new Project(
        new Project.Id("missing-required-dependency"),
        Path.of("."),
        Set.of(new SourceSet("main", SourceSet.Type.PROD, Set.of())),
        Project.ArtifactLayout.DEFAULT
    );

    final Path classFile = tempDir.resolve("build/classes/main/org/example/Main.class");
    assumeFalse(Files.exists(classFile));
    return new DynamicTest[]{
        dynamicTest(
            "Check build fails",
            () -> assertDoesNotThrow(() -> Build.compile(tempDir, project))
        ),
        dynamicTest(
            "Check class file not generated",
            () -> assumeFalse(Files.exists(classFile))
        ),
    };
  }

  @DisplayName("Compiling project with plain JAR dependency works")
  @TestFactory
  DynamicTest[] compilingWithPlainJarDependencyWorks(@TempDir final Path tempDir) {
    setupHelloWorldWithPlainJarDep(tempDir, true);

    final var slf4jDep = new Dependency.File(
        tempDir.resolve("slf4j-api.jar"),
        Dependency.Scope.COMPILE
    );
    final var project = new Project(
        new Project.Id("plain-jar"),
        Path.of("."),
        Set.of(new SourceSet("main", SourceSet.Type.PROD, Set.of(slf4jDep))),
        Project.ArtifactLayout.DEFAULT
    );

    final Path classFile = tempDir.resolve("build/classes/main/org/example/Main.class");
    assumeFalse(Files.exists(classFile));
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

  private static void setupHelloWorldWithPlainJarDep(final Path tempDir, final boolean copyJar) {
    final var sourceDirectory = tempDir.resolve("src/main/java/org/example");
    final var testClass = SimpleBuildTest.class;
    try (
        final var mainStream = testClass.getResourceAsStream("/local-jar/Main.java");
        final var jarStream = testClass.getResourceAsStream("/local-jar/slf4j-api-2.0.17.jar")
    ) {
      Files.createDirectories(sourceDirectory);

      @SuppressWarnings("DataFlowIssue")
      final var source = new String(mainStream.readAllBytes(), StandardCharsets.UTF_8);
      Files.writeString(sourceDirectory.resolve("Main.java"), source, StandardOpenOption.CREATE);

      if (copyJar) {
        @SuppressWarnings("DataFlowIssue")
        final byte[] jar = jarStream.readAllBytes();
        Files.write(tempDir.resolve("slf4j-api.jar"), jar, StandardOpenOption.CREATE);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    assumeTrue(Files.exists(sourceDirectory.resolve("Main.java")));
    if (copyJar) {
      assumeTrue(Files.exists(tempDir.resolve("slf4j-api.jar")));
    }
  }
}
