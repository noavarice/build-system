package com.github.build;

import com.github.build.deps.Dependency;
import com.github.build.util.PathUtils;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @param path Path to source set content relative to {@link Project#path() project directory}
 * @author noavarice
 * @since 1.0.0
 */
public record SourceSet(
    Id id,
    Path path,
    SourceSetLayout layout,
    Type type,
    Set<Dependency> dependencies
) {

  public static Builder withId(final String idStr) {
    final var id = new SourceSet.Id(idStr);
    return new Builder(id);
  }

  public SourceSet {
    Objects.requireNonNull(id);

    path = Objects.requireNonNull(path).normalize();
    PathUtils.checkRelative(path);

    Objects.requireNonNull(layout);
    Objects.requireNonNull(type);
    dependencies = Set.copyOf(dependencies);
  }

  public record Id(String value) {

    public Id {
      value = Objects.requireNonNull(value).strip();
      if (value.isBlank()) {
        throw new IllegalArgumentException();
      }
    }

    @Override
    public String toString() {
      return value;
    }
  }

  public enum Type {
    PROD,
    TEST,
    DEV,
  }

  public static final class Builder {

    private final Id id;

    private SourceSetLayout layout = SourceSetLayout.DEFAULT;

    private Type type = Type.PROD;

    private final Set<Dependency> dependencies = new HashSet<>();

    private Builder(final Id id) {
      this.id = id;
    }

    public Builder withLayout(final SourceSetLayout layout) {
      this.layout = Objects.requireNonNull(layout);
      return this;
    }

    public Builder withDependency(final Dependency dependency) {
      dependencies.add(dependency);
      return this;
    }

    public SourceSet build() {
      if (id == null) {
        throw new IllegalStateException();
      }

      return new SourceSet(
          id,
          Path.of("src", id.value()),
          layout,
          type,
          dependencies
      );
    }
  }
}
