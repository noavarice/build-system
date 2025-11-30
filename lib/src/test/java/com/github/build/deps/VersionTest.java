package com.github.build.deps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * @author noavarice
 * @since 1.0.0
 */
@DisplayName("Version domain object test")
class VersionTest {

  @DisplayName("Parsing tests")
  @Nested
  class Parsing {

    @DisplayName("Parsing exact version works")
    @Test
    void parsingExactVersionWorks() {
      final Version parsed = Version.parse("1.0.0");
      assertEquals(new Version.Exact("1.0.0"), parsed);
    }

    @DisplayName("Parsing invalid range fails")
    @ParameterizedTest
    @ValueSource(strings = {
        "(1.0.0)",
        "[1.0.0)",
        "(1.0.0]",
        "(1.0.0",
        "[1.0.0",
    })
    void parsingExactVersionWorks(final String invalidRange) {
      assertThrows(IllegalArgumentException.class, () -> Version.parse(invalidRange));
    }

    @DisplayName("Parsing single version range works")
    @ParameterizedTest
    @ValueSource(strings = {
        "[1.0.0]",
        "[ 1.0.0]",
        "[1.0.0 ]",
        "[ 1.0.0 ]",
    })
    void parsingSingleVersionRangeWorks(final String value) {
      final Version parsed = Version.parse(value);
      assertEquals(
          new Version.Range(
              new Version.Range.Bound("1.0.0", true),
              new Version.Range.Bound("1.0.0", true)
          ),
          parsed
      );
    }

    @DisplayName("Parsing right half-open unbounded range works")
    @ParameterizedTest
    @ValueSource(strings = {
        "[1.0.0,)",
        "[1.0.0, )",
        "[ 1.0.0 ,)",
        "[ 1.0.0 , )",
    })
    void parsingRightHalfOpenUnboundedRangeWorks(final String value) {
      final Version parsed = Version.parse(value);
      assertEquals(
          new Version.Range(
              new Version.Range.Bound("1.0.0", true),
              null
          ),
          parsed
      );
    }

    @DisplayName("Parsing left half-open unbounded range works")
    @ParameterizedTest
    @ValueSource(strings = {
        "(,1.0.0]",
        "( ,1.0.0]",
        "(, 1.0.0 ]",
        "( , 1.0.0 ]",
    })
    void parsingLeftHalfOpenUnboundedRangeWorks(final String value) {
      final Version parsed = Version.parse(value);
      assertEquals(
          new Version.Range(
              null,
              new Version.Range.Bound("1.0.0", true)
          ),
          parsed
      );
    }

    @DisplayName("Parsing right half-open bounded range works")
    @ParameterizedTest
    @ValueSource(strings = {
        "[1.0.0,1.1.0)",
        "[1.0.0, 1.1.0 )",
        "[ 1.0.0 ,1.1.0)",
        "[ 1.0.0 , 1.1.0 )",
    })
    void parsingRightHalfOpenBoundedRangeWorks(final String value) {
      final Version parsed = Version.parse(value);
      assertEquals(
          new Version.Range(
              new Version.Range.Bound("1.0.0", true),
              new Version.Range.Bound("1.1.0", false)
          ),
          parsed
      );
    }

    @DisplayName("Parsing left half-open bounded range works")
    @ParameterizedTest
    @ValueSource(strings = {
        "(1.0.0,1.1.0]",
        "( 1.0.0 ,1.1.0]",
        "(1.0.0, 1.1.0 ]",
        "( 1.0.0 , 1.1.0 ]",
    })
    void parsingLeftHalfOpenBoundedRangeWorks() {
      final Version parsed = Version.parse("(1.0.0,1.1.0]");
      assertEquals(
          new Version.Range(
              new Version.Range.Bound("1.0.0", false),
              new Version.Range.Bound("1.1.0", true)
          ),
          parsed
      );
    }

    @DisplayName("Parsing closed range works")
    @ParameterizedTest
    @ValueSource(strings = {
        "[1.0.0,1.1.0]",
        "[ 1.0.0 ,1.1.0]",
        "[1.0.0, 1.1.0 ]",
        "[ 1.0.0 , 1.1.0 ]",
    })
    void parsingClosedRangeWorks() {
      final Version parsed = Version.parse("[1.0.0,1.1.0]");
      assertEquals(
          new Version.Range(
              new Version.Range.Bound("1.0.0", true),
              new Version.Range.Bound("1.1.0", true)
          ),
          parsed
      );
    }

    @DisplayName("Parsing open range works")
    @ParameterizedTest
    @ValueSource(strings = {
        "(1.0.0,1.1.0)",
        "( 1.0.0 ,1.1.0)",
        "(1.0.0, 1.1.0 )",
        "( 1.0.0 , 1.1.0 )",
    })
    void parsingOpenRangeWorks() {
      final Version parsed = Version.parse("(1.0.0,1.1.0)");
      assertEquals(
          new Version.Range(
              new Version.Range.Bound("1.0.0", false),
              new Version.Range.Bound("1.1.0", false)
          ),
          parsed
      );
    }
  }
}
