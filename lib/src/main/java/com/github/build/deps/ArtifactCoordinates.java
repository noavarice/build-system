package com.github.build.deps;

import java.util.Objects;

/**
 * @author noavarice
 * @since 1.0.0
 */
public record ArtifactCoordinates(String groupId, String artifactId) {

  public ArtifactCoordinates {
    groupId = Objects.requireNonNull(groupId).strip();
    if (groupId.isBlank()) {
      throw new IllegalArgumentException();
    }

    artifactId = Objects.requireNonNull(artifactId).strip();
    if (artifactId.isBlank()) {
      throw new IllegalArgumentException();
    }
  }

  public Coordinates withVersion(final String version) {
    return new Coordinates(groupId, artifactId, version);
  }

  @Override
  public String toString() {
    return groupId + ':' + artifactId;
  }
}
