package com.github.build;

import com.github.build.deps.GroupArtifact;
import com.github.build.util.PathUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author noavarice
 * @since 1.0.0
 */
public final class ProjectService {

  private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

  private final Set<GroupArtifact> groupArtifacts = new HashSet<>();

  private final Set<Project> projects = new HashSet<>();

  // TODO: either pass all required args or provide all one-by-one
  public Project create(
      final String groupId,
      final String artifactId,
      final String version,
      final Consumer<ProjectBuilder> builderConsumer
  ) {
    Objects.requireNonNull(builderConsumer);
    final var ga = new GroupArtifact(groupId, artifactId);
    if (groupArtifacts.contains(ga)) {
      // TODO: use dedicated exception
      throw new IllegalStateException("There are two %s projects".formatted(ga));
    }

    groupArtifacts.add(ga);
    final var builder = new Builder(groupId, artifactId).withVersion(version);
    builderConsumer.accept(builder);
    final Project project = builder.build();
    // TODO: add checks (name uniqueness, path uniqueness, etc.)
    projects.add(project);
    log.debug("[project={}] Created", project.gav());
    return project;
  }

  // TODO: return DAG (or add separate method that enforces build order)
  public Set<Project> allProjects() {
    return Collections.unmodifiableSet(projects);
  }

  private static final class Builder implements ProjectBuilder {

    private final String groupId;

    private final String artifactId;

    private String version = "0.1.0";

    private Path path = Path.of("");

    private MainSourceSetArgs mainSourceSetArgs;

    private List<TestSourceSetArgs> testSourceSetArgsList = new ArrayList<>();

    private Project.ArtifactLayout artifactLayout = Project.ArtifactLayout.DEFAULT;

    private Builder(final String groupId, final String artifactId) {
      this.groupId = Objects.requireNonNull(groupId);
      this.artifactId = Objects.requireNonNull(artifactId);
    }

    @Override
    public Builder withVersion(final String version) {
      Objects.requireNonNull(version);
      this.version = version;
      return this;
    }

    @Override
    public Builder withPath(final String path) {
      return withPath(Path.of(path));
    }

    @Override
    public Builder withPath(final Path path) {
      Objects.requireNonNull(path);
      PathUtils.checkRelative(path);
      this.path = path.normalize();
      return this;
    }

    @Override
    public ProjectBuilder withSourceSets(
        final MainSourceSetArgs mainSourceSetArgs,
        final TestSourceSetArgs... testSourceSetArgsList
    ) {
      this.mainSourceSetArgs = Objects.requireNonNull(mainSourceSetArgs);
      this.testSourceSetArgsList = List.of(testSourceSetArgsList);
      return this;
    }

    @Override
    public Builder withArtifactLayout(final Project.ArtifactLayout artifactLayout) {
      Objects.requireNonNull(artifactLayout);
      this.artifactLayout = artifactLayout;
      return this;
    }

    private Project build() {
      return new Project(
          groupId,
          artifactId,
          version,
          path,
          mainSourceSetArgs,
          testSourceSetArgsList,
          artifactLayout
      );
    }
  }
}
