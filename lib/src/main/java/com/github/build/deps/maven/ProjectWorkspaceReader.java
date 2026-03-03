package com.github.build.deps.maven;

import static com.github.build.deps.Dependency.Jar;
import static com.github.build.deps.Dependency.OnProject;
import static com.github.build.deps.Dependency.OnSourceSet;
import static com.github.build.deps.Dependency.Remote;
import static java.util.stream.Collectors.toUnmodifiableMap;

import com.github.build.Project;
import com.github.build.deps.GroupArtifact;
import com.github.build.deps.GroupArtifactVersion;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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

/**
 * Integrates {@link Project} into Maven Artifact Resolver resolution mechanism.
 *
 * @author noavarice
 * @since 1.0.0
 */
public final class ProjectWorkspaceReader implements WorkspaceReader {

  private final WorkspaceRepository repository;

  private final Path workdir;

  private final Map<GroupArtifactVersion, Project> projects;

  public ProjectWorkspaceReader(
      final WorkspaceRepository repository,
      final Path workdir,
      final Set<Project> projects
  ) {
    this.repository = Objects.requireNonNull(repository);
    this.workdir = Objects.requireNonNull(workdir);
    this.projects = projects
        .stream()
        .collect(toUnmodifiableMap(Project::gav, project -> project));
  }

  @Override
  public WorkspaceRepository getRepository() {
    return repository;
  }

  @Override
  @Nullable
  public File findArtifact(final Artifact artifact) {
    Objects.requireNonNull(artifact);
    final var gav = new GroupArtifactVersion(
        artifact.getGroupId(),
        artifact.getArtifactId(),
        artifact.getVersion()
    );

    final Project project = projects.get(gav);
    if (project == null) {
      return null;
    }

    return switch (artifact.getExtension()) {
      case "jar" -> workdir
          .resolve(project.path())
          .resolve(project.artifactLayout().rootDir())
          .resolve(project.artifactId() + ".jar")
          .toFile();
      case "pom" -> generatePom(project);
      default -> null;
    };
  }

  /**
   * Maps project to POM {@link Model}, serializes it into XML and saves under build directory.
   */
  private File generatePom(final Project project) {
    final var model = new Model();
    model.setModelVersion("4.0.0");
    model.setGroupId(project.groupId());
    model.setArtifactId(project.artifactId());
    model.setVersion(project.version());

    // setting dependency management
    {
      final var dependencyManagement = new DependencyManagement();
      project.mainSourceSet().dependencyConstraints()
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
      final List<Dependency> dependencies = project.mainSourceSet().exposedClasspath()
          .stream()
          .map(d -> {
            final var dependency = new Dependency();
            dependency.setScope("compile");

            switch (d) {
              case Remote.WithVersion withVersion -> {
                final GroupArtifactVersion gav = withVersion.gav();
                dependency.setGroupId(gav.groupId());
                dependency.setArtifactId(gav.artifactId());
                dependency.setVersion(gav.version());
              }

              case Remote.WithoutVersion withoutVersion -> {
                final GroupArtifact ga = withoutVersion.ga();
                dependency.setGroupId(ga.groupId());
                dependency.setArtifactId(ga.artifactId());
                // version should be complemented by dependency constraints
              }

              case OnProject onProject -> {
                final GroupArtifactVersion gav = onProject.project().gav();
                dependency.setGroupId(gav.groupId());
                dependency.setArtifactId(gav.artifactId());
                dependency.setVersion(gav.version());
              }
              // exposing source set other than main seems wrong
              case OnSourceSet ignored -> throw new IllegalStateException();
              // TODO: check if it's possible to expose JAR as part of POM
              case Jar ignored -> throw new IllegalStateException();
            }

            return dependency;
          })
          .toList();
      model.setDependencies(dependencies);
    }

    final Path pomDir = workdir
        .resolve(project.path())
        .resolve(project.artifactLayout().rootDir());
    final Path pomPath = pomDir.resolve(project.artifactId() + '-' + project.version() + ".pom");

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

    return pomPath.toFile();
  }

  @Override
  public List<String> findVersions(final Artifact artifact) {
    Objects.requireNonNull(artifact);
    throw new UnsupportedOperationException();
  }
}
