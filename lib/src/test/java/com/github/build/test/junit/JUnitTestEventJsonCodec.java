package com.github.build.test.junit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.build.test.EventCodec;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Tests for JUnitEvent JSON serialization/deserialization")
class JUnitTestEventJsonCodec {

  private final EventCodec<JUnitEvent> codec = new JUnitEventJsonCodec();

  @Nested
  @DisplayName("toBytes serialization tests")
  class ToBytesTests {

    @Test
    @DisplayName("Should serialize ContainerStarted correctly")
    void serializeContainerStarted() {
      JUnitEvent event = new JUnitEvent.ContainerStarted("container-123");
      byte[] bytes = codec.toBytes(event);
      String json = new String(bytes, StandardCharsets.UTF_8);

      assertEquals("{\"type\":\"ContainerStarted\",\"containerId\":\"container-123\"}", json);
    }

    @Test
    @DisplayName("Should serialize ContainerFinished correctly")
    void serializeContainerFinished() {
      JUnitEvent event = new JUnitEvent.ContainerFinished("container-456");
      byte[] bytes = codec.toBytes(event);
      String json = new String(bytes, StandardCharsets.UTF_8);

      assertEquals("{\"type\":\"ContainerFinished\",\"containerId\":\"container-456\"}", json);
    }

    @Test
    @DisplayName("Should serialize ContainerSkipped correctly")
    void serializeContainerSkipped() {
      JUnitEvent event = new JUnitEvent.ContainerSkipped("container-789");
      byte[] bytes = codec.toBytes(event);
      String json = new String(bytes, StandardCharsets.UTF_8);

      assertEquals("{\"type\":\"ContainerSkipped\",\"containerId\":\"container-789\"}", json);
    }

    @Test
    @DisplayName("Should serialize TestSkipped correctly")
    void serializeTestSkipped() {
      JUnitEvent event = new JUnitEvent.TestSkipped("test-abc");
      byte[] bytes = codec.toBytes(event);
      String json = new String(bytes, StandardCharsets.UTF_8);

      assertEquals("{\"type\":\"TestSkipped\",\"testId\":\"test-abc\"}", json);
    }

    @ParameterizedTest
    @EnumSource(JUnitEvent.Status.class)
    @DisplayName("Should serialize TestFinished with all status types correctly")
    void serializeTestFinished(JUnitEvent.Status status) {
      JUnitEvent event = new JUnitEvent.TestFinished("test-xyz", status);
      byte[] bytes = codec.toBytes(event);
      String json = new String(bytes, StandardCharsets.UTF_8);

      assertEquals(
          "{\"type\":\"TestFinished\",\"testId\":\"test-xyz\",\"status\":\"" + status.name()
              + "\"}", json);
    }

    @Test
    @DisplayName("Should handle null input gracefully")
    void serializeNull() {
      byte[] bytes = codec.toBytes(null);
      String json = new String(bytes, StandardCharsets.UTF_8);
      assertEquals("null", json);
    }

    @Test
    @DisplayName("Should escape special characters in strings")
    void escapeSpecialCharacters() {
      JUnitEvent event = new JUnitEvent.TestSkipped("test\"with\\quotes\nand\tnewlines");
      byte[] bytes = codec.toBytes(event);
      String json = new String(bytes, StandardCharsets.UTF_8);

      // Verify the string is properly escaped
      assertTrue(json.contains("test\\\"with\\\\quotes\\nand\\tnewlines"));

      // Round-trip test
      JUnitEvent deserialized = codec.toValue(bytes);
      assertEquals(event, deserialized);
    }

    @Test
    @DisplayName("Should return valid UTF-8 bytes")
    void returnsUtf8Bytes() {
      JUnitEvent event = new JUnitEvent.TestSkipped("test with unicode: ñ á é");
      byte[] bytes = codec.toBytes(event);

      // Verify it's valid UTF-8 by decoding
      String json = new String(bytes, StandardCharsets.UTF_8);
      assertTrue(json.contains("ñ á é") || json.contains("\\u00f1"));
    }
  }

  @Nested
  @DisplayName("toValue deserialization tests")
  class ToValueTests {

    @Test
    @DisplayName("Should deserialize ContainerStarted correctly")
    void deserializeContainerStarted() {
      String json = "{\"type\":\"ContainerStarted\",\"containerId\":\"container-123\"}";
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

      JUnitEvent event = codec.toValue(bytes);

      assertInstanceOf(JUnitEvent.ContainerStarted.class, event);
      JUnitEvent.ContainerStarted containerStarted = (JUnitEvent.ContainerStarted) event;
      assertEquals("container-123", containerStarted.containerId());
    }

    @Test
    @DisplayName("Should deserialize ContainerFinished correctly")
    void deserializeContainerFinished() {
      String json = "{\"type\":\"ContainerFinished\",\"containerId\":\"container-456\"}";
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

      JUnitEvent event = codec.toValue(bytes);

      assertInstanceOf(JUnitEvent.ContainerFinished.class, event);
      JUnitEvent.ContainerFinished containerFinished = (JUnitEvent.ContainerFinished) event;
      assertEquals("container-456", containerFinished.containerId());
    }

    @Test
    @DisplayName("Should deserialize ContainerSkipped correctly")
    void deserializeContainerSkipped() {
      String json = "{\"type\":\"ContainerSkipped\",\"containerId\":\"container-789\"}";
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

      JUnitEvent event = codec.toValue(bytes);

      assertInstanceOf(JUnitEvent.ContainerSkipped.class, event);
      JUnitEvent.ContainerSkipped containerSkipped = (JUnitEvent.ContainerSkipped) event;
      assertEquals("container-789", containerSkipped.containerId());
    }

    @Test
    @DisplayName("Should deserialize TestSkipped correctly")
    void deserializeTestSkipped() {
      String json = "{\"type\":\"TestSkipped\",\"testId\":\"test-abc\"}";
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

      JUnitEvent event = codec.toValue(bytes);

      assertInstanceOf(JUnitEvent.TestSkipped.class, event);
      JUnitEvent.TestSkipped testSkipped = (JUnitEvent.TestSkipped) event;
      assertEquals("test-abc", testSkipped.testId());
    }

    @ParameterizedTest
    @EnumSource(JUnitEvent.Status.class)
    @DisplayName("Should deserialize TestFinished with all status types correctly")
    void deserializeTestFinished(JUnitEvent.Status status) {
      String json =
          "{\"type\":\"TestFinished\",\"testId\":\"test-xyz\",\"status\":\"" + status.name()
              + "\"}";
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

      JUnitEvent event = codec.toValue(bytes);

      assertInstanceOf(JUnitEvent.TestFinished.class, event);
      JUnitEvent.TestFinished testFinished = (JUnitEvent.TestFinished) event;
      assertEquals("test-xyz", testFinished.testId());
      assertEquals(status, testFinished.status());
    }

    @Test
    @DisplayName("Should handle JSON with whitespace")
    void deserializeWithWhitespace() {
      String json = "{\n  \"type\": \"TestFinished\",\n  \"testId\": \"test-123\",\n  \"status\": \"SUCCESSFUL\"\n}";
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

      JUnitEvent event = codec.toValue(bytes);

      assertInstanceOf(JUnitEvent.TestFinished.class, event);
      JUnitEvent.TestFinished testFinished = (JUnitEvent.TestFinished) event;
      assertEquals("test-123", testFinished.testId());
      assertEquals(JUnitEvent.Status.SUCCESSFUL, testFinished.status());
    }

    @Test
    @DisplayName("Should handle escaped characters in JSON strings")
    void deserializeEscapedCharacters() {
      String json = "{\"type\":\"TestSkipped\",\"testId\":\"test\\\"with\\\"quotes\\nand\\ttabs\"}";
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

      JUnitEvent event = codec.toValue(bytes);

      assertInstanceOf(JUnitEvent.TestSkipped.class, event);
      JUnitEvent.TestSkipped testSkipped = (JUnitEvent.TestSkipped) event;
      assertEquals("test\"with\"quotes\nand\ttabs", testSkipped.testId());
    }

    @Test
    @DisplayName("Should handle Unicode escapes in JSON strings")
    void deserializeUnicodeEscapes() {
      String json = "{\"type\":\"TestSkipped\",\"testId\":\"test with unicode: \\u00F1 \\u00E1 \\u00E9\"}";
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

      JUnitEvent event = codec.toValue(bytes);

      assertInstanceOf(JUnitEvent.TestSkipped.class, event);
      JUnitEvent.TestSkipped testSkipped = (JUnitEvent.TestSkipped) event;
      assertEquals("test with unicode: ñ á é", testSkipped.testId());
    }

    @Test
    @DisplayName("Should handle field order variation")
    void deserializeDifferentFieldOrder() {
      String json = "{\"testId\":\"test-123\",\"type\":\"TestFinished\",\"status\":\"FAILED\"}";
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

      JUnitEvent event = codec.toValue(bytes);

      assertInstanceOf(JUnitEvent.TestFinished.class, event);
      JUnitEvent.TestFinished testFinished = (JUnitEvent.TestFinished) event;
      assertEquals("test-123", testFinished.testId());
      assertEquals(JUnitEvent.Status.FAILED, testFinished.status());
    }

    @Test
    @DisplayName("Should ignore unknown fields")
    void deserializeWithUnknownFields() {
      String json = "{\"type\":\"ContainerStarted\",\"containerId\":\"c123\",\"unknownField\":\"value\",\"anotherUnknown\":42}";
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

      JUnitEvent event = codec.toValue(bytes);

      assertInstanceOf(JUnitEvent.ContainerStarted.class, event);
      JUnitEvent.ContainerStarted containerStarted = (JUnitEvent.ContainerStarted) event;
      assertEquals("c123", containerStarted.containerId());
    }

    @Test
    @DisplayName("Should handle UTF-8 encoded bytes")
    void handleUtf8Bytes() {
      String json = "{\"type\":\"TestSkipped\",\"testId\":\"test with unicode: ñ á é\"}";
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

      JUnitEvent event = codec.toValue(bytes);

      assertInstanceOf(JUnitEvent.TestSkipped.class, event);
      JUnitEvent.TestSkipped testSkipped = (JUnitEvent.TestSkipped) event;
      assertEquals("test with unicode: ñ á é", testSkipped.testId());
    }
  }

  @Nested
  @DisplayName("Round-trip tests")
  class RoundTripTests {

    static Stream<JUnitEvent> provideEvents() {
      return Stream.of(
          new JUnitEvent.ContainerStarted("container-1"),
          new JUnitEvent.ContainerFinished("container-2"),
          new JUnitEvent.ContainerSkipped("container-3"),
          new JUnitEvent.TestSkipped("test-1"),
          new JUnitEvent.TestFinished("test-2", JUnitEvent.Status.SUCCESSFUL),
          new JUnitEvent.TestFinished("test-3", JUnitEvent.Status.FAILED),
          new JUnitEvent.TestFinished("test-4", JUnitEvent.Status.ABORTED)
      );
    }

    @ParameterizedTest
    @MethodSource("provideEvents")
    @DisplayName("Should maintain equality after toBytes/toValue round-trip")
    void roundTrip(JUnitEvent original) {
      byte[] bytes = codec.toBytes(original);
      JUnitEvent deserialized = codec.toValue(bytes);

      assertEquals(original, deserialized, "Round-trip failed for: " + original);
      assertEquals(original.hashCode(), deserialized.hashCode(), "Hash codes should match");
    }

    @Test
    @DisplayName("Should handle empty strings in round-trip")
    void roundTripWithEmptyStrings() {
      JUnitEvent event = new JUnitEvent.TestSkipped("");

      byte[] bytes = codec.toBytes(event);
      JUnitEvent deserialized = codec.toValue(bytes);

      assertEquals(event, deserialized);
    }

    @Test
    @DisplayName("Should handle very long strings in round-trip")
    void roundTripWithLongStrings() {
      String longString = "a".repeat(10000);
      JUnitEvent event = new JUnitEvent.TestSkipped(longString);

      byte[] bytes = codec.toBytes(event);
      JUnitEvent deserialized = codec.toValue(bytes);

      assertEquals(event, deserialized);
    }

    @Test
    @DisplayName("Should handle null in round-trip")
    void roundTripWithNull() {
      byte[] bytes = codec.toBytes(null);
      JUnitEvent deserialized = codec.toValue(bytes);

      assertNull(deserialized);
    }
  }

  @Nested
  @DisplayName("Error handling tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Should throw exception for null byte array input")
    void nullByteArrayInput() {
      assertThrows(IllegalArgumentException.class,
          () -> codec.toValue(null));
    }

    @Test
    @DisplayName("Should throw exception for empty byte array")
    void emptyByteArray() {
      byte[] emptyBytes = new byte[0];

      assertThrows(IllegalArgumentException.class,
          () -> codec.toValue(emptyBytes));
    }

    @Test
    @DisplayName("Should throw exception for invalid UTF-8 bytes")
    void invalidUtf8Bytes() {
      // Create invalid UTF-8 sequence (0xFF is invalid in UTF-8)
      byte[] invalidBytes = new byte[]{(byte) 0xFF, (byte) 0xFE, (byte) 0x00};

      assertThrows(IllegalArgumentException.class,
          () -> codec.toValue(invalidBytes));
    }

    @Test
    @DisplayName("Should throw exception for missing type field")
    void missingTypeField() {
      String json = "{\"containerId\":\"c123\"}";
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

      assertThrows(IllegalArgumentException.class,
          () -> codec.toValue(bytes));
    }

    @Test
    @DisplayName("Should throw exception for invalid type value")
    void invalidTypeValue() {
      String json = "{\"type\":\"InvalidType\",\"containerId\":\"c123\"}";
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

      assertThrows(IllegalArgumentException.class,
          () -> codec.toValue(bytes));
    }

    @Test
    @DisplayName("Should throw exception for missing required field")
    void missingRequiredField() {
      String json = "{\"type\":\"ContainerStarted\"}";
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

      assertThrows(IllegalArgumentException.class,
          () -> codec.toValue(bytes));
    }

    @Test
    @DisplayName("Should throw exception for invalid JSON syntax")
    void invalidJsonSyntax() {
      String[] invalidJsons = {
          "{\"type\":\"TestFinished\",\"testId\":\"test-123\",\"status\":\"SUCCESSFUL\"",
          // missing closing brace
          "{\"type\":\"TestFinished\",\"testId\":\"test-123\",\"status\":\"SUCCESSFUL\"}}",
          // extra brace
          "{\"type\":\"TestFinished\",\"testId\":\"test-123\",\"status\":SUCCESSFUL}",
          // unquoted value
          "{\"type\":\"TestFinished\",\"testId\":\"test-123\" \"status\":\"SUCCESSFUL\"}",
          // missing comma
          "{\"type\":\"TestFinished\",, \"testId\":\"test-123\"}", // extra comma
      };

      for (String invalidJson : invalidJsons) {
        byte[] bytes = invalidJson.getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
            () -> codec.toValue(bytes),
            "Should throw for invalid JSON: " + invalidJson);
      }
    }

    @Test
    @DisplayName("Should throw exception for invalid escape sequence")
    void invalidEscapeSequence() {
      String json = "{\"type\":\"TestSkipped\",\"testId\":\"test\\xinvalid\"}";
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

      assertThrows(IllegalArgumentException.class,
          () -> codec.toValue(bytes));
    }

    @Test
    @DisplayName("Should throw exception for incomplete Unicode escape")
    void incompleteUnicodeEscape() {
      String json = "{\"type\":\"TestSkipped\",\"testId\":\"test\\u00F\"}";
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

      assertThrows(IllegalArgumentException.class,
          () -> codec.toValue(bytes));
    }

    @Test
    @DisplayName("Should throw exception for invalid status value")
    void invalidStatusValue() {
      String json = "{\"type\":\"TestFinished\",\"testId\":\"test-123\",\"status\":\"INVALID_STATUS\"}";
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

      assertThrows(IllegalArgumentException.class,
          () -> codec.toValue(bytes));
    }

    @Test
    @DisplayName("Should throw exception for malformed byte input")
    void malformedByteInput() {
      // Random bytes that don't form valid JSON
      byte[] randomBytes = new byte[]{1, 2, 3, 4, 5};

      assertThrows(IllegalArgumentException.class,
          () -> codec.toValue(randomBytes));
    }
  }

  @Nested
  @DisplayName("Edge cases and boundaries")
  class EdgeCaseTests {

    @Test
    @DisplayName("Should handle string with all escaped characters")
    void allEscapedCharacters() {
      String specialChars = "\"\\/\b\f\n\r\t";
      JUnitEvent event = new JUnitEvent.TestSkipped(specialChars);

      byte[] bytes = codec.toBytes(event);
      JUnitEvent deserialized = codec.toValue(bytes);

      assertEquals(event, deserialized);
    }

    @Test
    @DisplayName("Should handle IDs with special characters")
    void idsWithSpecialCharacters() {
      JUnitEvent event = new JUnitEvent.ContainerStarted(
          "container/with/slashes?and#special:chars");

      byte[] bytes = codec.toBytes(event);
      JUnitEvent deserialized = codec.toValue(bytes);

      assertEquals(event, deserialized);
    }

    @Test
    @DisplayName("Should handle maximum Unicode code points")
    void maximumUnicodeCodePoints() {
      // Test with some valid Unicode characters including supplementary planes
      String unicodeStr = "𐐷𤭢🌍🎉";
      JUnitEvent event = new JUnitEvent.TestSkipped(unicodeStr);

      byte[] bytes = codec.toBytes(event);
      JUnitEvent deserialized = codec.toValue(bytes);

      assertEquals(event, deserialized);
    }

    @Test
    @DisplayName("Should handle JSON with multiple spaces")
    void jsonWithMultipleSpaces() {
      String json = "{   \"type\"   :   \"ContainerStarted\"   ,   \"containerId\"   :   \"c123\"   }";
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

      JUnitEvent event = codec.toValue(bytes);

      assertInstanceOf(JUnitEvent.ContainerStarted.class, event);
      assertEquals("c123", ((JUnitEvent.ContainerStarted) event).containerId());
    }

    @Test
    @DisplayName("Should handle very large byte arrays")
    void veryLargeByteArrays() {
      // Create a JSON with a very large string value
      String largeString = "a".repeat(100000);
      String json = "{\"type\":\"TestSkipped\",\"testId\":\"" + largeString + "\"}";
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

      JUnitEvent event = codec.toValue(bytes);

      assertInstanceOf(JUnitEvent.TestSkipped.class, event);
      assertEquals(largeString, ((JUnitEvent.TestSkipped) event).testId());
    }

    @Test
    @DisplayName("Should handle byte array with BOM (Byte Order Mark)")
    void handleBom() {
      // JSON with UTF-8 BOM
      byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
      String json = "{\"type\":\"ContainerStarted\",\"containerId\":\"c123\"}";

      byte[] bytesWithBom = new byte[bom.length + json.getBytes(StandardCharsets.UTF_8).length];
      System.arraycopy(bom, 0, bytesWithBom, 0, bom.length);
      System.arraycopy(json.getBytes(StandardCharsets.UTF_8), 0, bytesWithBom, bom.length,
          json.getBytes(StandardCharsets.UTF_8).length);

      // Should still work (BOM might be handled or might cause issues - adjust expectation based on implementation)
      assertDoesNotThrow(() -> {
        JUnitEvent event = codec.toValue(bytesWithBom);
        assertInstanceOf(JUnitEvent.ContainerStarted.class, event);
      });
    }
  }

  @Nested
  @DisplayName("Contract tests")
  class ContractTests {

    @Test
    @DisplayName("toBytes should return non-null byte array for non-null input")
    void toBytesReturnsNonNullForNonNullInput() {
      JUnitEvent event = new JUnitEvent.ContainerStarted("test");
      byte[] bytes = codec.toBytes(event);

      assertNotNull(bytes);
      assertTrue(bytes.length > 0);
    }

    @Test
    @DisplayName("toBytes should return 'null' bytes for null input")
    void toBytesReturnsNullBytesForNullInput() {
      byte[] bytes = codec.toBytes(null);

      assertNotNull(bytes);
      String json = new String(bytes, StandardCharsets.UTF_8);
      assertEquals("null", json);
    }

    @Test
    @DisplayName("toValue should return null for 'null' bytes")
    void toValueReturnsNullForNullBytes() {
      String json = "null";
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

      JUnitEvent event = codec.toValue(bytes);

      assertNull(event);
    }

    @Test
    @DisplayName("toValue should handle whitespace around null")
    void toValueHandlesWhitespaceAroundNull() {
      String json = "  null  ";
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

      JUnitEvent event = codec.toValue(bytes);

      assertNull(event);
    }
  }
}
