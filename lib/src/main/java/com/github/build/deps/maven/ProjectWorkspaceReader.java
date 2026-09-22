package com.github.build.deps.maven;

import static com.github.build.deps.Dependency.Jar;
import static com.github.build.deps.Dependency.OnProject;
import static com.github.build.deps.Dependency.OnSourceSet;
import static com.github.build.deps.Dependency.Remote;
import static java.util.stream.Collectors.toUnmodifiableSet;

import com.github.build.Project;
import com.github.build.ProjectService;
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
    final var gav = new GroupArtifactVersion(
        artifact.getGroupId(),
        artifact.getArtifactId(),
        artifact.getVersion()
    );

    final Project project = projectService.allProjects()
        .stream()
        .filter(p -> p.gav().equals(gav))
        .findFirst()
        .orElse(null);
    if (project == null) {
      return null;
    }

    return switch (artifact.getExtension()) {
      case "", "jar" -> workdir
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
      // TODO: dependency order matters here
      final Set<GroupArtifact> compileClasspathArtifacts = project.mainSourceSet()
          .compileDependencies()
          .stream()
          .map(d -> switch (d) {
            case Jar ignored -> null;
            case OnProject onProject -> onProject.project().gav().groupArtifact();
            // Maven cannot depend on source set explicitly
            // TODO: should we handle this differently?
            case OnSourceSet ignored -> null;
            case Remote.WithoutVersion withoutVersion -> withoutVersion.ga();
            case Remote.WithVersion withVersion -> withVersion.gav().groupArtifact();
          })
          .filter(Objects::nonNull)
          .collect(toUnmodifiableSet());
      final Set<GroupArtifact> runtimeClasspathArtifacts = project.mainSourceSet()
          .runtimeDependencies()
          .stream()
          .map(d -> switch (d) {
            case Jar ignored -> null;
            case OnProject onProject -> onProject.project().gav().groupArtifact();
            // Maven cannot depend on source set explicitly
            // TODO: should we handle this differently?
            case OnSourceSet ignored -> null;
            case Remote.WithoutVersion withoutVersion -> withoutVersion.ga();
            case Remote.WithVersion withVersion -> withVersion.gav().groupArtifact();
          })
          .filter(Objects::nonNull)
          .collect(toUnmodifiableSet());

      final List<Dependency> dependencies = new ArrayList<>(
          compileClasspathArtifacts.size() + runtimeClasspathArtifacts.size()
      );

      for (final var d : project.mainSourceSet().compileDependencies()) {
        final var dependency = new Dependency();

        switch (d) {
          case Remote.WithVersion withVersion -> {
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
          }

          case Remote.WithoutVersion withoutVersion -> {
            final GroupArtifact ga = withoutVersion.ga();
            dependency.setGroupId(ga.groupId());
            dependency.setArtifactId(ga.artifactId());
            // version should be complemented by dependency constraints

            if (runtimeClasspathArtifacts.contains(ga)) {
              dependency.setScope("compile");
            } else {
              dependency.setScope("provided");
            }
          }

          case OnProject onProject -> {
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
          }
          case OnSourceSet ignored -> {
            // do nothing
          }
          case Jar ignored -> {
            // do nothing
            // TODO: check if it's possible to expose JAR as part of POM
          }
        }

        dependencies.add(dependency);
      }

      for (final var d : project.mainSourceSet().runtimeDependencies()) {
        final var dependency = new Dependency();

        switch (d) {
          case Remote.WithVersion withVersion -> {
            final GroupArtifactVersion gav = withVersion.gav();
            dependency.setGroupId(gav.groupId());
            dependency.setArtifactId(gav.artifactId());
            dependency.setVersion(gav.version());

            final GroupArtifact ga = gav.groupArtifact();
            if (!compileClasspathArtifacts.contains(ga)) {
              dependency.setScope("runtime");
            }
          }

          case Remote.WithoutVersion withoutVersion -> {
            final GroupArtifact ga = withoutVersion.ga();
            dependency.setGroupId(ga.groupId());
            dependency.setArtifactId(ga.artifactId());
            // version should be complemented by dependency constraints

            if (!compileClasspathArtifacts.contains(ga)) {
              dependency.setScope("runtime");
            }
          }

          case OnProject onProject -> {
            final GroupArtifactVersion gav = onProject.project().gav();
            dependency.setGroupId(gav.groupId());
            dependency.setArtifactId(gav.artifactId());
            dependency.setVersion(gav.version());

            final GroupArtifact ga = gav.groupArtifact();
            if (!compileClasspathArtifacts.contains(ga)) {
              dependency.setScope("runtime");
            }
          }
          case OnSourceSet ignored -> {
            // do nothing
          }
          case Jar ignored -> {
            // do nothing
            // TODO: check if it's possible to expose JAR as part of POM
          }
        }

        dependencies.add(dependency);
      }

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

    log.debug("Generated {} POM file at {}", project.artifactId(), pomPath);
    return pomPath.toFile();
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
