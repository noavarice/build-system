package com.github.build;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Arguments for declaring a local JAR dependency in a source set.
 *
 * @author noavarice
 * @since 1.0.0
 */
public record LocalJar(Path path) implements MainSourceSetDependency, TestSourceSetDependency {

  public LocalJar {
    Objects.requireNonNull(path);
  }
}
