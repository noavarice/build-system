package com.github.build;

import static java.util.stream.Collectors.toUnmodifiableSet;

import com.github.build.deps.DependencyConstraints;
import com.github.build.util.PathUtils;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Arguments for declaring test source set in a project.
 *
 * @author noavarice
 * @since 1.0.0
 */
public record TestSourceSetArgs(
    String id,
    Set<Path> sourceDirectories,
    Set<Path> resourceDirectories,
    List<TestSourceSetDependency> compileDependencies,
    List<TestSourceSetDependency> runtimeDependencies,
    DependencyConstraints dependencyConstraints
) {

  public static TestSourceSetArgs withTestDefaults() {
    return new TestSourceSetArgs(
        SourceSet.Id.TEST.toString(),
        Set.of(Path.of("src", "test", "java")),
        Set.of(Path.of("src", "test", "resources")),
        List.of(),
        List.of(),
        DependencyConstraints.EMPTY
    );
  }

  public TestSourceSetArgs {
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
