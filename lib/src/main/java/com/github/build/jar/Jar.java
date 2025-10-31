package com.github.build.jar;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author noavarice
 * @since 1.0.0
 */
public final class Jar {

  private static final Logger log = LoggerFactory.getLogger(Jar.class);

  private Jar() {
  }

  /**
   * Creates JAR file.
   *
   * @param args JAR creation arguments
   */
  public static void create(final JarArgs args) {
    Objects.requireNonNull(args);
    log.debug("Creating JAR file at {}", args.path());
    try (final OutputStream os = Files.newOutputStream(args.path())) {
      final var jos = new JarOutputStream(os);
      args.contents().forEach((path, content) -> {
        log.trace("Writing {} to JAR {}", path, args.path());
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
}
