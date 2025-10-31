package com.github.build.util;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.reverseOrder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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
   * Deletes specified directory and all of its content recursively.
   *
   * @param directory Path to directory
   */
  public static void deleteDirectory(final Path directory) {
    log.info("Removing directory {}", directory);
    Objects.requireNonNull(directory);
    if (!Files.isDirectory(directory)) {
      log.info("{} is not a directory (it's a file or it does not exist)", directory);
      return;
    }

    final List<Path> paths = FileUtils.listAll(directory, reverseOrder());
    log.debug("Files and directories to remove, in order: {}", paths);
    for (final Path path : paths) {
      log.trace("Removing {}", path);
      try {
        Files.delete(path);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
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
