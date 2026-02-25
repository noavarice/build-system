package com.github.build.util;

import static java.util.Comparator.naturalOrder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author noavarice
 */
public final class FileUtils {

  private static final Logger log = LoggerFactory.getLogger(FileUtils.class);

  private FileUtils() {
  }

  /**
   * Copies contents of the source directory recursively to a target directory.
   *
   * @param sourceDir Path to directory to be copied
   * @param targetDir Path to directory to copy to
   */
  public static void copyDirectory(final Path sourceDir, final Path targetDir) {
    if (Files.isRegularFile(sourceDir)) {
      throw new IllegalArgumentException("Source path must not be a file");
    }

    if (Files.notExists(sourceDir)) {
      log.debug("{} does not exist, do nothing", sourceDir);
      return;
    }

    if (Files.isRegularFile(targetDir)) {
      throw new IllegalArgumentException("Target path must not be a file");
    }

    copyToNonExistentDirectory(sourceDir, targetDir);
  }

  private static void copyToNonExistentDirectory(final Path sourceDir, final Path targetDir) {
    final List<Path> content = listAll(sourceDir, naturalOrder());
    for (final Path path : content) {
      final Path relativePath = sourceDir.relativize(path);
      final Path pathInsideTarget = targetDir.resolve(relativePath);
      if (pathInsideTarget.equals(targetDir)) {
        if (Files.notExists(targetDir)) {
          try {
            Files.createDirectory(targetDir);
          } catch (final IOException e) {
            throw new UncheckedIOException(e);
          }
        }
        continue;
      }

      if (Files.isDirectory(path)) {
        if (Files.notExists(pathInsideTarget)) {
          try {
            Files.createDirectory(pathInsideTarget);
          } catch (final IOException e) {
            throw new UncheckedIOException(e);
          }
        }
      } else {
        try {
          log.debug("Copying {} to {}", path, pathInsideTarget);
          Files.copy(path, pathInsideTarget, StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }
  }

  /**
   * Deletes specified path. If path is non-empty directory, deletes directory with its content.
   *
   * @param path File path
   */
  public static void delete(final Path path) {
    log.info("Deleting {}", path);
    if (Files.notExists(path)) {
      log.debug("Path {} not found, do nothing", path);
      return;
    }

    if (Files.isRegularFile(path)) {
      try {
        Files.delete(path);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }

      return;
    }

    log.debug("Deleting directory {} recursively", path);
    try {
      Files.walkFileTree(path, DeletingFileVisitor.INSTANCE);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * File visitor for cleaning contents of a directory.
   */
  private static final class DeletingFileVisitor implements FileVisitor<Path> {

    private static final FileVisitor<Path> INSTANCE = new DeletingFileVisitor();

    private DeletingFileVisitor() {
    }

    @Override
    public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(
        final Path file,
        final BasicFileAttributes attrs
    ) throws IOException {
      log.trace("Deleting file {}", file);
      Files.delete(file);
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(final Path file, final IOException exc) {
      log.error("Failed to delete file {}", file, exc);
      return FileVisitResult.TERMINATE;
    }

    @Override
    public FileVisitResult postVisitDirectory(
        final Path dir,
        final IOException exc
    ) throws IOException {
      if (exc != null) {
        log.error("Failed to visit directory {}", dir, exc);
        return FileVisitResult.TERMINATE;
      }

      log.trace("Deleting directory {}", dir);
      Files.delete(dir);
      return FileVisitResult.CONTINUE;
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this || obj != null && obj.getClass() == this.getClass();
    }

    @Override
    public int hashCode() {
      return 1;
    }

    @Override
    public String toString() {
      return "CleaningFileVisitor[]";
    }

  }

  /**
   * Lists every file and directory in file tree inside specified directory.
   *
   * @param directory  Directory
   * @param comparator How to sort returned items
   * @return Non-null list of all directory contents
   */
  public static List<Path> listAll(final Path directory, final Comparator<Path> comparator) {
    try (final var stream = Files.walk(directory)) {
      return stream
          .sorted(comparator)
          .toList();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
