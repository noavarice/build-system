package com.github.build;

import com.github.build.util.PathUtils;
import java.nio.file.Path;
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

  private final SourceSet main;

  private final SourceSet test;

  private final ArtifactLayout artifactLayout;

  public static Builder withId(final String idStr) {
    final var id = new Id(idStr);
    return new Builder(id);
  }

  private Project(
      final Id id,
      final Path path,
      final SourceSetArgs main,
      final SourceSetArgs test,
      final ArtifactLayout artifactLayout
  ) {
    this.id = id;
    this.path = path;
    this.main = createSourceSet(SourceSet.Id.MAIN, main);
    this.test = createSourceSet(SourceSet.Id.TEST, test);
    this.artifactLayout = Objects.requireNonNull(artifactLayout);
  }

  private SourceSet createSourceSet(final SourceSet.Id id, final SourceSetArgs args) {
    // TODO: consider more flexible approach
    final Path path = Objects.requireNonNullElseGet(
        args.path(),
        () -> Path.of("src", id.value())
    );
    return new SourceSet(
        this,
        id,
        path,
        args.sourceDirectories(),
        args.resourceDirectories(),
        args.type(),
        args.dependencies(),
        args.dependencyConstraints()
    );
  }

  public SourceSet mainSourceSet() {
    return main;
  }

  public SourceSet testSourceSet() {
    return test;
  }

  public Id id() {
    return id;
  }

  public Path path() {
    return path;
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

    private SourceSetArgs main = SourceSetArgs
        .builder()
        .build();

    private SourceSetArgs test = SourceSetArgs
        .builder()
        .withType(SourceSetArgs.Type.TEST)
        .build();

    public Builder(final Id id) {
      this.id = id;
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

    public Builder withMainSourceSet(final SourceSetArgs args) {
      main = Objects.requireNonNull(args);
      return this;
    }

    public Builder withTestSourceSet(final SourceSetArgs args) {
      test = Objects.requireNonNull(args);
      return this;
    }

    public Project build() {
      return new Project(
          id,
          path,
          main,
          test,
          ArtifactLayout.DEFAULT
      );
    }
  }
}
