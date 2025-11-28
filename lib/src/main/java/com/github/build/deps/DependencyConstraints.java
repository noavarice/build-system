package com.github.build.deps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Set of constraints to use when resolving dependencies without exact versions.
 *
 * @author noavarice
 */
public final class DependencyConstraints {

  public static Builder builder() {
    return new Builder();
  }

  public static final DependencyConstraints EMPTY = new DependencyConstraints(Map.of());

  private final Map<GroupArtifact, String> constraints;

  private DependencyConstraints(final Map<GroupArtifact, String> constraints) {
    this.constraints = Map.copyOf(constraints);
  }

  public Builder copy() {
    return new Builder(constraints);
  }

  // TODO: add specific type for version constraints (exact version, ranges, etc.)
  @Nullable
  public String getConstraint(final GroupArtifact artifact) {
    Objects.requireNonNull(artifact);
    return constraints.get(artifact);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj instanceof DependencyConstraints other) {
      return constraints.equals(other.constraints);
    } else {
      return false;
    }
  }

  public static final class Builder {

    private final Map<GroupArtifact, String> constraints;

    private Builder() {
      constraints = new HashMap<>();
    }

    private Builder(final Map<GroupArtifact, String> values) {
      this.constraints = new HashMap<>(values);
    }

    public Builder withExactVersion(final String artifactStr, final String... other) {
      Objects.requireNonNull(artifactStr);

      final var artifacts = new ArrayList<GroupArtifactVersion>();
      artifacts.add(GroupArtifactVersion.parse(artifactStr));
      if (other != null) {
        for (final String s : other) {
          artifacts.add(GroupArtifactVersion.parse(s));
        }
      }

      for (final GroupArtifactVersion artifact : artifacts) {
        constraints.put(artifact.groupArtifact(), artifact.version());
      }

      return this;
    }

    public Builder withExactVersion(final GroupArtifactVersion artifact) {
      Objects.requireNonNull(artifact);
      constraints.put(artifact.groupArtifact(), artifact.version());
      return this;
    }

    public Builder withExactVersion(final GroupArtifact artifact, final String version) {
      Objects.requireNonNull(artifact);
      Objects.requireNonNull(version);
      constraints.put(artifact, version);
      return this;
    }

    public DependencyConstraints build() {
      return new DependencyConstraints(constraints);
    }
  }
}
