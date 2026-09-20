package com.github.build;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Arguments for declaring a local JAR dependency in a source set.
 *
 * @author noavarice
 * @since 1.0.0
 */
public record LocalJarArgs(Path path) implements MainSourceSetDependency, TestSourceSetDependency {

  public LocalJarArgs {
    Objects.requireNonNull(path);
  }
}