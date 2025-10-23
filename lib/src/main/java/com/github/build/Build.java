package com.github.build;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.reverseOrder;

import com.github.build.Project.ArtifactLayout;
import com.github.build.util.PathUtils;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author noavarice
 * @since 1.0.0
 */
public final class Build {

  private static final Logger log = LoggerFactory.getLogger(Build.class);

  private Build() {
  }

  public static void createJar(final JarArgs config) {
    Objects.requireNonNull(config);
    log.debug("Creating JAR file at {}", config.path());
    try (final OutputStream os = Files.newOutputStream(config.path())) {
      final var jos = new JarOutputStream(os);
      config.contents().forEach((path, content) -> {
        log.trace("Writing {} to JAR {}", path, config.path());
        writeJarEntry(jos, path, content);
      });
      jos.finish();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void writeJarEntry(
      final JarOutputStream jos,
      final Path path,
      final JarArgs.Content content
  ) {
    final byte[] bytes = switch (content) {
      case JarArgs.Content.Bytes b -> b.value();
      case JarArgs.Content.File f -> {
        try {
          yield Files.readAllBytes(f.path());
        } catch (final IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    };

    final var entry = new ZipEntry(path.normalize().toString());
    try {
      jos.putNextEntry(entry);
      jos.write(bytes);
      jos.closeEntry();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Copies resources from project's main source set to a {@link ArtifactLayout#resourcesDir()}.
   *
   * @param workdir   Working directory
   * @param sourceSet Source set to copy resources from
   */
  public static void copyResources(final Path workdir, final SourceSet sourceSet) {
    Objects.requireNonNull(workdir);
    Objects.requireNonNull(sourceSet);

    PathUtils.checkAbsolute(workdir);
    PathUtils.checkDirectory(workdir);

    log.info("[project={}][sourceSet={}] Copying resources",
        sourceSet.project().id(),
        sourceSet.id()
    );

    final Project project = sourceSet.project();
    final Path targetDir = workdir
        .resolve(project.path())
        .resolve(project.artifactLayout().rootDir())
        .resolve(project.artifactLayout().resourcesDir())
        .resolve(sourceSet.id().value());

    if (Files.isDirectory(targetDir)) {
      deleteDirectory(targetDir);
    }

    try {
      // always creating directory, even if there's nothing to copy
      Files.createDirectories(targetDir);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    for (final Path dir : sourceSet.resourceDirectories()) {
      final var absolutePath = workdir
          .resolve(project.path())
          .resolve(sourceSet.path())
          .resolve(dir);
      log.info("[project={}][sourceSet={}] Copying resources from {}",
          sourceSet.project().id(),
          sourceSet.id(),
          absolutePath
      );
      copyDirectory(absolutePath, targetDir);
    }
  }

  /**
   * Copies contents of the source directory recursively to a target directory.
   *
   * @param sourceDir Path to directory to be copied
   * @param targetDir Path to directory to copy to
   */
  public static void copyDirectory(final Path sourceDir, final Path targetDir) {
    if (Files.isRegularFile(sourceDir)) {
      throw new IllegalArgumentException("Source path must not be a file");
    }

    if (Files.notExists(sourceDir)) {
      log.debug("{} does not exist, do nothing", sourceDir);
      return;
    }

    if (Files.isRegularFile(targetDir)) {
      throw new IllegalArgumentException("Target path must not be a file");
    }

    copyToNonExistentDirectory(sourceDir, targetDir);
  }

  /**
   * Deletes specified directory and all of its content recursively.
   *
   * @param directory Path to directory
   */
  public static void deleteDirectory(final Path directory) {
    log.info("Removing directory {}", directory);
    Objects.requireNonNull(directory);
    if (!Files.isDirectory(directory)) {
      log.info("{} is not a directory (it's a file or it does not exist)", directory);
      return;
    }

    final List<Path> paths = listAll(directory, reverseOrder());
    log.debug("Files and directories to remove, in order: {}", paths);
    for (final Path path : paths) {
      log.trace("Removing {}", path);
      try {
        Files.delete(path);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  private static void copyToNonExistentDirectory(final Path sourceDir, final Path targetDir) {
    final List<Path> content = listAll(sourceDir, naturalOrder());
    for (final Path path : content) {
      final Path relativePath = sourceDir.relativize(path);
      final Path pathInsideTarget = targetDir.resolve(relativePath);
      if (pathInsideTarget.equals(targetDir)) {
        if (Files.notExists(targetDir)) {
          try {
            Files.createDirectory(targetDir);
          } catch (final IOException e) {
            throw new UncheckedIOException(e);
          }
        }
        continue;
      }

      if (Files.isDirectory(path)) {
        if (Files.notExists(pathInsideTarget)) {
          try {
            Files.createDirectory(pathInsideTarget);
          } catch (final IOException e) {
            throw new UncheckedIOException(e);
          }
        }
      } else {
        try {
          log.debug("Copying {} to {}", path, pathInsideTarget);
          Files.copy(path, pathInsideTarget, StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }
  }

  private static List<Path> listAll(final Path directory, final Comparator<Path> comparator) {
    try (final var stream = Files.walk(directory)) {
      return stream
          .sorted(comparator)
          .toList();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
