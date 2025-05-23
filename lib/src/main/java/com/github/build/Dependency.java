package com.github.build;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Project dependency.
 *
 * @author noavarice
 * @since 1.0.0
 */
public sealed interface Dependency permits Dependency.File {

  /**
   * Defines where this dependency is used (e.g., as part of compilation classpath).
   *
   * @return Dependency scope
   */
  Scope scope();

  /**
   * Defines where this dependency is used (e.g., as part of compilation classpath).
   */
  enum Scope {
    /**
     * Dependency with this scope will be used as part of source set compilation classpath.
     */
    COMPILE,
  }

  /**
   * Designates dependency on local file.
   *
   * @param path File path
   */
  record File(Path path, Scope scope) implements Dependency {

    public File {
      Objects.requireNonNull(path);
      // not checking if file exists as its may not be present for now
      Objects.requireNonNull(scope);
    }
  }
}
