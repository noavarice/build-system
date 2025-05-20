package com.github.build;

import java.util.Objects;
import java.util.Set;

/**
 * @author noavarice
 * @since 1.0.0
 */
public record SourceSet(
    String name,
    Type type,
    Set<Dependency> dependencies
) {

  public SourceSet {
    Objects.requireNonNull(name);
    if (name.isBlank()) {
      throw new IllegalArgumentException("Must not be empty");
    }

    Objects.requireNonNull(type);
  }

  public enum Type {
    PROD,
    TEST,
    DEV,
  }
}
