package com.github.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.github.build.compile.CompileService;
import com.github.build.deps.DependencyService;
import com.github.build.deps.LocalRepository;
import com.github.build.deps.RemoteRepository;
import com.github.build.util.FileUtils;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

/**
 * Tests for copying files (e.g. resources).
 *
 * @author noavarice
 * @since 1.0.0
 */
@DisplayName("Tests for copying files (e.g. resources)")
class CopyTest {

  private final BuildService buildService;

  CopyTest(@TempDir final Path localRepoBase) {
    final CompileService compileService = new CompileService();
    final var remoteRepository = new RemoteRepository(
        // TODO: externalize
        URI.create("http://localhost:8081/repository/maven-central"),
        HttpClient.newHttpClient(),
        new ObjectMapper()
    );
    final var localRepository = new LocalRepository(
        localRepoBase,
        Map.of("sha256", "SHA-256")
    );
    final DependencyService dependencyService = new DependencyService(
        List.of(remoteRepository),
        localRepository
    );
    buildService = new BuildService(compileService, dependencyService);
  }

  @DisplayName("Tests for copying directory")
  @Nested
  class CopyDirectory {

    @DisplayName("Check copying empty directory to non-existent directory works")
    @TestFactory
    DynamicTest[] copyingEmptyDirectoryWorks(@TempDir final Path tempDir) throws IOException {
      final Path emptyDir = Files.createDirectory(tempDir.resolve("empty"));
      assumeThat(emptyDir).isEmptyDirectory();

      final Path targetDir = tempDir.resolve("target");
      assumeThat(targetDir).doesNotExist();

      return new DynamicTest[]{
          dynamicTest("Check method succeeds", () ->
              assertThatCode(() -> FileUtils.copyDirectory(emptyDir, targetDir))
                  .doesNotThrowAnyException()
          ),
          dynamicTest("Check target directory exists",
              () -> assertThat(targetDir).isEmptyDirectory()),
      };
    }

    @DisplayName("Check copying empty directory to existing empty directory works")
    @TestFactory
    DynamicTest[] copyingEmptyDirectoryToEmptyExistingDirWorks(
        @TempDir final Path tempDir
    ) throws IOException {
      final Path emptyDir = Files.createDirectory(tempDir.resolve("empty"));
      assumeThat(emptyDir).isEmptyDirectory();

      final Path targetDir = Files.createDirectory(tempDir.resolve("target"));
      assumeThat(targetDir).isEmptyDirectory();

      return new DynamicTest[]{
          dynamicTest("Check method succeeds", () ->
              assertThatCode(() -> FileUtils.copyDirectory(emptyDir, targetDir))
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
              assertThatCode(() -> FileUtils.copyDirectory(sourceDir, targetDir))
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
    DynamicTest[] copyingNonEmptyDirectoryToEmptyExistingDirWorks(
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
              assertThatCode(() -> FileUtils.copyDirectory(sourceDir, targetDir))
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

    @DisplayName("Check copying empty directory to existing non-empty directory works")
    @TestFactory
    DynamicTest[] copyingEmptyDirectoryToExistingNonEmptyDirWorks(
        @TempDir final Path tempDir
    ) throws IOException {
      final Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
      assumeThat(sourceDir).isEmptyDirectory();

      final Path targetDir = Files.createDirectory(tempDir.resolve("target"));
      Files.writeString(targetDir.resolve("existing.txt"), "Hello, world");
      final Path someDir = Files.createDirectory(targetDir.resolve("some-dir"));
      Files.writeString(someDir.resolve("existing.txt"), "Hello, world");
      assumeThat(targetDir.resolve("existing.txt")).isNotEmptyFile();
      assumeThat(targetDir.resolve("some-dir").resolve("existing.txt")).isNotEmptyFile();

      return new DynamicTest[]{
          dynamicTest("Check method succeeds", () ->
              assertThatCode(() -> FileUtils.copyDirectory(sourceDir, targetDir))
                  .doesNotThrowAnyException()
          ),
          dynamicTest("Check immediate old file not removed",
              () -> assertThat(targetDir.resolve("existing.txt")).exists()
          ),
          dynamicTest("Check nested old file not removed",
              () -> assertThat(targetDir.resolve("some-dir").resolve("existing.txt")).exists()
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
      final Path someDir = Files.createDirectory(targetDir.resolve("nested"));
      Files.writeString(someDir.resolve("existing.txt"), "Hello, world");
      assumeThat(targetDir.resolve("existing.txt")).isNotEmptyFile();
      assumeThat(targetDir.resolve("nested").resolve("existing.txt")).isNotEmptyFile();

      return new DynamicTest[]{
          dynamicTest("Check method succeeds", () ->
              assertThatCode(() -> FileUtils.copyDirectory(sourceDir, targetDir))
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
          dynamicTest("Check immediate old file not removed",
              () -> assertThat(targetDir.resolve("existing.txt")).exists()
          ),
          dynamicTest("Check nested old file not removed",
              () -> assertThat(targetDir.resolve("nested").resolve("existing.txt")).exists()
          ),
      };
    }

    @DisplayName("Check overriding existing file works")
    @TestFactory
    DynamicTest[] overridingExistingFileWorks(@TempDir final Path tempDir) throws IOException {
      final Path sourceDir = Files.createDirectory(tempDir.resolve("source"));
      Files.writeString(sourceDir.resolve("greeting.txt"), "Hello, new world!");
      final Path nestedDir = Files.createDirectory(sourceDir.resolve("nested"));
      Files.writeString(nestedDir.resolve("greeting.txt"), "Hello, new nested world!");
      assumeThat(sourceDir).isNotEmptyDirectory();

      final Path targetDir = Files.createDirectory(tempDir.resolve("target"));
      Files.writeString(targetDir.resolve("greeting.txt"), "Hello, world");
      final Path someDir = Files.createDirectory(targetDir.resolve("nested"));
      Files.writeString(someDir.resolve("greeting.txt"), "Hello, nested world");
      assumeThat(targetDir.resolve("greeting.txt")).isNotEmptyFile();
      assumeThat(targetDir.resolve("nested").resolve("greeting.txt")).isNotEmptyFile();

      return new DynamicTest[]{
          dynamicTest("Check method succeeds", () ->
              assertThatCode(() -> FileUtils.copyDirectory(sourceDir, targetDir))
                  .doesNotThrowAnyException()
          ),
          dynamicTest("Check target directory has immediate file",
              () -> assertThat(targetDir.resolve("greeting.txt"))
                  .hasContent("Hello, new world!")
          ),
          dynamicTest("Check target directory has nested file",
              () -> assertThat(targetDir.resolve("nested").resolve("greeting.txt"))
                  .hasContent("Hello, new nested world!")
          ),
      };
    }
  }

  @DisplayName("Tests for copying project resources")
  @Nested
  class CopyResources {

    @DisplayName("Check copying resources from non-existent directory works")
    @TestFactory
    DynamicTest[] testCopyResourcesFromNonExistentDirectoryWorks(@TempDir final Path tempDir) {
      final Project project = createProject();

      final Path resourcesDir = tempDir.resolve("src/main/resources");
      assumeThat(resourcesDir).doesNotExist();

      final Path targetDir = tempDir
          .resolve(project.path())
          .resolve(project.artifactLayout().rootDir())
          .resolve(project.artifactLayout().resourcesDir())
          .resolve(SourceSet.Id.MAIN.toString());
      assumeThat(targetDir).doesNotExist();

      return new DynamicTest[]{
          dynamicTest("Check method works without exception", () ->
              assertThatCode(
                  () -> buildService.copyResources(tempDir, project, SourceSet.Id.MAIN)
              ).doesNotThrowAnyException()
          ),
          dynamicTest("Check target directory exist",
              () -> assertThat(targetDir).isEmptyDirectory()
          ),
      };
    }

    @DisplayName("Check copying resources from empty directory works")
    @TestFactory
    DynamicTest[] testCopyResourcesFromEmptyDirectoryWorks(
        @TempDir final Path tempDir
    ) throws IOException {
      final Project project = createProject();
      final Path resourcesDir = Files.createDirectories(tempDir.resolve("src/main/resources"));
      assumeThat(resourcesDir).isEmptyDirectory();

      final Path targetDir = tempDir
          .resolve(project.path())
          .resolve(project.artifactLayout().rootDir())
          .resolve(project.artifactLayout().resourcesDir())
          .resolve(SourceSet.Id.MAIN.toString());
      assumeThat(targetDir).doesNotExist();

      return new DynamicTest[]{
          dynamicTest("Check method works without exception", () ->
              assertThatCode(
                  () -> buildService.copyResources(tempDir, project, SourceSet.Id.MAIN)
              ).doesNotThrowAnyException()
          ),
          dynamicTest("Check target directory exist",
              () -> assertThat(targetDir).isEmptyDirectory()
          ),
      };
    }

    @DisplayName("Check copying resources works")
    @TestFactory
    DynamicTest[] testCopyResourcesWorks(@TempDir final Path tempDir) {
      FsUtils.setupFromYaml("/projects/hello-world.yaml", tempDir);
      final Project project = Project
          .withId("hello-world")
          .withPath("hello-world")
          .withSourceSet(SourceSet.withMainDefaults().build())
          .withSourceSet(SourceSet.withTestDefaults().build())
          .build();

      final Path copiedResourcePath = tempDir.resolve(
          "hello-world/build/resources/main/greeting.txt"
      );
      assumeThat(copiedResourcePath).doesNotExist();

      final Path copiedNestedResourcePath = tempDir.resolve(
          "hello-world/build/resources/main/nested/greeting.txt"
      );
      assumeThat(copiedNestedResourcePath).doesNotExist();

      final Path otherCopiedResourcePath = tempDir.resolve(
          "hello-world/build/resources/main/other-greeting.txt"
      );
      assumeThat(copiedNestedResourcePath).doesNotExist();

      return new DynamicTest[]{
          dynamicTest("Check method works without exception", () ->
              assertThatCode(
                  () -> buildService.copyResources(tempDir, project, SourceSet.Id.MAIN)
              ).doesNotThrowAnyException()
          ),
          dynamicTest(
              "Check resource copied",
              () -> assertThat(copiedResourcePath)
                  .hasContent("Hello, world!")
          ),
          dynamicTest(
              "Check nested resource copied",
              () -> assertThat(copiedNestedResourcePath)
                  .hasContent("Hello, world from nested resource file!")
          ),
          dynamicTest(
              "Check resource from other directory is not copied",
              () -> assertThat(otherCopiedResourcePath)
                  .doesNotExist()
          ),
      };
    }

    @DisplayName("Check copying resources from multiple directories works")
    @TestFactory
    DynamicTest[] testCopyResourcesFromMultipleDirWorks(@TempDir final Path tempDir) {
      FsUtils.setupFromYaml("/projects/hello-world.yaml", tempDir);
      final var mainSourceSet = SourceSet
          .withMainDefaults()
          .withResourceDir("src/main/other-resources")
          .build();
      final var project = Project
          .withId("hello-world")
          .withPath("hello-world")
          .withSourceSet(mainSourceSet)
          .withSourceSet(SourceSet.withTestDefaults().build())
          .build();

      // setup paths for copied resources
      final Path copiedResourcePath = tempDir.resolve(
          "hello-world/build/resources/main/greeting.txt"
      );
      assumeThat(copiedResourcePath).doesNotExist();

      final Path copiedNestedResourcePath = tempDir.resolve(
          "hello-world/build/resources/main/nested/greeting.txt"
      );
      assumeThat(copiedNestedResourcePath).doesNotExist();

      final Path otherCopiedResourcePath = tempDir.resolve(
          "hello-world/build/resources/main/other-greeting.txt"
      );
      assumeThat(copiedNestedResourcePath).doesNotExist();

      return new DynamicTest[]{
          dynamicTest("Check method works without exception", () ->
              assertThatCode(
                  () -> buildService.copyResources(tempDir, project, SourceSet.Id.MAIN)
              ).doesNotThrowAnyException()
          ),
          dynamicTest(
              "Check resource copied",
              () -> assertThat(copiedResourcePath)
                  .hasContent("Hello, world!")
          ),
          dynamicTest(
              "Check nested resource copied",
              () -> assertThat(copiedNestedResourcePath)
                  .hasContent("Hello, world from nested resource file!")
          ),
          dynamicTest(
              "Check resource from other directory copied",
              () -> assertThat(otherCopiedResourcePath)
                  .hasContent("Hello, world from resource file from additional resource directory!")
          ),
      };
    }

    private static Project createProject() {
      final var mainSourceSet = SourceSet
          .withMainDefaults()
          .build();
      return Project
          .withId("test-project")
          .withSourceSet(mainSourceSet)
          .withSourceSet(SourceSet.withTestDefaults().build())
          .build();
    }
  }
}
