package com.github.build;

import com.github.build.deps.Dependency;
import com.github.build.deps.GroupArtifact;
import com.github.build.deps.GroupArtifactVersion;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Project to build.
 *
 * @author noavarice
 * @since 1.0.0
 */
public final class Project implements MainSourceSetDependency, TestSourceSetDependency,
    ExtraSourceSetDependency {

  private final String groupId;

  private final String artifactId;

  private final String version;

  private final Path path;

  private final Map<SourceSet.Id, SourceSet> sourceSets;

  private final SourceSet mainSourceSet;

  private final SourceSet testSourceSet;

  private final ArtifactLayout artifactLayout;

  // TODO: Think about making this private in favor of ProjectService
  public Project(
      final String groupId,
      final String artifactId,
      final String version,
      final Path path,
      final MainSourceSetArgs mainSourceSetArgs,
      final List<TestSourceSetArgs> testSourceSetArgsList,
      final ArtifactLayout artifactLayout
  ) {
    Objects.requireNonNull(groupId);
    if (groupId.isBlank()) {
      throw new IllegalArgumentException();
    }
    this.groupId = groupId.strip();

    Objects.requireNonNull(artifactId);
    if (artifactId.isBlank()) {
      throw new IllegalArgumentException();
    }
    this.artifactId = artifactId.strip();

    Objects.requireNonNull(version);
    if (version.isBlank()) {
      throw new IllegalArgumentException();
    }
    this.version = version.strip();

    this.path = Objects.requireNonNull(path);
    this.mainSourceSet = mapToSourceSet(mainSourceSetArgs);

    final var sourceSetMap = new HashMap<SourceSet.Id, SourceSet>();
    sourceSetMap.put(mainSourceSet.id(), mainSourceSet);

    for (final TestSourceSetArgs testSourceSetArgs : testSourceSetArgsList) {
      createExtraSourceSets(testSourceSetArgs, sourceSetMap);
      final SourceSet testSourceSet = mapToSourceSet(testSourceSetArgs, sourceSetMap);
      sourceSetMap.put(testSourceSet.id(), testSourceSet);
    }

    this.sourceSets = Map.copyOf(sourceSetMap);

    this.testSourceSet = Objects.requireNonNull(sourceSets.get(SourceSet.Id.TEST));
    this.artifactLayout = Objects.requireNonNull(artifactLayout);
  }

  private SourceSet mapToSourceSet(final MainSourceSetArgs args) {
    final var compileDependencies = args.compileDependencies()
        .stream()
        .map(Project::mapDependency)
        .toList();
    final var runtimeDependencies = args.runtimeDependencies()
        .stream()
        .map(Project::mapDependency)
        .toList();
    return new SourceSet(
        this,
        new SourceSet.Id(args.id()),
        args.sourceDirectories(),
        args.resourceDirectories(),
        compileDependencies,
        runtimeDependencies,
        List.of(),
        args.dependencyConstraints()
    );
  }

  private static Dependency mapDependency(final MainSourceSetDependency dependency) {
    return switch (dependency) {
      case Project project -> new Dependency.OnProject(project);
      case GroupArtifact ga -> new Dependency.Remote.WithoutVersion(ga);
      case GroupArtifactVersion gav -> new Dependency.Remote.WithVersion(gav);
      case LocalJar args -> new Dependency.Jar(args.path());
    };
  }

  private void createExtraSourceSets(
      final TestSourceSetArgs testSourceSetArgs,
      final Map<SourceSet.Id, SourceSet> sourceSets
  ) {
    for (final TestSourceSetDependency dependency : testSourceSetArgs.compileDependencies()) {
      if (dependency instanceof ExtraSourceSetArgs extraSourceSetArgs) {
        final SourceSet extraSourceSet = mapToSourceSet(extraSourceSetArgs, sourceSets);
        sourceSets.put(extraSourceSet.id(), extraSourceSet);
      }
    }

    for (final TestSourceSetDependency dependency : testSourceSetArgs.runtimeDependencies()) {
      if (dependency instanceof ExtraSourceSetArgs extraSourceSetArgs) {
        final SourceSet extraSourceSet = mapToSourceSet(extraSourceSetArgs, sourceSets);
        sourceSets.put(extraSourceSet.id(), extraSourceSet);
      }
    }
  }

  private SourceSet mapToSourceSet(
      final ExtraSourceSetArgs args,
      final Map<SourceSet.Id, SourceSet> sourceSets
  ) {
    final var compileDependencies = args.compileDependencies()
        .stream()
        .map(d -> mapDependency(d, sourceSets))
        .toList();
    final var runtimeDependencies = args.runtimeDependencies()
        .stream()
        .map(d -> mapDependency(d, sourceSets))
        .toList();
    return new SourceSet(
        this,
        new SourceSet.Id(args.id()),
        args.sourceDirectories(),
        args.resourceDirectories(),
        compileDependencies,
        runtimeDependencies,
        List.of(),
        args.dependencyConstraints()
    );
  }

  private static Dependency mapDependency(
      final ExtraSourceSetDependency dependency,
      final Map<SourceSet.Id, SourceSet> sourceSets
  ) {
    return switch (dependency) {
      case Project project -> new Dependency.OnProject(project);
      case GroupArtifact ga -> new Dependency.Remote.WithoutVersion(ga);
      case GroupArtifactVersion gav -> new Dependency.Remote.WithVersion(gav);
      case MainSourceSetArgs main -> {
        final var id = new SourceSet.Id(main.id());
        final SourceSet sourceSet = sourceSets.get(id);
        yield new Dependency.OnSourceSet(sourceSet);
      }
      case LocalJar args -> new Dependency.Jar(args.path());
    };
  }

  private SourceSet mapToSourceSet(
      final TestSourceSetArgs args,
      final Map<SourceSet.Id, SourceSet> sourceSets
  ) {
    final var compileDependencies = args.compileDependencies()
        .stream()
        .map(d -> mapDependency(d, sourceSets))
        .toList();
    final var runtimeDependencies = args.runtimeDependencies()
        .stream()
        .map(d -> mapDependency(d, sourceSets))
        .toList();
    return new SourceSet(
        this,
        new SourceSet.Id(args.id()),
        args.sourceDirectories(),
        args.resourceDirectories(),
        compileDependencies,
        runtimeDependencies,
        List.of(),
        args.dependencyConstraints()
    );
  }

  private static Dependency mapDependency(
      final TestSourceSetDependency dependency,
      final Map<SourceSet.Id, SourceSet> sourceSets
  ) {
    return switch (dependency) {
      case Project project -> new Dependency.OnProject(project);
      case GroupArtifact ga -> new Dependency.Remote.WithoutVersion(ga);
      case GroupArtifactVersion gav -> new Dependency.Remote.WithVersion(gav);
      case MainSourceSetArgs main -> {
        final var id = new SourceSet.Id(main.id());
        final SourceSet sourceSet = sourceSets.get(id);
        yield new Dependency.OnSourceSet(sourceSet);
      }
      case ExtraSourceSetArgs extra -> {
        final var id = new SourceSet.Id(extra.id());
        final SourceSet sourceSet = sourceSets.get(id);
        yield new Dependency.OnSourceSet(sourceSet);
      }
      case LocalJar args -> new Dependency.Jar(args.path());
    };
  }

  public Map<SourceSet.Id, SourceSet> sourceSets() {
    return sourceSets;
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

  public String groupId() {
    return groupId;
  }

  public String artifactId() {
    return artifactId;
  }

  public String version() {
    return version;
  }

  public GroupArtifactVersion gav() {
    return new GroupArtifactVersion(groupId, artifactId, version);
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
}
