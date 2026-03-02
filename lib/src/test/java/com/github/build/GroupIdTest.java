package com.github.build;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * @author noavarice
 */
@DisplayName("GroupId Record Tests")
class GroupIdTest {

  @Test
  @DisplayName("Should create valid groupId with simple package name")
  void shouldCreateWithSimplePackage() {
    GroupId groupId = new GroupId("com");
    assertEquals("com", groupId.value());
  }

  @Test
  @DisplayName("Should create valid groupId with multiple segments")
  void shouldCreateWithMultipleSegments() {
    GroupId groupId = new GroupId("org.apache.maven");
    assertEquals("org.apache.maven", groupId.value());
  }

  @Test
  @DisplayName("Should create valid groupId with digits after first character")
  void shouldCreateWithDigitsInSegments() {
    GroupId groupId = new GroupId("com.example2.project3");
    assertEquals("com.example2.project3", groupId.value());
  }

  @Test
  @DisplayName("Should create valid groupId with uppercase letters")
  void shouldCreateWithUppercaseLetters() {
    GroupId groupId = new GroupId("com.MyCompany.Project");
    assertEquals("com.MyCompany.Project", groupId.value());
  }

  @Test
  @DisplayName("Should create valid groupId with single character segments")
  void shouldCreateWithSingleCharacterSegments() {
    GroupId groupId = new GroupId("a.b.c");
    assertEquals("a.b.c", groupId.value());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "com.google",
      "org.springframework.boot",
      "io.netty",
      "net.sf.jasperreports",
      "javax.servlet",
      "com.fasterxml.jackson.core",
      "org.apache.commons.lang3",
      "com.my-company",        // Note: Hyphens are NOT allowed in groupId
      "com.company_1"          // Note: Underscores are NOT allowed
  })
  @DisplayName("Should validate common real-world groupIds")
  void shouldValidateRealWorldGroupIds(String validGroupId) {
    // Skip hyphen and underscore examples as they're not valid
    if (!validGroupId.contains("-") && !validGroupId.contains("_")) {
      assertDoesNotThrow(() -> new GroupId(validGroupId));
    }
  }

  @DisplayName("Should throw exception for null or blank groupId")
  @Test
  void shouldThrowForNull() {
    assertThrows(
        NullPointerException.class,
        () -> new GroupId(null),
        "GroupId must not be null or empty"
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "\t", "\n"})
  @DisplayName("Should throw exception for null or blank groupId")
  void shouldThrowForEmptyOrBlank(String invalidValue) {
    assertThrows(IllegalArgumentException.class, () -> new GroupId(invalidValue),
        "GroupId must not be null or empty");
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "123com",                    // Starts with digit
      "com.123example",             // Segment starts with digit
      "com-example",                // Contains hyphen
      "com_example",                // Contains underscore
      "com..example",               // Double dot
      ".com.example",                // Starts with dot
      "com.example.",                // Ends with dot
      "com.example..project",        // Double dot in middle
      "com/example",                 // Contains slash
      "com\\example",                 // Contains backslash
      "com example",                  // Contains space
      "com@example",                  // Contains special character
      "com#example",                  // Contains hash
      "com$example",                  // Contains dollar sign
      "com%example",                  // Contains percent
      "com^example",                  // Contains caret
      "com&example",                  // Contains ampersand
      "com*example",                  // Contains asterisk
      "com+example",                  // Contains plus
      "com=example",                  // Contains equals
      "com:example",                  // Contains colon
      "com;example",                  // Contains semicolon
      "com\"example",                 // Contains quote
      "com'example",                  // Contains apostrophe
      "com<example",                  // Contains less than
      "com>example",                  // Contains greater than
      "com?example",                  // Contains question mark
      "com|example",                  // Contains pipe
      "com~example",                  // Contains tilde
      "com`example",                  // Contains backtick
      "com,example",                  // Contains comma
      "com{example}",                 // Contains curly braces
      "com[example]",                  // Contains square brackets
      "über.example",                  // Contains Unicode
      "café.example",                  // Contains accented character
      "中文.example",                   // Contains Chinese characters
  })
  @DisplayName("Should throw exception for groupId with invalid format")
  void shouldThrowForInvalidFormat(String invalidValue) {
    // Skip the segment length test for the long segment case
    if (invalidValue.contains("a".repeat(64))) {
      assertThrows(IllegalArgumentException.class, () -> new GroupId(invalidValue),
          "GroupId segment exceeds maximum length");
    } else {
      assertThrows(IllegalArgumentException.class, () -> new GroupId(invalidValue),
          "GroupId must follow Java package naming convention");
    }
  }

  @DisplayName("Should throw exception for groupId with invalid format")
  @Test
  void shouldThrowForTooLongSegment() {
    final String value = "com." + "a".repeat(64) + ".example";  // Segment too long (64 chars)
    assertThrows(IllegalArgumentException.class, () -> new GroupId(value));
  }

  @Test
  @DisplayName("Should throw exception when total length exceeds maximum")
  void shouldThrowForExcessiveLength() {
    // Create a groupId with 256 characters (exceeds 255 limit)
    String longGroupId = "com.example." + "a".repeat(240);
    assertThrows(IllegalArgumentException.class, () -> new GroupId(longGroupId),
        "GroupId must not exceed 255 characters");
  }

  @Test
  @DisplayName("Should throw exception when segment exceeds maximum length")
  void shouldThrowForSegmentExceedingMaxLength() {
    String longSegment = "a".repeat(64);
    String groupIdWithLongSegment = "com." + longSegment + ".example";

    Exception exception = assertThrows(IllegalArgumentException.class,
        () -> new GroupId(groupIdWithLongSegment));
    assertTrue(exception.getMessage().contains("must not exceed 63"));
  }

  @Test
  @DisplayName("Should preserve exact value after creation")
  void shouldPreserveExactValue() {
    String expected = "org.apache.maven";
    GroupId groupId = new GroupId(expected);
    assertEquals(expected, groupId.value());
  }

  @Test
  @DisplayName("Should implement equals and hashCode correctly")
  void shouldImplementEqualsAndHashCode() {
    GroupId id1 = new GroupId("org.apache.maven");
    GroupId id2 = new GroupId("org.apache.maven");
    GroupId id3 = new GroupId("com.google.guava");

    assertEquals(id1, id2);
    assertNotEquals(id1, id3);
    assertEquals(id1.hashCode(), id2.hashCode());
    assertNotEquals(id1.hashCode(), id3.hashCode());
  }

  @Test
  @DisplayName("Should implement toString correctly")
  void shouldImplementToString() {
    GroupId groupId = new GroupId("org.apache.maven");
    assertTrue(groupId.toString().contains("org.apache.maven"));
  }

  @Test
  @DisplayName("Should handle edge case with maximum allowed segment length")
  void shouldHandleMaxSegmentLength() {
    String maxSegment = "a".repeat(63);
    GroupId groupId = new GroupId("com." + maxSegment + ".example");
    assertEquals("com." + maxSegment + ".example", groupId.value());
  }

  @DisplayName("Should handle edge case with maximum total length")
  @TestFactory
  DynamicTest[] shouldHandleMaxTotalLength() {
    // Create a groupId of exactly 255 characters
    final String longSegment = "a".repeat(63);
    final String value = longSegment
        + '.' + longSegment
        + '.' + longSegment
        + '.' + longSegment;
    return new DynamicTest[]{
        dynamicTest(
            "Check groupId of length 255 works",
            () -> assertDoesNotThrow(() -> new GroupId(value))
        ),
        dynamicTest(
            "Check groupId of length 256 fails",
            () -> assertThrows(
                IllegalArgumentException.class,
                () -> new GroupId(value + 'a')
            )
        ),
    };
  }

  @Test
  @DisplayName("Should validate Java package naming conventions")
  void shouldValidateJavaPackageConventions() {
    // Valid Java package names
    assertDoesNotThrow(() -> new GroupId("com"));
    assertDoesNotThrow(() -> new GroupId("com.google"));
    assertDoesNotThrow(() -> new GroupId("org.apache.maven.plugin"));
    assertDoesNotThrow(() -> new GroupId("io.netty.buffer"));
    assertDoesNotThrow(() -> new GroupId("com.fasterxml.jackson.databind"));

    // Invalid Java package names (should throw)
    assertThrows(IllegalArgumentException.class, () -> new GroupId("com.123example"));
    assertThrows(IllegalArgumentException.class, () -> new GroupId("123.com"));
    assertThrows(IllegalArgumentException.class, () -> new GroupId("com.example-123"));
  }

  @Test
  @DisplayName("Should handle company domain-based groupIds")
  void shouldHandleDomainBasedGroupIds() {
    assertDoesNotThrow(() -> new GroupId("com.google"));
    assertDoesNotThrow(() -> new GroupId("org.springframework"));
    assertDoesNotThrow(() -> new GroupId("io.netty"));
    assertDoesNotThrow(() -> new GroupId("net.sourceforge"));
    assertDoesNotThrow(() -> new GroupId("uk.co.company"));
    assertDoesNotThrow(() -> new GroupId("de.company.product"));
    assertDoesNotThrow(() -> new GroupId("com.github.username"));
  }
}
