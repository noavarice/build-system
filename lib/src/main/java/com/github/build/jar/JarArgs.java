package com.github.build.jar;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * @param path     Path where JAR file will be created, must be absolute
 * @param contents Map from an entry path (always relative) inside JAR to an entry value
 * @author noavarice
 * @since 1.0.0
 */
public record JarArgs(Path path, Map<Path, Content> contents, @Nullable JarManifest manifest) {

  public JarArgs {
    path = Objects.requireNonNull(path).normalize().toAbsolutePath();

    Objects.requireNonNull(contents);
    contents.forEach((p, c) -> {
      if (p.isAbsolute()) {
        throw new IllegalArgumentException(
            "JAR paths must be relative while " + p + " is absolute"
        );
      }
    });
    contents = Map.copyOf(contents);
  }

  public sealed interface Content {

    record Bytes(byte[] value) implements Content {

      public Bytes {
        Objects.requireNonNull(value);
      }
    }

    record File(Path path) implements Content {

      public File {
        Objects.requireNonNull(path);
        if (!path.isAbsolute()) {
          throw new IllegalArgumentException("Must be absolute file path");
        }
      }
    }
  }
}
