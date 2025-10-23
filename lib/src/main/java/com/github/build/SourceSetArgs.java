package com.github.build;

import static java.util.stream.Collectors.toUnmodifiableSet;

import com.github.build.deps.Dependency;
import com.github.build.util.PathUtils;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Arguments for creating {@link SourceSet} within {@link Project}.
 *
 * @param path                Optional path to source set content, relative to
 *                            {@link Project#path() project directory}
 * @param sourceDirectories   Directories containing source files
 * @param resourceDirectories Directories containing resources
 * @param type                Source set type (main, test, etc.)
 * @param dependencies        Source set dependencies (compile classpath, runtime classpath, etc.)
 * @author noavarice
 * @since 1.0.0
 */
public record SourceSetArgs(
    @Nullable Path path,
    Set<Path> sourceDirectories,
    Set<Path> resourceDirectories,
    Type type,
    Set<Dependency> dependencies
) {

  public static Builder builder() {
    return new Builder();
  }

  public SourceSetArgs {
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
  }

  public enum Type {
    PROD,
    TEST,
    DEV,
  }

  public static final class Builder {

    private final Set<Path> resourceDirectories = new HashSet<>();

    private Type type = Type.PROD;

    private final Set<Dependency> dependencies = new HashSet<>();

    private Builder() {
      resourceDirectories.add(Path.of("resources"));
    }

    public Builder withType(final Type type) {
      this.type = Objects.requireNonNull(type);
      return this;
    }

    public Builder withResourceDir(final String resourceDir) {
      return withResourceDir(Path.of(resourceDir));
    }

    public Builder withResourceDir(final Path resourceDir) {
      Objects.requireNonNull(resourceDir);
      PathUtils.checkRelative(resourceDir);
      this.resourceDirectories.add(resourceDir.normalize());
      return this;
    }

    public Builder withDependency(final Dependency dependency) {
      dependencies.add(dependency);
      return this;
    }

    public SourceSetArgs build() {
      return new SourceSetArgs(
          null,
          Set.of(Path.of("java")),
          resourceDirectories,
          type,
          dependencies
      );
    }
  }
}
