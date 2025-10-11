package com.github.build;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Project to build.
 *
 * @param id         Identifies project, so you don't need to mess with physical project location on
 *                   disk.
 * @param path       Path to a physical project files location on disk, relative to a working
 *                   directory
 * @param sourceSets Unmodifiable set of source sets, never null but can be empty
 * @author noavarice
 * @since 1.0.0
 */
public record Project(Id id, Path path, Set<SourceSet> sourceSets, ArtifactLayout artifactLayout) {

  public static Builder withId(final String idStr) {
    final var id = new Project.Id(idStr);
    return new Builder(id);
  }

  public Project {
    Objects.requireNonNull(id);
    path = Objects.requireNonNull(path).normalize();
    if (path.isAbsolute()) {
      throw new IllegalArgumentException("Must be a relative path");
    }

    sourceSets = Set.copyOf(sourceSets);
    checkSingleProdSourceSet(sourceSets);

    Objects.requireNonNull(artifactLayout);
  }

  private static void checkSingleProdSourceSet(final Set<SourceSet> sourceSets) {
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

  public record Id(String value) {

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

  /**
   * Defines a directory structure for storing various build artifacts (class files, JARs, etc.).
   * <p>
   * The purpose of building software is creating various artifacts. Speaking about Java
   * applications, these artifacts include generated sources and class files, resources, JAR files
   * and so on. Obviously, we need to store these artifacts somewhere. This class defines a list of
   * paths for storing artifacts, relative to a project location.
   *
   * @param rootDir      Path to a root directory for storing artifacts, relative to a project
   *                     location
   * @param classesDir   Path to a directory for storing class files, relative to a
   *                     {@link #rootDir}
   * @param resourcesDir Path to a directory for storing resources, relative to a {@link #rootDir}
   */
  public record ArtifactLayout(Path rootDir, Path classesDir, Path resourcesDir) {

    public static ArtifactLayout DEFAULT = new ArtifactLayout(
        Path.of("build"),
        Path.of("classes"),
        Path.of("resources")
    );

    public ArtifactLayout {
      Objects.requireNonNull(rootDir);
      if (rootDir.isAbsolute()) {
        throw new IllegalArgumentException("Must be a relative path");
      }
      rootDir = rootDir.normalize();

      Objects.requireNonNull(classesDir);
      if (classesDir.isAbsolute()) {
        throw new IllegalArgumentException("Must be a relative path");
      }
      classesDir = classesDir.normalize();
    }
  }

  public static final class Builder {

    private final Id id;

    private Path path = Path.of("");

    private final Set<SourceSet> sourceSets = new HashSet<>();

    private ArtifactLayout artifactLayout = ArtifactLayout.DEFAULT;

    public Builder(final Project.Id id) {
      this.id = id;
    }

    public Builder withPath(final Path path) {
      Objects.requireNonNull(path);
      if (path.isAbsolute()) {
        throw new IllegalArgumentException();
      }

      this.path = path.normalize();
      return this;
    }

    public Builder withSourceSet(final SourceSet sourceSet) {
      Objects.requireNonNull(sourceSet);
      sourceSets.add(sourceSet);
      return this;
    }

    public Project build() {
      return new Project(
          id,
          path,
          sourceSets,
          artifactLayout
      );
    }
  }
}
