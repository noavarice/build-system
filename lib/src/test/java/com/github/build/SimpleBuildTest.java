package com.github.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * @author noavarice
 * @since 1.0.0
 */
@DisplayName("Simple build tests")
class SimpleBuildTest {

  @DisplayName("Compiling hello world project works")
  @TestFactory
  DynamicTest[] compilingHelloWorldWorks(@TempDir final Path tempDir) {
    setupHelloWorld(tempDir);
    final var main = new SourceSet(
        new SourceSet.Id("main"),
        List.of(Path.of("src/main/java")),
        List.of(),
        SourceSet.Type.PROD,
        Set.of()
    );
    final var project = new Project(
        new Project.Id("hello-world"),
        Path.of("."),
        Set.of(main),
        Project.ArtifactLayout.DEFAULT
    );

    final Path classFile = tempDir.resolve("build/classes/main/org/example/HelloWorld.class");
    assumeTrue(Files.notExists(classFile));
    return new DynamicTest[]{
        dynamicTest(
            "Check build succeeds",
            () -> assertTrue(Build.compile(tempDir, project))
        ),
        dynamicTest(
            "Check class file generated",
            () -> assertTrue(Files.exists(classFile))
        ),
    };
  }

  @DisplayName("Compiling project without required compile classpath dependency fails")
  @TestFactory
  DynamicTest[] compilingWithoutRequiredDependencyFails(@TempDir final Path tempDir) {
    setupHelloWorldWithPlainJarDep(tempDir, false);

    final var main = new SourceSet(
        new SourceSet.Id("main"),
        List.of(Path.of("src/main/java")),
        List.of(),
        SourceSet.Type.PROD,
        Set.of()
    );
    final var project = new Project(
        new Project.Id("missing-required-dependency"),
        Path.of("."),
        Set.of(main),
        Project.ArtifactLayout.DEFAULT
    );

    final Path classFile = tempDir.resolve("build/classes/main/org/example/Main.class");
    assumeFalse(Files.exists(classFile));
    return new DynamicTest[]{
        dynamicTest(
            "Check build fails",
            () -> assertFalse(Build.compile(tempDir, project))
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
    final var main = new SourceSet(
        new SourceSet.Id("main"),
        List.of(Path.of("src/main/java")),
        List.of(),
        SourceSet.Type.PROD,
        Set.of(slf4jDep)
    );
    final var project = new Project(
        new Project.Id("jar-file-dep"),
        Path.of("."),
        Set.of(main),
        Project.ArtifactLayout.DEFAULT
    );

    final Path classFile = tempDir.resolve("build/classes/main/org/example/Main.class");
    assumeFalse(Files.exists(classFile));
    return new DynamicTest[]{
        dynamicTest(
            "Check build succeeds",
            () -> assertTrue(Build.compile(tempDir, project))
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

  @DisplayName("Check copying empty directory to non-existent directory works")
  @TestFactory
  DynamicTest[] copyingEmptyDirectoryWorks(@TempDir final Path tempDir) throws IOException {
    final Path emptyDir = Files.createDirectory(tempDir.resolve("empty"));
    assumeThat(emptyDir).isEmptyDirectory();

    final Path targetDir = tempDir.resolve("target");
    assumeThat(targetDir).doesNotExist();

    return new DynamicTest[]{
        dynamicTest("Check method succeeds", () ->
            assertThatCode(() -> Build.copyDirectory(emptyDir, targetDir))
                .doesNotThrowAnyException()
        ),
        dynamicTest("Check target directory exists",
            () -> assertThat(targetDir).isEmptyDirectory()),
    };
  }

  @DisplayName("Check copying empty directory to existing empty directory works")
  @TestFactory
  DynamicTest[] copyingEmptyDirectoryToExistingDirWorks(
      @TempDir final Path tempDir
  ) throws IOException {
    final Path emptyDir = Files.createDirectory(tempDir.resolve("empty"));
    assumeThat(emptyDir).isEmptyDirectory();

    final Path targetDir = Files.createDirectory(tempDir.resolve("target"));
    assumeThat(targetDir).isEmptyDirectory();

    return new DynamicTest[]{
        dynamicTest("Check method succeeds", () ->
            assertThatCode(() -> Build.copyDirectory(emptyDir, targetDir))
                .doesNotThrowAnyException()
        ),
        dynamicTest("Check target directory exists",
            () -> assertThat(targetDir).isEmptyDirectory()),
    };
  }

  @DisplayName("Check copying non-empty directory to non-existing empty directory works")
  @TestFactory
  DynamicTest[] copyingNonEmptyDirectoryToNonExistingDirWorks(
      @TempDir final Path tempDir
  ) throws IOException {
    final Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
    Files.writeString(sourceDir.resolve("greeting.txt"), "Hello, world!");
    final Path nestedDir = Files.createDirectory(sourceDir.resolve("nested"));
    Files.writeString(nestedDir.resolve("greeting.txt"), "Hello, world!");
    assumeThat(sourceDir).isNotEmptyDirectory();

    final Path targetDir = tempDir.resolve("target");
    assumeThat(targetDir).doesNotExist();

    return new DynamicTest[]{
        dynamicTest("Check method succeeds", () ->
            assertThatCode(() -> Build.copyDirectory(sourceDir, targetDir))
                .doesNotThrowAnyException()
        ),
        dynamicTest("Check target directory has immediate file",
            () -> assertThat(targetDir.resolve("greeting.txt"))
                .hasContent("Hello, world!")
        ),
        dynamicTest("Check target directory has nested file",
            () -> assertThat(targetDir.resolve("nested").resolve("greeting.txt"))
                .hasContent("Hello, world!")
        ),
    };
  }

  @DisplayName("Check copying non-empty directory to existing empty directory works")
  @TestFactory
  DynamicTest[] copyingNonEmptyDirectoryToExistingDirWorks(
      @TempDir final Path tempDir
  ) throws IOException {
    final Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
    Files.writeString(sourceDir.resolve("greeting.txt"), "Hello, world!");
    final Path nestedDir = Files.createDirectory(sourceDir.resolve("nested"));
    Files.writeString(nestedDir.resolve("greeting.txt"), "Hello, world!");
    assumeThat(sourceDir).isNotEmptyDirectory();

    final Path targetDir = Files.createDirectory(tempDir.resolve("target"));
    assumeThat(targetDir).isEmptyDirectory();

    return new DynamicTest[]{
        dynamicTest("Check method succeeds", () ->
            assertThatCode(() -> Build.copyDirectory(sourceDir, targetDir))
                .doesNotThrowAnyException()
        ),
        dynamicTest("Check target directory has immediate file",
            () -> assertThat(targetDir.resolve("greeting.txt"))
                .hasContent("Hello, world!")
        ),
        dynamicTest("Check target directory has nested file",
            () -> assertThat(targetDir.resolve("nested").resolve("greeting.txt"))
                .hasContent("Hello, world!")
        ),
    };
  }

  @DisplayName("Check copying non-empty directory to existing non-empty directory works")
  @TestFactory
  DynamicTest[] copyingNonEmptyDirectoryToExistingNonEmptyDirWorks(
      @TempDir final Path tempDir
  ) throws IOException {
    final Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
    Files.writeString(sourceDir.resolve("greeting.txt"), "Hello, world!");
    final Path nestedDir = Files.createDirectory(sourceDir.resolve("nested"));
    Files.writeString(nestedDir.resolve("greeting.txt"), "Hello, world!");
    assumeThat(sourceDir).isNotEmptyDirectory();

    final Path targetDir = Files.createDirectory(tempDir.resolve("target"));
    Files.writeString(targetDir.resolve("existing.txt"), "Hello, world");
    final Path someDir = Files.createDirectory(targetDir.resolve("some-dir"));
    Files.writeString(someDir.resolve("existing.txt"), "Hello, world");
    assumeThat(targetDir.resolve("existing.txt")).isNotEmptyFile();
    assumeThat(targetDir.resolve("some-dir").resolve("existing.txt")).isNotEmptyFile();

    return new DynamicTest[]{
        dynamicTest("Check method succeeds", () ->
            assertThatCode(() -> Build.copyDirectory(sourceDir, targetDir))
                .doesNotThrowAnyException()
        ),
        dynamicTest("Check target directory has immediate file",
            () -> assertThat(targetDir.resolve("greeting.txt"))
                .hasContent("Hello, world!")
        ),
        dynamicTest("Check target directory has nested file",
            () -> assertThat(targetDir.resolve("nested").resolve("greeting.txt"))
                .hasContent("Hello, world!")
        ),
        dynamicTest("Check immediate old file does not exist",
            () -> assertThat(targetDir.resolve("existing.txt"))
                .doesNotExist()
        ),
        dynamicTest("Check nested old file does not exist",
            () -> assertThat(targetDir.resolve("some-dir").resolve("existing.txt"))
                .doesNotExist()
        ),
    };
  }

  private static void setupHelloWorld(final Path tempDir) {
    final String text;
    try (final var is = SimpleBuildTest.class.getResourceAsStream("/HelloWorld.java")) {
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
