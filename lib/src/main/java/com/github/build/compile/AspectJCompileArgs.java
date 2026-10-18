package com.github.build.compile;

import static java.util.stream.Collectors.toUnmodifiableSet;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * AspectJ compilation arguments.
 *
 * @param sourceRoots Source root directories to compile (same as "-sourceroots")
 * @param classesDir  Directory for storing generated class files (same as "-d")
 * @param classpath   Compilation classpath (same as "-classpath")
 * @param aspectPath  Directories of already compiled aspects to weave against (same as
 *                    "-aspectpath")
 * @param outXmlFile  File to write the AspectJ XML configuration to (same as "-outxmlfile"), or
 *                    {@code null} to skip configuration generation
 * @author noavarice
 * @apiNote Upon object construction, all path sets are normalized and resolved to absolute paths
 * according to {@link Path#toAbsolutePath()}, so it's recommended to pass absolute paths to avoid
 * undesired implementation-dependent path resolution
 * @since 1.0.0
 */
public record AspectJCompileArgs(
    Set<Path> sourceRoots,
    Path classesDir,
    Set<Path> classpath,
    Set<Path> aspectPath,
    @Nullable Path outXmlFile
) {

  public AspectJCompileArgs {
    sourceRoots = Objects.requireNonNull(sourceRoots)
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
    aspectPath = Objects.requireNonNull(aspectPath)
        .stream()
        .map(Objects::requireNonNull)
        .map(Path::normalize)
        .map(Path::toAbsolutePath)
        .collect(toUnmodifiableSet());
  }
}
