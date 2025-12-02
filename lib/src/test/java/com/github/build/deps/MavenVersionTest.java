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
class MavenVersionTest {

  @DisplayName("Parsing tests")
  @Nested
  class Parsing {

    @DisplayName("Parsing exact version works")
    @Test
    void parsingExactVersionWorks() {
      final MavenVersion parsed = MavenVersion.parse("1.0.0");
      assertEquals(new MavenVersion.Exact("1.0.0"), parsed);
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
      assertThrows(IllegalArgumentException.class, () -> MavenVersion.parse(invalidRange));
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
      final MavenVersion parsed = MavenVersion.parse(value);
      assertEquals(
          new MavenVersion.Range(
              new MavenVersion.Range.Bound("1.0.0", true),
              new MavenVersion.Range.Bound("1.0.0", true)
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
      final MavenVersion parsed = MavenVersion.parse(value);
      assertEquals(
          new MavenVersion.Range(
              new MavenVersion.Range.Bound("1.0.0", true),
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
      final MavenVersion parsed = MavenVersion.parse(value);
      assertEquals(
          new MavenVersion.Range(
              null,
              new MavenVersion.Range.Bound("1.0.0", true)
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
      final MavenVersion parsed = MavenVersion.parse(value);
      assertEquals(
          new MavenVersion.Range(
              new MavenVersion.Range.Bound("1.0.0", true),
              new MavenVersion.Range.Bound("1.1.0", false)
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
      final MavenVersion parsed = MavenVersion.parse("(1.0.0,1.1.0]");
      assertEquals(
          new MavenVersion.Range(
              new MavenVersion.Range.Bound("1.0.0", false),
              new MavenVersion.Range.Bound("1.1.0", true)
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
      final MavenVersion parsed = MavenVersion.parse("[1.0.0,1.1.0]");
      assertEquals(
          new MavenVersion.Range(
              new MavenVersion.Range.Bound("1.0.0", true),
              new MavenVersion.Range.Bound("1.1.0", true)
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
      final MavenVersion parsed = MavenVersion.parse("(1.0.0,1.1.0)");
      assertEquals(
          new MavenVersion.Range(
              new MavenVersion.Range.Bound("1.0.0", false),
              new MavenVersion.Range.Bound("1.1.0", false)
          ),
          parsed
      );
    }
  }
}
