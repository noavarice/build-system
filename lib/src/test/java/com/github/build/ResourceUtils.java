package com.github.build;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Utilities to work with resources.
 *
 * @author noavarice
 * @since 1.0.0
 */
public final class ResourceUtils {

  private ResourceUtils() {
  }

  public static String readString(final String resourcePath) {
    final byte[] bytes = read(resourcePath);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  public static byte[] read(final String resourcePath) {
    try (final var is = CompileServiceTest.class.getResourceAsStream(resourcePath)) {
      return Objects.requireNonNull(is).readAllBytes();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
