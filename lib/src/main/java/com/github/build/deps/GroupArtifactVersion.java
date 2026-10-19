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
public record GroupArtifactVersion(
    String groupId,
    String artifactId,
    @Nullable String classifier,
    String version
) implements MainSourceSetDependency, TestSourceSetDependency, ExtraSourceSetDependency {

  public GroupArtifactVersion(final String groupId, final String artifactId, final String version) {
    this(groupId, artifactId, null, version);
  }

  public static GroupArtifactVersion parse(final String value) {
    Objects.requireNonNull(value);
    final String[] parts = value.split(":", -1);
    return switch (parts.length) {
      case 3 -> new GroupArtifactVersion(parts[0], parts[1], null, parts[2]);
      case 4 -> new GroupArtifactVersion(parts[0], parts[1], null, parts[3]); // skipping extension
      case 5 ->
          new GroupArtifactVersion(parts[0], parts[1], parts[3], parts[4]); // skipping extension
      default -> throw new IllegalStateException("Unexpected part count " + parts.length);
    };
  }

  public GroupArtifactVersion {
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

    version = Objects.requireNonNull(version).strip();
    if (version.isBlank()) {
      throw new IllegalArgumentException();
    }
  }

  public GroupArtifact groupArtifact() {
    return new GroupArtifact(groupId, artifactId, classifier);
  }

  @Override
  public String toString() {
    if (classifier == null) {
      return String.join(":", groupId, artifactId, version);
    }

    return String.join(":", groupId, artifactId, "jar", classifier, version);
  }
}
