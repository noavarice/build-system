package com.github.build.deps;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * @author noavarice
 * @since 1.0.0
 */
public sealed interface Version {

  static Version parse(final String value) {
    Objects.requireNonNull(value);
    if (value.isBlank()) {
      throw new IllegalArgumentException();
    }

    final String trimmed = value.strip();
    if (!trimmed.startsWith("[") && !trimmed.startsWith("(")) {
      return new Exact(trimmed);
    }

    if (!trimmed.endsWith("]") && !trimmed.endsWith(")")) {
      throw new IllegalArgumentException();
    }

    final String content = trimmed.substring(1, trimmed.length() - 1);
    if (content.isBlank()) {
      throw new IllegalArgumentException();
    }

    final String[] versions = content.split(",", -1);
    final char firstChar = trimmed.charAt(0);
    final char lastChar = trimmed.charAt(trimmed.length() - 1);
    return switch (versions.length) {
      case 1 -> {
        if (firstChar != '[' || lastChar != ']') {
          throw new IllegalArgumentException("Range with one version can only be closed");
        }

        final String version = versions[0];
        yield new Range(
            new Range.Bound(version, true),
            new Range.Bound(version, true)
        );
      }
      case 2 -> {
        final String left = versions[0];
        final Range.Bound lower = switch (firstChar) {
          // Closed bound must contain version value
          case '[' -> new Range.Bound(left, true);
          case '(' -> left == null || left.isBlank() ? null : new Range.Bound(left, false);
          default -> throw new IllegalStateException("Unexpected value: " + firstChar);
        };

        final String right = versions[1];
        final Range.Bound upper = switch (lastChar) {
          case ']' -> new Range.Bound(right, true);
          case ')' -> right == null || right.isBlank() ? null : new Range.Bound(right, false);
          default -> throw new IllegalStateException("Unexpected value: " + lastChar);
        };

        yield new Range(lower, upper);
      }
      default -> throw new IllegalArgumentException();
    };
  }

  record Exact(String value) implements Version {

    public Exact {
      Objects.requireNonNull(value);
      if (value.isBlank()) {
        throw new IllegalArgumentException();
      }

      value = value.strip();
    }
  }

  record Range(@Nullable Bound lower, @Nullable Bound upper) implements Version {

    public Range {
      if (lower == null && upper == null) {
        throw new IllegalStateException();
      }
    }

    record Bound(String value, boolean including) {

      public Bound {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
          throw new IllegalArgumentException();
        }

        value = value.strip();
      }
    }
  }
}
