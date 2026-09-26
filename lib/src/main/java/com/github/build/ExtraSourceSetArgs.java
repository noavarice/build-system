package com.github.build;

import static java.util.stream.Collectors.toUnmodifiableSet;

import com.github.build.deps.DependencyConstraints;
import com.github.build.util.PathUtils;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Arguments for declaring extra source set in a project.
 *
 * @author noavarice
 * @since 1.0.0
 */
public record ExtraSourceSetArgs(
    String id,
    Set<Path> sourceDirectories,
    Set<Path> resourceDirectories,
    List<ExtraSourceSetDependency> compileDependencies,
    List<ExtraSourceSetDependency> runtimeDependencies,
    DependencyConstraints dependencyConstraints
) implements TestSourceSetDependency {

  public ExtraSourceSetArgs {
    Objects.requireNonNull(id);
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

    compileDependencies = List.copyOf(compileDependencies);
    runtimeDependencies = List.copyOf(runtimeDependencies);
    Objects.requireNonNull(dependencyConstraints);
  }
}
