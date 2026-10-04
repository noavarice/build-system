package com.github.build.deps.maven;

import com.github.build.Project;
import com.github.build.SourceSet;
import com.github.build.deps.Dependency;
import com.github.build.deps.GroupArtifact;
import com.github.build.deps.GroupArtifactVersion;
import java.util.Map;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.jspecify.annotations.Nullable;

/**
 * @author noavarice
 * @since 1.0.0
 */
public final class MavenArtifactResolverUtils {

  private MavenArtifactResolverUtils() {
  }

  @Nullable
  public static GroupArtifact mapDependencyToGa(com.github.build.deps.Dependency d) {
    return switch (d) {
      case Dependency.Jar ignored -> null;
      case Dependency.OnProject onProject -> onProject.project().gav().groupArtifact();
      case Dependency.OnSourceSet onSourceSet ->
          MavenArtifactResolverUtils.makeSourceSetGav(onSourceSet.sourceSet()).groupArtifact();
      case Dependency.Remote.WithoutVersion withoutVersion -> withoutVersion.ga();
      case Dependency.Remote.WithVersion withVersion -> withVersion.gav().groupArtifact();
    };
  }

  /**
   * Customizes GAV, so it's possible to resolve project source set as an independent artifact.
   *
   * @param sourceSet Source set
   * @return (Optionally) modified GAV for source set resolution
   */
  public static GroupArtifactVersion makeSourceSetGav(final SourceSet sourceSet) {
    final GroupArtifactVersion projectGav = sourceSet.project().gav();
    // TODO: introduce MainSourceSet type and use type check instead of name check (name can differ)
    if (sourceSet.id().equals(SourceSet.Id.MAIN)) {
      return projectGav;
    }

    return new GroupArtifactVersion(
        projectGav.groupId(),
        projectGav.artifactId() + SOURCE_SET_ARTIFACT_PREFIX + sourceSet.id(),
        projectGav.version()
    );
  }

  public static Artifact toArtifact(final Project project) {
    return new DefaultArtifact(
        project.groupId(),
        project.artifactId(),
        null,
        "jar",
        project.version()
    );
  }

  public static org.eclipse.aether.graph.Dependency toDependency(
      final GroupArtifactVersion gav,
      @Nullable final String scope
  ) {
    final var artifact = new DefaultArtifact(
        gav.groupId(),
        gav.artifactId(),
        null,
        "jar",
        gav.version()
    );
    return new org.eclipse.aether.graph.Dependency(artifact, scope);
  }

  @Nullable
  public static SourceSet mapArtifactToSourceSet(final Artifact artifact, final Project project) {
    if (!artifact.getGroupId().equals(project.groupId())) {
      return null;
    }

    final String artifactId = artifact.getArtifactId();
    if (!artifactId.startsWith(project.artifactId())) {
      return null;
    }

    for (final Map.Entry<SourceSet.Id, SourceSet> pair : project.sourceSets().entrySet()) {
      final SourceSet.Id sourceSetId = pair.getKey();
      final String sourceSetArtifactId = sourceSetId.equals(SourceSet.Id.MAIN)
          ? project.artifactId()
          : project.artifactId() + SOURCE_SET_ARTIFACT_PREFIX + sourceSetId;
      if (artifactId.equals(sourceSetArtifactId)) {
        return pair.getValue();
      }
    }

    return null;
  }

  private static final String SOURCE_SET_ARTIFACT_PREFIX = "-source-set-";
}
