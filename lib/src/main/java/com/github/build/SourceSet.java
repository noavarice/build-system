package com.github.build;

import static java.util.stream.Collectors.toUnmodifiableSet;

import com.github.build.deps.Dependency;
import com.github.build.deps.DependencyConstraints;
import com.github.build.deps.GroupArtifact;
import com.github.build.deps.GroupArtifactVersion;
import com.github.build.util.PathUtils;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Source set is a combination of source files and their dependencies.
 *
 * @param sourceDirectories     Directories containing source files, relative to project directory
 * @param resourceDirectories   Directories containing resources, relative to project directories
 * @param compileClasspath      Source set compile classpath
 * @param dependencyConstraints Constraints to use when resolving dependencies without exact
 *                              versions
 * @author noavarice
 * @since 1.0.0
 */
public record SourceSet(
    Id id,
    Set<Path> sourceDirectories,
    Set<Path> resourceDirectories,
    Set<Dependency> compileClasspath,
    DependencyConstraints dependencyConstraints
) {

  public static Builder builder(final Id id) {
    return new Builder(id);
  }

  public static Builder withMainDefaults() {
    return new Builder(Id.MAIN)
        .withSourceDir(Path.of("src").resolve("main").resolve("java"))
        .withResourceDir(Path.of("src").resolve("main").resolve("resources"));
  }

  public static Builder withTestDefaults() {
    return new Builder(Id.TEST)
        .withSourceDir(Path.of("src").resolve("test").resolve("java"))
        .withResourceDir(Path.of("src").resolve("test").resolve("resources"));
  }

  public SourceSet {
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

    compileClasspath = Set.copyOf(compileClasspath);
  }

  public static final class Builder {

    private final Id id;

    private final Set<Path> sourceDirectories = new HashSet<>();

    private final Set<Path> resourceDirectories = new HashSet<>();

    private final Set<Dependency> compileClasspath = new HashSet<>();

    private DependencyConstraints dependencyConstraints = DependencyConstraints.EMPTY;

    private Builder(final Id id) {
      this.id = Objects.requireNonNull(id);
    }

    public Builder withSourceDir(final Path directory) {
      Objects.requireNonNull(directory);
      PathUtils.checkRelative(directory);
      sourceDirectories.add(directory.normalize());
      return this;
    }

    public Builder withResourceDir(final String resourceDir) {
      Objects.requireNonNull(resourceDir);
      return withResourceDir(Path.of(resourceDir));
    }

    public Builder withResourceDir(final Path resourceDir) {
      Objects.requireNonNull(resourceDir);
      PathUtils.checkRelative(resourceDir);
      resourceDirectories.add(resourceDir.normalize());
      return this;
    }

    public Builder compileWith(final SourceSet sourceSet) {
      compileClasspath.add(new Dependency.OnSourceSet(sourceSet));
      return this;
    }

    public Builder compileWith(final Project project) {
      compileClasspath.add(new Dependency.OnProject(project));
      return this;
    }

    public Builder compileWithLocalJar(final Path jarPath) {
      compileClasspath.add(new Dependency.Jar(jarPath));
      return this;
    }

    public Builder compileWith(final GroupArtifactVersion gav) {
      compileClasspath.add(new Dependency.Remote.WithVersion(gav));
      return this;
    }

    public Builder compileWith(final GroupArtifact ga) {
      compileClasspath.add(new Dependency.Remote.WithoutVersion(ga));
      return this;
    }

    public Builder withDependencyConstraints(final DependencyConstraints dependencyConstraints) {
      this.dependencyConstraints = Objects.requireNonNull(dependencyConstraints);
      return this;
    }

    public SourceSet build() {
      return new SourceSet(
          id,
          sourceDirectories,
          resourceDirectories,
          compileClasspath,
          dependencyConstraints
      );
    }
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
