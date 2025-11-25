package com.github.build.deps;

import com.github.build.util.PathUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author noavarice
 * TODO: follow .m2 local repository structure
 */
public final class LocalRepository {

  private static final Logger log = LoggerFactory.getLogger(LocalRepository.class);

  private final Path basePath;

  private final Map<String, String> idToMessageDigest;

  public LocalRepository(final Path basePath, final Map<String, String> idToMessageDigest) {
    this.idToMessageDigest = idToMessageDigest;
    Objects.requireNonNull(basePath);
    PathUtils.checkAbsolute(basePath);
    PathUtils.checkDirectory(basePath);
    this.basePath = basePath;
  }

  public Path saveJar(final GroupArtifactVersion gav, final byte[] bytes) {
    log.debug("Saving {} JAR to local repository", gav);
    final Path dir = basePath
        .resolve(gav.groupId().replace('.', '/'))
        .resolve(gav.artifactId())
        .resolve(gav.version());
    final String fileName = gav.artifactId() + '-' + gav.version();
    final Path jarPath = dir.resolve(fileName + ".jar");
    try {
      Files.createDirectories(dir);
      Files.write(jarPath, bytes, StandardOpenOption.CREATE);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    for (final String id : idToMessageDigest.keySet()) {
      final Path hashFilePath = dir.resolve(fileName + '.' + id);

      final String algo = Objects.requireNonNull(idToMessageDigest.get(id));
      final MessageDigest messageDigest;
      try {
        messageDigest = MessageDigest.getInstance(algo);
      } catch (final NoSuchAlgorithmException e) {
        throw new IllegalStateException(e);
      }

      final byte[] hashBytes = messageDigest.digest(bytes);
      final String hash = HexFormat.of().formatHex(hashBytes);

      try {
        Files.writeString(hashFilePath, hash);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    return jarPath;
  }

  /**
   * Checks if artifact JAR is present in file system.
   *
   * @param gav Artifact
   * @return True if JAR exists, false otherwise
   */
  public boolean jarPresent(final GroupArtifactVersion gav) {
    Objects.requireNonNull(gav);
    final Path jarPath = getPathInternal(gav);
    return Files.isRegularFile(jarPath);
  }

  /**
   * Constructs JAR file path for artifact without checking file presence.
   *
   * @param gav Artifact
   * @return JAR path, never null
   */
  public Path getPath(final GroupArtifactVersion gav) {
    Objects.requireNonNull(gav);
    return getPathInternal(gav);
  }

  private Path getPathInternal(final GroupArtifactVersion gav) {
    return basePath
        .resolve(gav.groupId().replace('.', '/'))
        .resolve(gav.artifactId())
        .resolve(gav.version())
        .resolve(gav.artifactId() + '-' + gav.version() + ".jar");
  }
}
