package com.github.build;

import static java.util.stream.Collectors.toUnmodifiableSet;

import com.github.build.deps.Dependency;
import com.github.build.deps.DependencyConstraints;
import com.github.build.util.PathUtils;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/**
 * @author noavarice
 */
public record SourceSet(
    Project project,
    Id id,
    Path path,
    Set<Path> sourceDirectories,
    Set<Path> resourceDirectories,
    SourceSetArgs.Type type,
    Set<Dependency> dependencies,
    DependencyConstraints dependencyConstraints
) {

  public SourceSet {
    Objects.requireNonNull(id);

    path = Objects.requireNonNull(path).normalize();
    PathUtils.checkRelative(path);

    sourceDirectories = sourceDirectories
        .stream()
        .peek(Objects::requireNonNull)
        .peek(PathUtils::checkRelative)
        .map(Path::normalize)
        .collect(toUnmodifiableSet());
    if (sourceDirectories.isEmpty()) {
      throw new IllegalArgumentException("Source set must have at least one sources directory");
    }

    resourceDirectories = resourceDirectories
        .stream()
        .peek(Objects::requireNonNull)
        .peek(PathUtils::checkRelative)
        .map(Path::normalize)
        .collect(toUnmodifiableSet());

    Objects.requireNonNull(type);
    dependencies = Set.copyOf(dependencies);
    Objects.requireNonNull(dependencyConstraints);
  }

  public record Id(String value) {

    public static final Id MAIN = new Id("main");

    public static final Id TEST = new Id("test");

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
}
