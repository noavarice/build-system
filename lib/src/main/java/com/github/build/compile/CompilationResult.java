package com.github.build.compile;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Result of compiling a set of sources with one compiler.
 *
 * @param outputDirectory Directory the produced class files were written to
 * @author noavarice
 * @since 1.0.0
 */
public record CompilationResult(Path outputDirectory) {

  public CompilationResult {
    Objects.requireNonNull(outputDirectory);
    outputDirectory = outputDirectory.normalize().toAbsolutePath();
  }
}
