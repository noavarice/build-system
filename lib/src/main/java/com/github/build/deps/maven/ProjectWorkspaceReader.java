package com.github.build.deps.maven;

import static com.github.build.deps.Dependency.Jar;
import static com.github.build.deps.Dependency.OnProject;
import static com.github.build.deps.Dependency.OnSourceSet;
import static com.github.build.deps.Dependency.Remote;
import static java.util.stream.Collectors.toUnmodifiableSet;

import com.github.build.Project;
import com.github.build.ProjectService;
import com.github.build.SourceSet;
import com.github.build.deps.GroupArtifact;
import com.github.build.deps.GroupArtifactVersion;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integrates {@link Project} into Maven Artifact Resolver resolution mechanism.
 *
 * @author noavarice
 * @since 1.0.0
 */
public final class ProjectWorkspaceReader implements WorkspaceReader {

  private static final Logger log = LoggerFactory.getLogger(ProjectWorkspaceReader.class);

  private final WorkspaceRepository repository;

  private final Path workdir;

  private final ProjectService projectService;

  public ProjectWorkspaceReader(
      final WorkspaceRepository repository,
      final Path workdir,
      final ProjectService projectService
  ) {
    this.repository = Objects.requireNonNull(repository);
    this.workdir = Objects.requireNonNull(workdir);
    this.projectService = projectService;
  }

  @Override
  public WorkspaceRepository getRepository() {
    return repository;
  }

  @Override
  @Nullable
  public File findArtifact(final Artifact artifact) {
    Objects.requireNonNull(artifact);
    final SourceSet sourceSet = findSourceSet(artifact);
    if (sourceSet == null) {
      return null;
    }

    return switch (artifact.getExtension()) {
      case "", "jar" -> jarPath(sourceSet).toFile();
      case "pom" -> generatePom(sourceSet);
      default -> null;
    };
  }

  private SourceSet findSourceSet(final Artifact artifact) {
    for (final Project project : projectService.allProjects()) {
      final SourceSet sourceSet = MavenArtifactResolverUtils.mapArtifactToSourceSet(
          artifact, project
      );
      if (sourceSet != null) {
        return sourceSet;
      }
    }

    return null;
  }

  // TODO: perhaps should be moved to SourceSet
  private Path jarPath(final SourceSet sourceSet) {
    final Project project = sourceSet.project();
    final String jarName = sourceSet.id().equals(SourceSet.Id.MAIN)
        ? project.artifactId() + ".jar"
        : project.artifactId() + "-" + sourceSet.id() + ".jar";
    return workdir
        .resolve(project.path())
        .resolve(project.artifactLayout().rootDir())
        .resolve(jarName);
  }

  /**
   * Maps the project main source set to a POM {@link Model}, serializes it into XML and saves it
   * under the build directory. Aether reads a single descriptor per project, so all source-set
   * artifacts of the project share this POM.
   */
  private File generatePom(final SourceSet sourceSet) {
    final Project project = sourceSet.project();
    final GroupArtifactVersion sourceSetGav = MavenArtifactResolverUtils.makeSourceSetGav(
        sourceSet);

    final var model = new Model();
    model.setModelVersion("4.0.0");
    model.setGroupId(sourceSetGav.groupId());
    model.setArtifactId(sourceSetGav.artifactId());
    model.setVersion(sourceSetGav.version());

    // setting dependency management
    {
      final var dependencyManagement = new DependencyManagement();
      sourceSet.dependencyConstraints()
          .stream()
          .map(gav -> {
            final var dependency = new Dependency();
            dependency.setGroupId(gav.groupId());
            dependency.setArtifactId(gav.artifactId());
            dependency.setVersion(gav.version());
            return dependency;
          })
          .forEach(dependencyManagement::addDependency);

      model.setDependencyManagement(dependencyManagement);
    }

    // setting dependencies
    {
      final Set<GroupArtifact> compileClasspathArtifacts = sourceSet
          .compileDependencies()
          .stream()
          .map(ProjectWorkspaceReader::mapDependencyToGa)
          .filter(Objects::nonNull)
          .collect(toUnmodifiableSet());
      final Set<GroupArtifact> runtimeClasspathArtifacts = sourceSet
          .runtimeDependencies()
          .stream()
          .map(ProjectWorkspaceReader::mapDependencyToGa)
          .filter(Objects::nonNull)
          .collect(toUnmodifiableSet());

      final List<Dependency> dependencies = new ArrayList<>(
          compileClasspathArtifacts.size() + runtimeClasspathArtifacts.size()
      );

      for (final var d : sourceSet.compileDependencies()) {
        switch (d) {
          case Remote.WithVersion withVersion -> {
            final var dependency = new Dependency();
            final GroupArtifactVersion gav = withVersion.gav();
            dependency.setGroupId(gav.groupId());
            dependency.setArtifactId(gav.artifactId());
            dependency.setVersion(gav.version());

            final GroupArtifact ga = gav.groupArtifact();
            if (runtimeClasspathArtifacts.contains(ga)) {
              dependency.setScope("compile");
            } else {
              dependency.setScope("provided");
            }

            // avoid extracting this part from switch - adding dependency
            // with null groupId/artifactId (in case of plain JAR) will
            // silently break dependency resolution
            dependencies.add(dependency);
          }

          case Remote.WithoutVersion withoutVersion -> {
            final var dependency = new Dependency();
            final GroupArtifact ga = withoutVersion.ga();
            dependency.setGroupId(ga.groupId());
            dependency.setArtifactId(ga.artifactId());
            // version should be complemented by dependency constraints

            if (runtimeClasspathArtifacts.contains(ga)) {
              dependency.setScope("compile");
            } else {
              dependency.setScope("provided");
            }

            dependencies.add(dependency);
          }

          case OnProject onProject -> {
            final var dependency = new Dependency();
            final GroupArtifactVersion gav = onProject.project().gav();
            dependency.setGroupId(gav.groupId());
            dependency.setArtifactId(gav.artifactId());
            dependency.setVersion(gav.version());

            final GroupArtifact ga = gav.groupArtifact();
            if (runtimeClasspathArtifacts.contains(ga)) {
              dependency.setScope("compile");
            } else {
              dependency.setScope("provided");
            }

            dependencies.add(dependency);
          }
          case OnSourceSet onSourceSet -> {
            final var dependency = new Dependency();
            final SourceSet ss = onSourceSet.sourceSet();
            final GroupArtifactVersion gav = MavenArtifactResolverUtils.makeSourceSetGav(ss);
            dependency.setGroupId(gav.groupId());
            dependency.setArtifactId(gav.artifactId());
            dependency.setVersion(gav.version());

            final GroupArtifact ga = gav.groupArtifact();
            if (runtimeClasspathArtifacts.contains(ga)) {
              dependency.setScope("compile");
            } else {
              dependency.setScope("provided");
            }

            dependencies.add(dependency);
          }
          case Jar ignored -> {
            // do nothing
          }
        }

      }

      for (final var d : sourceSet.runtimeDependencies()) {
        switch (d) {
          case Remote.WithVersion withVersion -> {
            final var dependency = new Dependency();
            final GroupArtifactVersion gav = withVersion.gav();
            dependency.setGroupId(gav.groupId());
            dependency.setArtifactId(gav.artifactId());
            dependency.setVersion(gav.version());

            final GroupArtifact ga = gav.groupArtifact();
            if (!compileClasspathArtifacts.contains(ga)) {
              dependency.setScope("runtime");
            }

            dependencies.add(dependency);
          }

          case Remote.WithoutVersion withoutVersion -> {
            final var dependency = new Dependency();
            final GroupArtifact ga = withoutVersion.ga();
            dependency.setGroupId(ga.groupId());
            dependency.setArtifactId(ga.artifactId());
            // version should be complemented by dependency constraints

            if (!compileClasspathArtifacts.contains(ga)) {
              dependency.setScope("runtime");
            }

            dependencies.add(dependency);
          }

          case OnProject onProject -> {
            final var dependency = new Dependency();
            final GroupArtifactVersion gav = onProject.project().gav();
            dependency.setGroupId(gav.groupId());
            dependency.setArtifactId(gav.artifactId());
            dependency.setVersion(gav.version());

            final GroupArtifact ga = gav.groupArtifact();
            if (!compileClasspathArtifacts.contains(ga)) {
              dependency.setScope("runtime");
            }

            dependencies.add(dependency);
          }
          case OnSourceSet onSourceSet -> {
            final var dependency = new Dependency();
            final SourceSet ss = onSourceSet.sourceSet();
            final GroupArtifactVersion gav = MavenArtifactResolverUtils.makeSourceSetGav(ss);
            dependency.setGroupId(gav.groupId());
            dependency.setArtifactId(gav.artifactId());
            dependency.setVersion(gav.version());

            final GroupArtifact ga = gav.groupArtifact();
            if (!compileClasspathArtifacts.contains(ga)) {
              dependency.setScope("runtime");
            }

            dependencies.add(dependency);
          }
          case Jar ignored -> {
            // do nothing
          }
        }
      }

      model.setDependencies(dependencies);
    }

    final Path pomDir = workdir
        .resolve(project.path())
        .resolve(project.artifactLayout().rootDir());
    final Path pomPath = pomDir.resolve(
        sourceSetGav.artifactId() + '-' + sourceSetGav.version() + ".pom");

    try {
      Files.createDirectories(pomDir);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create POM file directory " + pomDir, e);
    }

    try (final var os = Files.newOutputStream(pomPath)) {
      final var writer = new MavenXpp3Writer();
      writer.write(os, model);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create POM file " + pomPath, e);
    }

    log.debug("Generated {} POM file at {}", project.artifactId(), pomPath);
    return pomPath.toFile();
  }

  @Nullable
  private static GroupArtifact mapDependencyToGa(com.github.build.deps.Dependency d) {
    return switch (d) {
      case Jar ignored -> null;
      case OnProject onProject -> onProject.project().gav().groupArtifact();
      case OnSourceSet onSourceSet ->
          MavenArtifactResolverUtils.makeSourceSetGav(onSourceSet.sourceSet()).groupArtifact();
      case Remote.WithoutVersion withoutVersion -> withoutVersion.ga();
      case Remote.WithVersion withVersion -> withVersion.gav().groupArtifact();
    };
  }

  @Override
  public List<String> findVersions(final Artifact artifact) {
    Objects.requireNonNull(artifact);
    final var ga = new GroupArtifact(
        artifact.getGroupId(),
        artifact.getArtifactId()
    );
    return projectService.allProjects()
        .stream()
        .filter(p -> p.gav().groupArtifact().equals(ga))
        .findFirst()
        .map(Project::version)
        .stream().toList();
  }
}
