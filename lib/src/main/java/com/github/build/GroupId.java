package com.github.build;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @author noavarice
 */
public record GroupId(String value) {

  private static final Pattern VALID_GROUP_ID_PATTERN =
      Pattern.compile("^[a-zA-Z][a-zA-Z0-9]*(\\.[a-zA-Z][a-zA-Z0-9]*)*$");

  private static final int MAX_SEGMENT_LENGTH = 63;
  private static final int MAX_TOTAL_LENGTH = 255;

  public GroupId {
    Objects.requireNonNull(value);
    value = value.strip();
    if (value.isBlank()) {
      throw new IllegalArgumentException("GroupId must not be empty");
    }

    if (value.length() > MAX_TOTAL_LENGTH) {
      throw new IllegalArgumentException(
          "GroupId must not exceed " + MAX_TOTAL_LENGTH + " characters"
      );
    }
    if (!VALID_GROUP_ID_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "GroupId must follow Java package naming convention: " +
              "segments must start with a letter, contain only letters and digits, " +
              "and be separated by dots: " + value
      );
    }

    // Additional validation for each segment
    String[] segments = value.split("\\.");
    for (String segment : segments) {
      if (segment.length() > MAX_SEGMENT_LENGTH) {
        throw new IllegalArgumentException(
            "Each groupId segment must not exceed " + MAX_SEGMENT_LENGTH +
                " characters. Found segment '" + segment + "' with length " +
                segment.length()
        );
      }
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
