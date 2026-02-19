package com.github.build.jar;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author noavarice
 * @since 1.0.0
 */
public final class JarService {

  private static final Logger log = LoggerFactory.getLogger(JarService.class);

  /**
   * Creates JAR file.
   *
   * @param args JAR creation arguments
   */
  public void create(final JarArgs args) {
    Objects.requireNonNull(args);
    log.debug("Creating JAR file at {}", args.path());
    try (final OutputStream os = Files.newOutputStream(args.path())) {
      final JarOutputStream jos;
      if (args.manifest() != null) {
        jos = new JarOutputStream(os, args.manifest().toManifest());
      } else {
        jos = new JarOutputStream(os);
      }

      final var writtenDirectories = new HashSet<Path>();
      args.contents().forEach((path, content) -> {
        log.trace("Writing {} to JAR {}", path, args.path());
        writeJarEntry(jos, path, content, writtenDirectories);
      });
      jos.finish();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void writeJarEntry(
      final JarOutputStream jos,
      final Path path,
      final JarArgs.Content content,
      final Set<Path> writtenDirectories
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

    final var normalized = path.normalize();

    // writing intermediate directories
    final Path parent = normalized.getParent();
    if (parent != null) {
      Path intermediate = Path.of("");
      for (final Path segment : parent) {
        intermediate = intermediate.resolve(segment);
        if (!writtenDirectories.contains(intermediate)) {
          writtenDirectories.add(intermediate);
          final var entry = new ZipEntry(intermediate.toString() + '/');
          try {
            jos.putNextEntry(entry);
            jos.closeEntry();
          } catch (final IOException e) {
            throw new UncheckedIOException(e);
          }
        }
      }
    }

    final var entry = new ZipEntry(normalized.toString());
    if (content instanceof JarArgs.Content.File file) {
      setFileAttributes(entry, file.path());
    }

    try {
      jos.putNextEntry(entry);
      jos.write(bytes);
      jos.closeEntry();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void setFileAttributes(final ZipEntry entry, final Path path) {
    final BasicFileAttributes attributes;
    try {
      attributes = Files.readAttributes(path, BasicFileAttributes.class);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    entry.setCreationTime(attributes.creationTime());
    entry.setLastModifiedTime(attributes.lastModifiedTime());
    entry.setLastAccessTime(attributes.lastAccessTime());
  }
}
