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
    String version,
    @Nullable Parent parent,
    Map<String, String> properties,
    List<Dependency> dependencyManagement, // TODO: use separate model with non-nullable version
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

    version = Objects.requireNonNull(version).strip();
    if (version.isBlank()) {
      throw new IllegalArgumentException();
    }

    properties = Map.copyOf(properties);
    dependencies = List.copyOf(dependencies);
  }

  public Coordinates coordinates() {
    return new Coordinates(groupId, artifactId, version);
  }

  @Override
  public boolean equals(final Object obj) {
    if (!(obj instanceof Pom other)) {
      return false;
    }

    return groupId.equals(other.groupId)
        && artifactId.equals(other.artifactId)
        && version.equals(other.version);
  }

  public record Parent(String groupId, String artifactId, String version) {

    public Parent {
      groupId = Objects.requireNonNull(groupId).strip();
      if (groupId.isBlank()) {
        throw new IllegalArgumentException();
      }

      artifactId = Objects.requireNonNull(artifactId).strip();
      if (artifactId.isBlank()) {
        throw new IllegalArgumentException();
      }

      version = version.strip();
      if (version.isBlank()) {
        throw new IllegalArgumentException();
      }
    }

    public Coordinates coordinates() {
      return new Coordinates(groupId, artifactId, version);
    }
  }

  public record Dependency(
      String groupId,
      String artifactId,
      @Nullable String version,
      Scope scope,
      boolean optional
  ) {

    public Dependency {
      groupId = Objects.requireNonNull(groupId).strip();
      artifactId = Objects.requireNonNull(artifactId).strip();
      if (version != null) {
        version = version.strip();
      }

      Objects.requireNonNull(scope);
    }

    public ArtifactCoordinates artifactCoordinates() {
      return new ArtifactCoordinates(groupId, artifactId);
    }

    public enum Scope {
      COMPILE,
      RUNTIME,
      TEST,
      SYSTEM,
      PROVIDED,
    }
  }
}
