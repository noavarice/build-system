package com.github.build;

import com.github.build.util.PathUtils;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Describes relative file and directory structure for some {@link SourceSet}.
 */
public record SourceSetLayout(
    List<Path> sourceDirectories,
    List<Path> resourceDirectories
) {

  public static final SourceSetLayout DEFAULT = new SourceSetLayout(
      List.of(Path.of("java")),
      List.of(Path.of("resources"))
  );

  public SourceSetLayout {
    sourceDirectories = sourceDirectories
        .stream()
        .peek(Objects::requireNonNull)
        .peek(PathUtils::checkRelative)
        .map(Path::normalize)
        .toList();
    if (sourceDirectories.isEmpty()) {
      throw new IllegalArgumentException("Source set must have at least one sources directory");
    }

    resourceDirectories = resourceDirectories
        .stream()
        .peek(Objects::requireNonNull)
        .peek(PathUtils::checkRelative)
        .map(Path::normalize)
        .toList();
  }
}
