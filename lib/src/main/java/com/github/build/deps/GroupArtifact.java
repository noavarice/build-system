package com.github.build.deps;

import java.util.Objects;

/**
 * @author noavarice
 * @since 1.0.0
 */
public record GroupArtifact(String groupId, String artifactId) {

  public static GroupArtifact parse(final String value) {
    Objects.requireNonNull(value);
    final String[] parts = value.split(":", -1);
    if (parts.length != 2) {
      throw new IllegalStateException("Unexpected part count " + parts.length);
    }
    return new GroupArtifact(parts[0], parts[1]);
  }

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
