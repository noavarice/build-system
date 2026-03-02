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

  // TODO: either pass all required args or provide all one-by-one
  public static Builder builder(final String groupIdStr, final String artifactIdStr) {
    final var groupId = new GroupId(groupIdStr);
    final var artifactId = new ArtifactId(artifactIdStr);
    return new Builder(groupId, artifactId);
  }

  private final GroupId groupId;

  private final ArtifactId artifactId;

  private final Path path;

  private final Map<SourceSet.Id, SourceSet> sourceSets;

  private final SourceSet mainSourceSet;

  private final SourceSet testSourceSet;

  private final ArtifactLayout artifactLayout;

  private Project(
      final GroupId groupId,
      final ArtifactId artifactId,
      final Path path,
      final Map<SourceSet.Id, SourceSet> sourceSets,
      final ArtifactLayout artifactLayout
  ) {
    this.groupId = Objects.requireNonNull(groupId);
    this.artifactId = Objects.requireNonNull(artifactId);
    this.path = Objects.requireNonNull(path);

    Objects.requireNonNull(sourceSets);
    if (!sourceSets.containsKey(SourceSet.Id.MAIN)) {
      throw new IllegalArgumentException("Project must have main source set");
    }

    this.sourceSets = Map.copyOf(sourceSets);
    this.mainSourceSet = Objects.requireNonNull(sourceSets.get(SourceSet.Id.MAIN));
    this.testSourceSet = Objects.requireNonNull(sourceSets.get(SourceSet.Id.TEST));
    this.artifactLayout = Objects.requireNonNull(artifactLayout);
  }

  public SourceSet sourceSet(final SourceSet.Id id) {
    return Objects.requireNonNull(sourceSets.get(id));
  }

  public SourceSet mainSourceSet() {
    return mainSourceSet;
  }

  public SourceSet testSourceSet() {
    return testSourceSet;
  }

  public GroupId groupId() {
    return groupId;
  }

  public ArtifactId artifactId() {
    return artifactId;
  }

  public Path path() {
    return path;
  }

  public ArtifactLayout artifactLayout() {
    return artifactLayout;
  }

  @Override
  public String toString() {
    return "Project[" + groupId + ':' + artifactId + ']';
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

    private final GroupId groupId;

    private final ArtifactId artifactId;

    private Path path = Path.of("");

    private final Map<SourceSet.Id, SourceSet> sourceSets = new HashMap<>();

    private ArtifactLayout artifactLayout = ArtifactLayout.DEFAULT;

    public Builder(final GroupId groupId, final ArtifactId artifactId) {
      this.groupId = Objects.requireNonNull(groupId);
      this.artifactId = Objects.requireNonNull(artifactId);
    }

    public Builder withPath(final String path) {
      return withPath(Path.of(path));
    }

    public Builder withPath(final Path path) {
      Objects.requireNonNull(path);
      PathUtils.checkRelative(path);
      this.path = path.normalize();
      return this;
    }

    public Builder withSourceSet(final SourceSet sourceSet) {
      Objects.requireNonNull(sourceSet);
      sourceSets.put(sourceSet.id(), sourceSet);
      return this;
    }

    public Builder withArtifactLayout(final ArtifactLayout artifactLayout) {
      Objects.requireNonNull(artifactLayout);
      this.artifactLayout = artifactLayout;
      return this;
    }

    public Project build() {
      return new Project(
          groupId,
          artifactId,
          path,
          sourceSets,
          artifactLayout
      );
    }
  }
}
