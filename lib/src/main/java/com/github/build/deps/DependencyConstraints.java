package com.github.build.deps;

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

  // TODO: add specific type for version constraints (exact version, ranges, etc.)
  @Nullable
  public String getConstraint(final GroupArtifact artifact) {
    Objects.requireNonNull(artifact);
    return constraints.get(artifact);
  }

  public static final class Builder {

    private final Map<GroupArtifact, String> constraints = new HashMap<>();

    private Builder() {
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
