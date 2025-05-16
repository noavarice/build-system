package com.github.build;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Project dependency.
 *
 * @author noavarice
 * @since 1.0.0
 */
public sealed interface Dependency permits Dependency.File, Project {

  /**
   * Designates dependency on local file.
   *
   * @param path File path
   */
  record File(Path path) implements Dependency {

    public File {
      Objects.requireNonNull(path);
      // not checking if file exists as its may not be present for now
    }
  }
}
