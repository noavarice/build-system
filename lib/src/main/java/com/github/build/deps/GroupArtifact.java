package com.github.build.deps;

import com.github.build.ExtraSourceSetDependency;
import com.github.build.MainSourceSetDependency;
import com.github.build.TestSourceSetDependency;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * @author noavarice
 * @since 1.0.0
 */
public record GroupArtifact(
    String groupId,
    String artifactId,
    @Nullable String classifier
) implements MainSourceSetDependency, TestSourceSetDependency, ExtraSourceSetDependency {

  public GroupArtifact(final String groupId, final String artifactId) {
    this(groupId, artifactId, null);
  }

  public static GroupArtifact parse(final String value) {
    Objects.requireNonNull(value);
    final String[] parts = value.split(":", -1);
    return switch (parts.length) {
      case 2 -> new GroupArtifact(parts[0], parts[1]);
      case 3 -> new GroupArtifact(parts[0], parts[1], parts[2]); // classifier
      case 4 -> new GroupArtifact(parts[0], parts[1], parts[3]); // skipping extension
      default -> throw new IllegalStateException("Unexpected part count " + parts.length);
    };
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

    if (classifier != null) {
      classifier = classifier.strip();
      if (classifier.isBlank()) {
        classifier = null;
      }
    }
  }

  public GroupArtifactVersion withVersion(final String version) {
    return new GroupArtifactVersion(groupId, artifactId, classifier, version);
  }

  @Override
  public String toString() {
    if (classifier == null) {
      return groupId + ':' + artifactId;
    }

    return String.join(":", groupId, artifactId, classifier);
  }
}
