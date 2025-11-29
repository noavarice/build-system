package com.github.build.deps;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.support.ParameterDeclarations;

/**
 * @author noavarice
 * @since 1.0.0
 */
@DisplayName("SemVer domain object tests")
class SemVerTest {

  @DisplayName("Check validations")
  @Nested
  class Validation {

    @DisplayName("Check negative major part disallowed")
    @Test
    void testNegativeMajorPartDisallowed() {
      assertThrows(IllegalArgumentException.class, () -> SemVer.of(-1, 2, 3, null, null));
    }

    @DisplayName("Check negative minor part disallowed")
    @Test
    void testNegativeMinorPartDisallowed() {
      assertThrows(IllegalArgumentException.class, () -> SemVer.of(1, -2, 3, null, null));
    }

    @DisplayName("Check negative patch part disallowed")
    @Test
    void testNegativePatchPartDisallowed() {
      assertThrows(IllegalArgumentException.class, () -> SemVer.of(1, 2, -3, null, null));
    }

    @DisplayName("Check invalid pre-release parts disallowed")
    @ParameterizedTest
    @ValueSource(strings = {
        "", ".alpha", "alpha.", "alpha.beta.", "alpha..gamma", // empty parts
        "!", // disallowed characters
        "00", "01", "01.alpha", "alpha.01", "alpha.1.01", // leading zeroes
        "-1", "alpha.-1", "alpha.1.-1", // negative numeric identifiers
    })
    void testInvalidPreReleasePartsDisallowed(final String preRelease) {
      assertThrows(IllegalArgumentException.class, () -> SemVer.of(1, 2, 3, preRelease, null));
    }

    @DisplayName("Check invalid build parts disallowed")
    @ParameterizedTest
    @ValueSource(strings = {
        "", ".alpha", "alpha.", "alpha.beta.", "alpha..gamma", // empty parts
        "!", // disallowed characters
    })
    void testInvalidBuildPartsDisallowed(final String build) {
      assertThrows(IllegalArgumentException.class, () -> SemVer.of(1, 2, 3, null, build));
    }

    @DisplayName("Check initial development version allowed")
    @Test
    void testInitialDevelopmentVersionAllowed() {
      assertDoesNotThrow(() -> SemVer.of(0, 1, 0, null, null));
    }

    @DisplayName("Check initial version allowed")
    @Test
    void testFirstReleaseVersionAllowed() {
      assertDoesNotThrow(() -> SemVer.of(1, 0, 0, null, null));
    }

    @DisplayName("Check allowed pre-release values")
    @ParameterizedTest
    @ValueSource(strings = {
        "alpha",
        "0alpha",
        "alpha.0",
        "alpha.1",
        "alpha.1.0",
        "alpha.1.1",
    })
    void testAllowedPreReleaseValues(final String preRelease) {
      assertDoesNotThrow(() -> SemVer.of(1, 0, 0, preRelease, null));
    }

    @DisplayName("Check allowed build values")
    @ParameterizedTest
    @ValueSource(strings = {
        "alpha",
        "0alpha",
        "alpha.0",
        "alpha.1",
        "alpha.-1",
        "alpha.1.0",
        "alpha.1.1",
        "alpha.1.-1",
    })
    void testAllowedBuildValues(final String build) {
      assertDoesNotThrow(() -> SemVer.of(1, 0, 0, null, build));
    }
  }

  @DisplayName("Check string representations")
  @Nested
  class ToString {

    @DisplayName("Check version core string representation")
    @Test
    void testCoreString() {
      final var value = SemVer.of(1, 2, 3, null, null);
      final String actual = value.toString();
      assertEquals("1.2.3", actual);
    }

    @DisplayName("Check version with pre-release string representation")
    @Test
    void testWithPreReleaseString() {
      final var value = SemVer.of(1, 2, 3, "alpha", null);
      final String actual = value.toString();
      assertEquals("1.2.3-alpha", actual);
    }

    @DisplayName("Check version with build string representation")
    @Test
    void testWithBuildString() {
      final var value = SemVer.of(1, 2, 3, null, "build20251129155424");
      final String actual = value.toString();
      assertEquals("1.2.3+build20251129155424", actual);
    }

    @DisplayName("Check full version string representation")
    @Test
    void testFullString() {
      final var value = SemVer.of(1, 2, 3, "alpha", "build20251129155424");
      final String actual = value.toString();
      assertEquals("1.2.3-alpha+build20251129155424", actual);
    }
  }

  @DisplayName("Check parsing")
  @Nested
  class Parsing {

    @DisplayName("Check invalid value parsing fails")
    @ParameterizedTest(name = "Check {0} parsing fails")
    @ValueSource(strings = {
        "", " ",
        "1", "1.2", // incomplete version core
        "00.1.0", "01.0.0", "1.00.0", "1.0.00", // leading zeroes
    })
    void testInvalidValueParsingFails(final String value) {
      assertThrows(IllegalArgumentException.class, () -> SemVer.parse(value));
    }

    @DisplayName("Check valid value parsing works")
    @ParameterizedTest(name = "Check {0} parsing works")
    @ArgumentsSource(ValidValueParsingArgumentProvider.class)
    void testValidValueParsingWorks(final String value, final SemVer expected) {
      final SemVer actual = SemVer.parse(value);
      assertEquals(expected, actual);
    }

    private static final class ValidValueParsingArgumentProvider implements ArgumentsProvider {

      @Override
      public Stream<? extends Arguments> provideArguments(
          final ParameterDeclarations parameters,
          final ExtensionContext context
      ) {
        return Stream.of(
            Arguments.of(
                " 0.1.0", // starting spaces ignored
                SemVer.of(0, 1, 0, null, null)
            ),
            Arguments.of(
                "0.1.0 ", // ending spaces ignored
                SemVer.of(0, 1, 0, null, null)
            ),
            Arguments.of(
                " 0.1.0 ", // both end spaces ignored
                SemVer.of(0, 1, 0, null, null)
            ),
            Arguments.of(
                "0.1.0",
                SemVer.of(0, 1, 0, null, null)
            ),
            Arguments.of(
                "1.0.0",
                SemVer.of(1, 0, 0, null, null)
            ),
            Arguments.of(
                "1.0.0-alpha",
                SemVer.of(1, 0, 0, "alpha", null)
            ),
            Arguments.of(
                "1.0.0-alpha.beta",
                SemVer.of(1, 0, 0, "alpha.beta", null)
            ),
            Arguments.of(
                "1.0.0-alpha-something.beta", // pre-release contains hyphens
                SemVer.of(1, 0, 0, "alpha-something.beta", null)
            ),
            Arguments.of(
                "1.0.0-alpha-something.beta-something", // pre-release contains hyphens
                SemVer.of(1, 0, 0, "alpha-something.beta-something", null)
            ),
            Arguments.of(
                "1.0.0+build-20251129-155424",
                SemVer.of(1, 0, 0, null, "build-20251129-155424")
            ),
            Arguments.of(
                "1.0.0-alpha.beta+build-20251129-155424",
                SemVer.of(1, 0, 0, "alpha.beta", "build-20251129-155424")
            ),
            Arguments.of(
                "1.0.0-alpha-something.beta-something+build.2025-11-29.155424",
                SemVer.of(1, 0, 0, "alpha-something.beta-something", "build.2025-11-29.155424")
            )
        );
      }
    }
  }

  @DisplayName("Comparison tests")
  @Nested
  class Comparison {

    @DisplayName("Compare values")
    @Test
    void testComparison() {
      final List<SemVer> versions = List.of(
          SemVer.parse("2.1.1-beta.2"),
          SemVer.parse("2.1.0"),
          SemVer.parse("2.0.0"),
          SemVer.parse("2.1.1-alpha.beta"),
          SemVer.parse("2.1.1"),
          SemVer.parse("1.0.0"),
          SemVer.parse("2.1.1-beta.11"),
          SemVer.parse("2.1.1-alpha.1"),
          SemVer.parse("2.1.1-beta"),
          SemVer.parse("2.1.1-alpha"),
          SemVer.parse("2.1.0"),
          SemVer.parse("2.1.1-alpha")
      );
      final List<SemVer> expected = List.of(
          SemVer.parse("1.0.0"),
          SemVer.parse("2.0.0"),
          SemVer.parse("2.1.0"),
          SemVer.parse("2.1.0"),
          SemVer.parse("2.1.1-alpha"),
          SemVer.parse("2.1.1-alpha"),
          SemVer.parse("2.1.1-alpha.1"),
          SemVer.parse("2.1.1-alpha.beta"),
          SemVer.parse("2.1.1-beta"),
          SemVer.parse("2.1.1-beta.2"),
          SemVer.parse("2.1.1-beta.11"),
          SemVer.parse("2.1.1")
      );
      final List<SemVer> actual = versions
          .stream()
          .sorted()
          .toList();
      assertEquals(expected, actual);
    }
  }
}
