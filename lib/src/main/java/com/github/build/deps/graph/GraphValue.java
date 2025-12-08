package com.github.build.deps.graph;

import com.github.build.deps.GroupArtifact;
import com.github.build.deps.GroupArtifactVersion;
import com.github.build.deps.MavenVersion;
import java.util.Objects;

/**
 * @author noavarice
 */
public final class GraphValue {

  public static GraphValue of(final GroupArtifactVersion gav) {
    Objects.requireNonNull(gav);
    return new GraphValue(gav.groupArtifact(), new MavenVersion.Exact(gav.version()));
  }

  private final GroupArtifact groupArtifact;

  private final MavenVersion version;

  public GraphValue(final GroupArtifact groupArtifact, final MavenVersion version) {
    this.groupArtifact = Objects.requireNonNull(groupArtifact);
    this.version = Objects.requireNonNull(version);
  }

  public GroupArtifact groupArtifact() {
    return groupArtifact;
  }

  public MavenVersion version() {
    return version;
  }

  @Override
  public boolean equals(final Object o) {
    if (o instanceof GraphValue graphValue) {
      return Objects.equals(groupArtifact, graphValue.groupArtifact)
          && Objects.equals(version, graphValue.version);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(groupArtifact, version);
  }

  @Override
  public String toString() {
    return groupArtifact.toString() + ':' + version;
  }
}
