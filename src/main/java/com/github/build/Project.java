package com.github.build;

import java.util.Objects;
import java.util.Set;

/**
 * Project to build.
 *
 * @author noavarice
 * @since 1.0.0
 */
public interface Project {

  /**
   * Identifies project, so you don't need to mess with physical project location on disk.
   *
   * @return Project ID, never null
   */
  Id id();

  /**
   * Project dependencies.
   *
   * @return Unmodifiable set of project dependencies, never null but can be empty
   */
  Set<Dependency> dependencies();

  /**
   * Project ID.
   *
   * @param value ID string
   */
  record Id(String value) {

    public Id {
      Objects.requireNonNull(value);
      if (value.isBlank()) {
        throw new IllegalArgumentException("Must not be empty");
      }

      value = value.strip();
    }
  }
}
