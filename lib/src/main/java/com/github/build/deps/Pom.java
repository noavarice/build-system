package com.github.build.deps;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Dependency's POM data from remote repository.
 *
 * @author noavarice
 * @since 1.0.0
 */
public record Pom(
    String groupId,
    String artifactId,
    @Nullable String version,
    @Nullable Parent parent,
    Map<String, String> properties,
    List<Dependency> dependencies
) {

  public Pom {
    groupId = Objects.requireNonNull(groupId).strip();
    if (groupId.isBlank()) {
      throw new IllegalArgumentException();
    }

    artifactId = Objects.requireNonNull(artifactId).strip();
    if (artifactId.isBlank()) {
      throw new IllegalArgumentException();
    }

    if (version != null) {
      version = version.strip();
      if (version.isBlank()) {
        throw new IllegalArgumentException();
      }
    }

    properties = Map.copyOf(properties);
    dependencies = List.copyOf(dependencies);
  }

  public record Parent(String groupId, String artifactId, @Nullable String version) {

    public Parent {
      groupId = Objects.requireNonNull(groupId).strip();
      if (groupId.isBlank()) {
        throw new IllegalArgumentException();
      }

      artifactId = Objects.requireNonNull(artifactId).strip();
      if (artifactId.isBlank()) {
        throw new IllegalArgumentException();
      }

      if (version != null) {
        version = version.strip();
        if (version.isBlank()) {
          throw new IllegalArgumentException();
        }
      }
    }
  }

  public record Dependency(String groupId, String artifactId, @Nullable String version) {

    public Dependency {
      Objects.requireNonNull(groupId);
      Objects.requireNonNull(artifactId);
    }
  }
}
