package com.github.build.deps;

import java.util.Objects;

/**
 * @author noavarice
 * @since 1.0.0
 */
public record Coordinates(String groupId, String artifactId, String version) {

  public Coordinates {
    groupId = Objects.requireNonNull(groupId).strip();
    if (groupId.isBlank()) {
      throw new IllegalArgumentException();
    }

    artifactId = Objects.requireNonNull(artifactId).strip();
    if (artifactId.isBlank()) {
      throw new IllegalArgumentException();
    }

    version = Objects.requireNonNull(version).strip();
    if (version.isBlank()) {
      throw new IllegalArgumentException();
    }
  }
}
