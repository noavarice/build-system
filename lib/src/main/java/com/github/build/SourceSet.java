package com.github.build;

import com.github.build.util.PathUtils;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author noavarice
 * @since 1.0.0
 */
public record SourceSet(
    Id id,
    List<Path> sourceDirectories,
    Type type,
    Set<Dependency> dependencies
) {

  public SourceSet {
    Objects.requireNonNull(id);
    sourceDirectories = sourceDirectories
        .stream()
        .peek(Objects::requireNonNull)
        .peek(PathUtils::checkRelative)
        .map(Path::normalize)
        .toList();
    if (sourceDirectories.isEmpty()) {
      throw new IllegalArgumentException("Source set must have at least one sources directory");
    }

    Objects.requireNonNull(type);
  }

  public record Id(String value) {

    public Id {
      value = Objects.requireNonNull(value).strip();
      if (value.isBlank()) {
        throw new IllegalArgumentException();
      }
    }

    @Override
    public String toString() {
      return value;
    }
  }

  public enum Type {
    PROD,
    TEST,
    DEV,
  }
}
