package com.github.build;

import static java.util.stream.Collectors.toUnmodifiableSet;

import com.github.build.deps.Dependency;
import com.github.build.deps.DependencyConstraints;
import com.github.build.util.PathUtils;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Source set is a combination of source files and their dependencies.
 *
 * @param sourceDirectories     Directories containing source files, relative to project directory
 * @param resourceDirectories   Directories containing resources, relative to project directories
 * @param compileDependencies   Dependencies to compile code with. Dependencies do not include
 *                              transitive dependencies
 * @param runtimeDependencies   Dependencies to run code with. Dependencies do not include
 *                              transitive dependencies
 * @param exposedClasspath      Dependencies to expose as project API to compile and runtime
 *                              classpath to consuming projects. Every dependency here must be a
 *                              part of both compile and runtime classpath of a current project.
 *                              Dependencies do not include transitive dependencies
 * @param dependencyConstraints Constraints to use when resolving dependencies without exact
 *                              versions
 * @author noavarice
 * @since 1.0.0
 */
public record SourceSet(
    Project project,
    Id id,
    Set<Path> sourceDirectories,
    Set<Path> resourceDirectories,
    // dependency declaration order matters
    List<Dependency> compileDependencies,
    List<Dependency> runtimeDependencies,
    List<Dependency> exposedClasspath,
    DependencyConstraints dependencyConstraints
) {

  public SourceSet {
    Objects.requireNonNull(project);
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
    exposedClasspath = List.copyOf(exposedClasspath);
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
