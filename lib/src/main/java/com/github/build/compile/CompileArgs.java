package com.github.build.compile;

import static java.util.stream.Collectors.toUnmodifiableSet;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/**
 * Compilation arguments.
 *
 * @param sources    List of sources files to compile
 * @param classesDir Directory for storing generated class files (same as compiler's "-d"
 *                   command-line option)
 * @param classpath  Compilation classpath (same as "-cp", "-classpath" command-line options)
 * @param options    Compiler options (e.g., "--release")
 * @author noavarice
 * @apiNote Upon object construction, all paths are normalized and resolved to an absolute path
 * according to {@link Path::toAbsolutePath()}, so it's recommended to pass absolute paths to avoid
 * undesired implementation-dependent path resolution
 * @since 1.0.0
 */
public record CompileArgs(
    Set<Path> sources,
    Path classesDir,
    Set<Path> classpath,
    CompilerOptions options
) {

  public CompileArgs {
    sources = Objects.requireNonNull(sources)
        .stream()
        .map(Objects::requireNonNull)
        .map(Path::normalize)
        .map(Path::toAbsolutePath)
        .collect(toUnmodifiableSet());
    classesDir = Objects.requireNonNull(classesDir).normalize().toAbsolutePath();
    classpath = Objects.requireNonNull(classpath)
        .stream()
        .map(Objects::requireNonNull)
        .map(Path::normalize)
        .map(Path::toAbsolutePath)
        .collect(toUnmodifiableSet());
    Objects.requireNonNull(options);
  }
}
