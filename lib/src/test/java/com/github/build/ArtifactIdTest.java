package com.github.build;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * @author noavarice
 */
@DisplayName("ArtifactId tests")
class ArtifactIdTest {

  @Test
  @DisplayName("Should create valid artifactId with lowercase letters only")
  void shouldCreateWithLowercaseLetters() {
    ArtifactId artifactId = new ArtifactId("springboot");
    assertEquals("springboot", artifactId.value());
  }

  @Test
  @DisplayName("Should create valid artifactId with digits only")
  void shouldCreateWithDigits() {
    ArtifactId artifactId = new ArtifactId("12345");
    assertEquals("12345", artifactId.value());
  }

  @Test
  @DisplayName("Should create valid artifactId with hyphens only")
  void shouldCreateWithHyphens() {
    ArtifactId artifactId = new ArtifactId("my-library");
    assertEquals("my-library", artifactId.value());
  }

  @Test
  @DisplayName("Should create valid artifactId with combination of lowercase letters and digits")
  void shouldCreateWithLettersAndDigits() {
    ArtifactId artifactId = new ArtifactId("log4j2");
    assertEquals("log4j2", artifactId.value());
  }

  @Test
  @DisplayName("Should create valid artifactId with combination of letters, digits, and hyphens")
  void shouldCreateWithAllValidCharacters() {
    ArtifactId artifactId = new ArtifactId("my-awesome-lib-2");
    assertEquals("my-awesome-lib-2", artifactId.value());
  }

  @Test
  @DisplayName("Should create valid artifactId with single character")
  void shouldCreateWithSingleCharacter() {
    ArtifactId artifactId = new ArtifactId("a");
    assertEquals("a", artifactId.value());
  }

  @Test
  @DisplayName("Should create valid artifactId with maximum reasonable length")
  void shouldCreateWithReasonableLength() {
    String longArtifactId = "a".repeat(100); // Assuming no explicit length limit
    ArtifactId artifactId = new ArtifactId(longArtifactId);
    assertEquals(longArtifactId, artifactId.value());
  }

  @Test
  @DisplayName("Should throw exception for null or empty artifactId")
  void shouldThrowForNull() {
    assertThrows(
        NullPointerException.class,
        () -> new ArtifactId(null),
        "ArtifactId must not be null"
    );
  }

  @Test
  @DisplayName("Should throw exception for null or empty artifactId")
  void shouldThrowForEmpty() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ArtifactId(""),
        "ArtifactId must not be empty"
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "SpringBoot",           // Uppercase letters
      "my_artifact",          // Underscore
      "my.artifact",          // Dot
      "my+artifact",          // Plus sign
      "my@artifact",          // At symbol
      "my#artifact",          // Hash
      "my$artifact",          // Dollar sign
      "my%artifact",          // Percent
      "my^artifact",          // Caret
      "my&artifact",          // Ampersand
      "my*artifact",          // Asterisk
      "my(artifact",          // Parenthesis
      "my)artifact",          // Parenthesis
      "my=artifact",          // Equals
      "my+artifact",          // Plus
      "my/artifact",          // Forward slash
      "my\\artifact",         // Backslash
      "my:artifact",          // Colon
      "my;artifact",          // Semicolon
      "my\"artifact",         // Double quote
      "my'artifact",          // Single quote
      "my<artifact",          // Less than
      "my>artifact",          // Greater than
      "my?artifact",          // Question mark
      "my|artifact",          // Pipe
      "my~artifact",          // Tilde
      "my`artifact",          // Backtick
      "my,artifact",          // Comma
      "my{artifact}",         // Curly braces
      "my[artifact]",         // Square brackets
      "my artifact",          // Space
      "arti\tfact",           // Tab
      "arti\nfact",           // Newline
      "arti\rfact",           // Carriage return
      "café",                 // Accented character
      "über",                 // Umlaut
      "中文",                  // Chinese characters
      "artifact with spaces"  // Multiple spaces
  })
  @DisplayName("Should throw exception for artifactId with invalid characters")
  void shouldThrowForInvalidCharacters(String invalidValue) {
    assertThrows(IllegalArgumentException.class, () -> new ArtifactId(invalidValue),
        "ArtifactId must contain only lowercase letters, digits, and hyphens");
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "-start-with-hyphen",   // Starting with hyphen
      "end-with-hyphen-",     // Ending with hyphen
      "double--hyphen",       // Double hyphen
      "---triple-hyphen"      // Multiple consecutive hyphens
  })
  @DisplayName("Should allow artifactId with hyphens at start, end, or consecutive")
  void shouldAllowHyphensInAnyPosition(String value) {
    // Note: While these are technically valid by the character rules,
    // they might be considered poor practice. Test passes if only character
    // validation is enforced.
    ArtifactId artifactId = new ArtifactId(value);
    assertEquals(value, artifactId.value());
  }

  @Test
  @DisplayName("Should preserve exact value after creation")
  void shouldPreserveExactValue() {
    String expected = "valid-artifact-123";
    ArtifactId artifactId = new ArtifactId(expected);
    assertEquals(expected, artifactId.value());
  }

  @Test
  @DisplayName("Should handle hyphen-only string")
  void shouldHandleHyphenOnly() {
    ArtifactId artifactId = new ArtifactId("-");
    assertEquals("-", artifactId.value());
  }

  @Test
  @DisplayName("Should implement equals and hashCode correctly")
  void shouldImplementEqualsAndHashCode() {
    ArtifactId id1 = new ArtifactId("my-lib");
    ArtifactId id2 = new ArtifactId("my-lib");
    ArtifactId id3 = new ArtifactId("different-lib");

    assertEquals(id1, id2);
    assertNotEquals(id1, id3);
    assertEquals(id1.hashCode(), id2.hashCode());
    assertNotEquals(id1.hashCode(), id3.hashCode());
  }

  @Test
  @DisplayName("Should implement toString correctly")
  void shouldImplementToString() {
    ArtifactId artifactId = new ArtifactId("my-lib");
    assertTrue(artifactId.toString().contains("my-lib"));
  }

  @Test
  @DisplayName("Should validate common real-world artifactIds")
  void shouldValidateRealWorldArtifactIds() {
    // Common Maven artifactIds from the ecosystem
    assertDoesNotThrow(() -> new ArtifactId("spring-core"));
    assertDoesNotThrow(() -> new ArtifactId("junit-jupiter-api"));
    assertDoesNotThrow(() -> new ArtifactId("log4j-core"));
    assertDoesNotThrow(() -> new ArtifactId("maven-compiler-plugin"));
    assertDoesNotThrow(() -> new ArtifactId("jackson-databind"));
    assertDoesNotThrow(() -> new ArtifactId("hibernate-core"));
    assertDoesNotThrow(() -> new ArtifactId("slf4j-api"));
    assertDoesNotThrow(() -> new ArtifactId("guava"));
    assertDoesNotThrow(() -> new ArtifactId("commons-lang3"));
    assertDoesNotThrow(() -> new ArtifactId("postgresql-42"));
  }
}
