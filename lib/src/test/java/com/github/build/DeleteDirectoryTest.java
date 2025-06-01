package com.github.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for deleting directory with content.
 *
 * @author noavarice
 * @since 1.0.0
 */
@DisplayName("Tests for deleting directory with content")
class DeleteDirectoryTest {

  @DisplayName("Check deleting non-existent directory works")
  @Test
  void testDeletingNonExistentDirectoryWorks(@TempDir final Path path) {
    final Path nonExistentDirectory = path.resolve("does-not-exist");
    assumeThat(nonExistentDirectory).doesNotExist();
    assertThatCode(() -> Build.deleteDirectory(nonExistentDirectory)).doesNotThrowAnyException();
  }

  @DisplayName("Check deleting path that is file works without deleting the file")
  @TestFactory
  DynamicTest[] testDeletingFileWorksWithoutDeletingFile(
      @TempDir final Path path
  ) throws IOException {
    final Path filePath = Files.writeString(path.resolve("greeting.txt"), "Hello, world!");
    return new DynamicTest[]{
        dynamicTest("Check method works without exception",
            () -> assertThatCode(() -> Build.deleteDirectory(filePath)).doesNotThrowAnyException()
        ),
        dynamicTest("Check file hasn't been touched",
            () -> assertThat(filePath).hasContent("Hello, world!")
        ),
    };
  }

  @DisplayName("Check deleting empty directory works")
  @TestFactory
  DynamicTest[] testDeletingEmptyDirectoryWorks(@TempDir final Path path) throws IOException {
    final Path emptyDir = Files.createDirectory(path.resolve("empty-directory"));
    assumeThat(emptyDir).isEmptyDirectory();
    return new DynamicTest[]{
        dynamicTest("Check method works without exception",
            () -> assertThatCode(() -> Build.deleteDirectory(emptyDir)).doesNotThrowAnyException()
        ),
        dynamicTest("Check directory removed", () -> assertThat(emptyDir).doesNotExist()),
    };
  }

  @DisplayName("Check deleting non-empty directory works")
  @TestFactory
  DynamicTest[] testDeletingNonEmptyDirectoryWorks(@TempDir final Path path) throws IOException {
    final Path dir = Files.createDirectory(path.resolve("non-empty-directory"));
    Files.writeString(dir.resolve("greeting.txt"), "Hello, world!");
    assumeThat(dir).isNotEmptyDirectory();
    return new DynamicTest[]{
        dynamicTest("Check method works without exception",
            () -> assertThatCode(() -> Build.deleteDirectory(dir)).doesNotThrowAnyException()
        ),
        dynamicTest("Check directory removed", () -> assertThat(dir).doesNotExist()),
    };
  }
}
