package com.github.build;

import com.github.build.util.PathUtils;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Project to build.
 *
 * @author noavarice
 * @since 1.0.0
 */
public final class Project {

  private final Id id;

  private final Path path;

  private final Map<SourceSet.Id, SourceSet> sourceSets;

  private final ArtifactLayout artifactLayout;


  public static Builder withId(final String idStr) {
    final var id = new Id(idStr);
    return new Builder(id);
  }

  private Project(
      final Id id,
      final Path path,
      final Map<SourceSet.Id, SourceSetArgs> sourceSets,
      final ArtifactLayout artifactLayout
  ) {
    this.id = id;
    this.path = path;
    checkSingleProdSourceSet(sourceSets);
    this.sourceSets = createSourceSets(sourceSets);
    this.artifactLayout = Objects.requireNonNull(artifactLayout);
  }

  private Map<SourceSet.Id, SourceSet> createSourceSets(
      final Map<SourceSet.Id, SourceSetArgs> allArgs
  ) {
    final var result = new HashMap<SourceSet.Id, SourceSet>();
    for (final SourceSet.Id id : allArgs.keySet()) {
      final SourceSetArgs args = allArgs.get(id);
      final var sourceSet = new SourceSet(
          this,
          id,
          args.path(),
          args.sourceDirectories(),
          args.resourceDirectories(),
          args.type(),
          args.dependencies()
      );
      result.put(id, sourceSet);
    }

    return Map.copyOf(result);
  }

  private static void checkSingleProdSourceSet(final Map<SourceSet.Id, SourceSetArgs> sourceSets) {
    final long prodSetsCount = sourceSets.values()
        .stream()
        .filter(sourceSet -> sourceSet.type() == SourceSetArgs.Type.PROD)
        .count();
    if (prodSetsCount == 0) {
      throw new IllegalArgumentException("No production source sets specified");
    }
    if (prodSetsCount > 1) {
      throw new IllegalArgumentException("Project can have only one production source set");
    }
  }

  public SourceSet mainSourceSet() {
    return sourceSets.values()
        .stream()
        .filter(sourceSet -> sourceSet.type() == SourceSetArgs.Type.PROD)
        .findFirst()
        .orElseThrow();
  }

  public Id id() {
    return id;
  }

  public Path path() {
    return path;
  }

  public Map<SourceSet.Id, SourceSet> sourceSets() {
    return sourceSets;
  }

  public ArtifactLayout artifactLayout() {
    return artifactLayout;
  }

  @Override
  public String toString() {
    return "Project[id=" + id + ']';
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

    private final Map<SourceSet.Id, SourceSetArgs> sourceSetArgs = new HashMap<>();

    public Builder(final Id id) {
      this.id = id;
    }

    public Builder withPath(final Path path) {
      Objects.requireNonNull(path);
      PathUtils.checkRelative(path);
      this.path = path.normalize();
      return this;
    }

    public Builder withSourceSet(final SourceSetArgs args) {
      Objects.requireNonNull(args);
      sourceSetArgs.put(args.id(), args);
      return this;
    }

    public Project build() {
      return new Project(
          id,
          path,
          sourceSetArgs,
          ArtifactLayout.DEFAULT
      );
    }
  }
}
