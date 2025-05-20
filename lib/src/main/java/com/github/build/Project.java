package com.github.build;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/**
 * Project to build.
 *
 * @param id         Identifies project, so you don't need to mess with physical project location on
 *                   disk.
 * @param sourceSets Unmodifiable set of source sets, never null but can be empty
 * @author noavarice
 * @since 1.0.0
 */
public record Project(
    Id id,
    Path path,
    Set<SourceSet> sourceSets
) implements Dependency {

  public Project {
    Objects.requireNonNull(id);
    Objects.requireNonNull(path);
    if (path.isAbsolute()) {
      throw new IllegalArgumentException("Must be a relative path");
    }

    sourceSets = Set.copyOf(sourceSets);
    checkSingleProdSourceSet(sourceSets);
  }

  private void checkSingleProdSourceSet(final Set<SourceSet> sourceSets) {
    final long prodSetsCount = sourceSets
        .stream()
        .filter(sourceSet -> sourceSet.type() == SourceSet.Type.PROD)
        .count();
    if (prodSetsCount == 0) {
      throw new IllegalArgumentException("No production source sets specified");
    }
    if (prodSetsCount > 1) {
      throw new IllegalArgumentException("Project can have only one production source set");
    }
  }

  public SourceSet mainSourceSet() {
    return sourceSets
        .stream()
        .filter(sourceSet -> sourceSet.type() == SourceSet.Type.PROD)
        .findFirst()
        .orElseThrow();
  }

  record Id(String value) {

    public Id {
      Objects.requireNonNull(value);
      if (value.isBlank()) {
        throw new IllegalArgumentException("Must not be empty");
      }

      value = value.strip();
    }

    @Override
    public String toString() {
      return value;
    }
  }
}
