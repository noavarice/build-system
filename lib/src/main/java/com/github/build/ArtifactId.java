package com.github.build;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @author noavarice
 */
public record ArtifactId(String value) {

  private static final Pattern VALID_ARTIFACT_ID_PATTERN =
      Pattern.compile("^[a-z0-9-]+$", Pattern.CANON_EQ);

  public ArtifactId {
    Objects.requireNonNull(value);
    if (value.isBlank()) {
      throw new IllegalArgumentException("Must not be empty");
    }

    value = value.strip();
    if (!VALID_ARTIFACT_ID_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "ArtifactId must contain only lowercase letters, digits, and hyphens: " + value
      );
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
