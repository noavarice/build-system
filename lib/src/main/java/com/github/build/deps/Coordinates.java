package com.github.build.deps;

import java.util.Objects;

/**
 * @author noavarice
 * @since 1.0.0
 */
public record Coordinates(String groupId, String artifactId, String version) {

  public static Coordinates parse(final String value) {
    Objects.requireNonNull(value);
    final String[] parts = value.split(":", -1);
    return switch (parts.length) {
      case 3 -> new Coordinates(parts[0], parts[1], parts[2]);
      case 4 -> new Coordinates(parts[0], parts[1], parts[3]); // skipping classifier
      default -> throw new IllegalStateException("Unexpected part count " + parts.length);
    };
  }

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

  public ArtifactCoordinates artifactCoordinates() {
    return new ArtifactCoordinates(groupId, artifactId);
  }

  @Override
  public String toString() {
    return String.join(":", groupId, artifactId, version);
  }
}
