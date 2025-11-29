package com.github.build.deps;

import static java.util.stream.Collectors.joining;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Version according to Semantic Versioning 2.0.
 *
 * @author noavarice
 * @since 1.0.0
 */
public final class SemVer implements Comparable<SemVer> {

  public static SemVer parse(final String value) {
    Objects.requireNonNull(value);
    final String trimmedValue = value.strip();
    final int hyphenIndex = trimmedValue.indexOf('-');
    final int plusSignIndex = trimmedValue.indexOf('+');

    final String versionCore;
    String preRelease = null;
    String build = null;
    if (hyphenIndex != -1) {
      if (plusSignIndex != -1) {
        if (plusSignIndex > hyphenIndex) {
          versionCore = trimmedValue.substring(0, hyphenIndex);
          preRelease = trimmedValue.substring(hyphenIndex + 1, plusSignIndex);
          build = trimmedValue.substring(plusSignIndex + 1);
        } else {
          // hyphens after plus sign considered part of build
          versionCore = trimmedValue.substring(0, plusSignIndex);
          build = trimmedValue.substring(plusSignIndex + 1);
        }
      } else {
        versionCore = trimmedValue.substring(0, hyphenIndex);
        preRelease = trimmedValue.substring(hyphenIndex + 1);
      }
    } else if (plusSignIndex != -1) {
      versionCore = trimmedValue.substring(0, plusSignIndex);
      build = trimmedValue.substring(plusSignIndex + 1);
    } else {
      versionCore = trimmedValue;
    }

    final Matcher versionCoreMatcher = VERSION_CORE_FORMAT.matcher(versionCore);
    if (!versionCoreMatcher.matches()) {
      throw new IllegalArgumentException("Invalid version core");
    }

    final String majorStr = versionCoreMatcher.group("major");
    final int major = Integer.parseInt(majorStr);
    if (hasLeadingZeroes(major, majorStr)) {
      throw new IllegalArgumentException("Major part must not contain leading zeroes");
    }

    final String minorStr = versionCoreMatcher.group("minor");
    final int minor = Integer.parseInt(minorStr);
    if (hasLeadingZeroes(minor, minorStr)) {
      throw new IllegalArgumentException("Minor part must not contain leading zeroes");
    }

    final String patchStr = versionCoreMatcher.group("patch");
    final int patch = Integer.parseInt(patchStr);
    if (hasLeadingZeroes(patch, patchStr)) {
      throw new IllegalArgumentException("Patch part must not contain leading zeroes");
    }

    return of(major, minor, patch, preRelease, build);
  }

  private static final Pattern VERSION_CORE_FORMAT = Pattern.compile(
      "^(?<major>\\d+)\\.(?<minor>\\d+)\\.(?<patch>\\d+)$"
  );

  public static SemVer of(
      final int major,
      final int minor,
      final int patch,
      @Nullable final String preRelease,
      @Nullable final String build
  ) {
    Object[] preReleaseParts = null;
    if (preRelease != null) {
      if (!PRE_RELEASE_FORMAT.test(preRelease)) {
        throw new IllegalArgumentException("Unexpected pre-release format");
      }

      final String[] strParts = preRelease.split("\\.", -1);
      preReleaseParts = new Object[strParts.length];
      for (int i = 0; i < strParts.length; i++) {
        final String part = strParts[i];
        try {
          final int number = Integer.parseInt(part);
          if (number < 0) {
            throw new IllegalArgumentException(
                "Numeric pre-release part must be non-negative number"
            );
          }

          if (hasLeadingZeroes(number, part)) {
            throw new IllegalArgumentException(
                "Numeric pre-release part must not contain leading zeroes"
            );
          }

          preReleaseParts[i] = number;
        } catch (final NumberFormatException ignored) {
          preReleaseParts[i] = part;
        }
      }
    }

    return new SemVer(major, minor, patch, preReleaseParts, build);
  }

  private SemVer(
      final int major,
      final int minor,
      final int patch,
      @Nullable final Object[] preRelease,
      @Nullable final String build
  ) {
    if (major < 0) {
      throw new IllegalArgumentException("Major part must be non-negative integer");
    }

    if (minor < 0) {
      throw new IllegalArgumentException("Minor part must be non-negative integer");
    }

    if (patch < 0) {
      throw new IllegalArgumentException("Patch part must be non-negative integer");
    }

    if (preRelease != null) {
      if (preRelease.length == 0) {
        throw new IllegalArgumentException("Empty pre-release parts");
      }

      for (final Object part : preRelease) {
        Objects.requireNonNull(part);
        if (!(part instanceof Integer) && !(part instanceof String)) {
          throw new IllegalArgumentException(
              "Unsupported pre-release part type " + part.getClass());
        }
      }
    }

    // same as pre-release format but without additional checks for numeric identifiers
    if (build != null && !PRE_RELEASE_FORMAT.test(build)) {
      throw new IllegalArgumentException("Unexpected build format");
    }

    this.major = major;
    this.minor = minor;
    this.patch = patch;
    this.preRelease = preRelease;
    this.build = build;
  }

  private static final Predicate<String> PRE_RELEASE_FORMAT = Pattern
      .compile("^[0-9A-Za-z-]+(\\.[0-9A-Za-z-]+)*$")
      .asMatchPredicate();
  private final int major;
  private final int minor;
  private final int patch;
  private final Object @Nullable [] preRelease;
  private final @Nullable String build;


  private static boolean hasLeadingZeroes(final int number, final String numberStr) {
    return number == 0 && numberStr.length() > 1 || number > 0 && numberStr.startsWith("0");
  }

  @Override
  public String toString() {
    final var sb = new StringBuilder()
        .append(major)
        .append('.')
        .append(minor)
        .append('.')
        .append(patch);
    if (preRelease != null) {
      sb.append('-');
      final String preReleaseStr = Stream
          .of(preRelease)
          .map(part -> switch (part) {
            case Integer number -> number.toString();
            case String string -> string;
            default -> throw new IllegalStateException("Unexpected value: " + part);
          })
          .collect(joining("."));
      sb.append(preReleaseStr);
    }

    if (build != null) {
      sb.append('+').append(build);
    }

    return sb.toString();
  }

  @Override
  public int compareTo(final SemVer o) {
    if (major != o.major) {
      return Integer.compare(major, o.major);
    }

    if (minor != o.minor) {
      return Integer.compare(minor, o.minor);
    }

    if (patch != o.patch) {
      return Integer.compare(patch, o.patch);
    }

    if (preRelease != null && o.preRelease != null) {
      for (int i = 0; i < Math.min(preRelease.length, o.preRelease.length); i++) {
        final Object part = preRelease[i];
        final Object otherPart = o.preRelease[i];
        if (part instanceof Integer numericPart) {
          if (otherPart instanceof Integer otherNumericPart) {
            if (!numericPart.equals(otherNumericPart)) {
              return numericPart.compareTo(otherNumericPart);
            }
          } else {
            return -1;
          }
        } else if (otherPart instanceof Integer) {
          return 1;
        } else {
          final var strPart = (String) part;
          final var otherStrPart = (String) otherPart;
          if (!strPart.equals(otherStrPart)) {
            return strPart.compareTo(otherStrPart);
          }
        }
      }

      if (preRelease.length != o.preRelease.length) {
        return Integer.compare(preRelease.length, o.preRelease.length);
      }
    } else if (preRelease != null) {
      return -1;
    } else if (o.preRelease != null) {
      return 1;
    }

    return 0;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof SemVer that) {
      return this.major == that.major &&
          this.minor == that.minor &&
          this.patch == that.patch &&
          Arrays.equals(this.preRelease, that.preRelease);
    }

    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(major, minor, patch, Arrays.hashCode(preRelease));
  }
}
