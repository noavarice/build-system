package com.github.build.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Maven local repository backed by the file system. Artifacts are stored under
 * {@code {localRepository}/{repositoryId}/...} and written atomically.
 *
 * @author noavarice
 * @since 1.0.0
 */
@Component
public final class LocalRepositoryStore {

  private static final Logger log = LoggerFactory.getLogger(LocalRepositoryStore.class);

  private final Path root;

  public LocalRepositoryStore(final RepositoryProxyProperties properties) {
    this.root = Objects.requireNonNull(properties).localRepository().toAbsolutePath().normalize();
    try {
      Files.createDirectories(root);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create local repository " + root, e);
    }

    log.info("Local repository: {}", root);
  }

  /**
   * Resolves the file system path for the given artifact without touching the file system.
   *
   * @param repositoryId Repository ID
   * @param artifactPath Repository-relative artifact path
   * @return Artifact path inside the local repository
   * @throws IllegalArgumentException If the path escapes the repository
   */
  public Path path(final String repositoryId, final String artifactPath) {
    final Path base = root.resolve(repositoryId).normalize();
    final Path resolved = base.resolve(artifactPath).normalize();
    if (!resolved.startsWith(base)) {
      throw new IllegalArgumentException("Artifact path escapes local repository");
    }

    return resolved;
  }

  public boolean present(final Path path) {
    return Files.isRegularFile(path);
  }

  /**
   * Stores artifact content at the target path atomically.
   *
   * @param target  Target file
   * @param content Artifact content, consumed fully and not closed
   */
  public void store(final Path target, final InputStream content) {
    final Path directory = target.getParent();
    try {
      Files.createDirectories(directory);
      final Path temp = Files.createTempFile(
          directory,
          target.getFileName().toString(),
          ".part"
      );
      try {
        try (final OutputStream out = Files.newOutputStream(
            temp,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING
        )) {
          content.transferTo(out);
        }
        move(temp, target);
      } catch (final IOException | RuntimeException e) {
        Files.deleteIfExists(temp);
        throw e;
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void move(final Path source, final Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (final AtomicMoveNotSupportedException e) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
