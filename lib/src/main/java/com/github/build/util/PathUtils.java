package com.github.build.util;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author noavarice
 * @since 1.0.0
 */
public final class PathUtils {

  private PathUtils() {
  }

  public static void checkRelative(final Path path) {
    if (path.isAbsolute()) {
      throw new IllegalArgumentException("Must be a relative path");
    }
  }

  public static void checkAbsolute(final Path path) {
    if (!path.isAbsolute()) {
      throw new IllegalArgumentException("Must be an absolute path");
    }
  }

  public static void checkDirectory(final Path path) {
    if (!Files.isDirectory(path)) {
      throw new IllegalArgumentException(path + " does not exist or is not a directory");
    }
  }
}
