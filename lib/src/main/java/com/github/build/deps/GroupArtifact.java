package com.github.build.deps;

import java.util.Objects;

/**
 * @author noavarice
 * @since 1.0.0
 */
public record GroupArtifact(String groupId, String artifactId) {

  public GroupArtifact {
    groupId = Objects.requireNonNull(groupId).strip();
    if (groupId.isBlank()) {
      throw new IllegalArgumentException();
    }

    artifactId = Objects.requireNonNull(artifactId).strip();
    if (artifactId.isBlank()) {
      throw new IllegalArgumentException();
    }
  }

  public GroupArtifactVersion withVersion(final String version) {
    return new GroupArtifactVersion(groupId, artifactId, version);
  }

  @Override
  public String toString() {
    return groupId + ':' + artifactId;
  }
}
