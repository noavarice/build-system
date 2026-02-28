package com.github.build.test.junit;

import com.github.build.test.EventCodec;
import java.nio.charset.StandardCharsets;

/**
 * @author noavarice
 * @since 1.0.0
 */
public final class JUnitEventJsonCodec implements EventCodec<JUnitEvent> {

  @Override
  public byte[] toBytes(JUnitEvent value) {
    if (value == null) {
      return "null".getBytes(StandardCharsets.UTF_8);
    }

    StringBuilder json = new StringBuilder();
    serializeEvent(value, json);
    return json.toString().getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public JUnitEvent toValue(byte[] bytes) {
    if (bytes == null) {
      throw new IllegalArgumentException("Byte array cannot be null");
    }

    if (bytes.length == 0) {
      throw new IllegalArgumentException("Byte array cannot be empty");
    }

    // Handle UTF-8 BOM (Byte Order Mark)
    int offset = 0;
    if (bytes.length >= 3 &&
        (bytes[0] & 0xFF) == 0xEF &&
        (bytes[1] & 0xFF) == 0xBB &&
        (bytes[2] & 0xFF) == 0xBF) {
      offset = 3;
    }

    String json;
    try {
      json = new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid UTF-8 byte sequence", e);
    }

    json = json.trim();
    if (json.isEmpty()) {
      throw new IllegalArgumentException("JSON string cannot be empty");
    }

    return parseEvent(json);
  }

  // Serialization methods
  private void serializeEvent(JUnitEvent event, StringBuilder json) {
    json.append("{");

    switch (event) {
      case JUnitEvent.ContainerStarted cs -> {
        json.append("\"type\":\"ContainerStarted\",");
        json.append("\"containerId\":").append(escapeString(cs.containerId()));
      }
      case JUnitEvent.ContainerFinished cf -> {
        json.append("\"type\":\"ContainerFinished\",");
        json.append("\"containerId\":").append(escapeString(cf.containerId()));
      }
      case JUnitEvent.ContainerSkipped cs -> {
        json.append("\"type\":\"ContainerSkipped\",");
        json.append("\"containerId\":").append(escapeString(cs.containerId()));
      }
      case JUnitEvent.TestSkipped ts -> {
        json.append("\"type\":\"TestSkipped\",");
        json.append("\"testId\":").append(escapeString(ts.testId()));
      }
      case JUnitEvent.TestFinished tf -> {
        json.append("\"type\":\"TestFinished\",");
        json.append("\"testId\":").append(escapeString(tf.testId())).append(",");
        json.append("\"status\":\"").append(tf.status().name()).append("\"");
      }
      default -> throw new IllegalArgumentException("Unknown event type: " + event.getClass());
    }

    json.append("}");
  }

  private String escapeString(String s) {
    if (s == null) {
      return "null";
    }

    StringBuilder sb = new StringBuilder("\"");
    for (char c : s.toCharArray()) {
      switch (c) {
        case '"':
          sb.append("\\\"");
          break;
        case '\\':
          sb.append("\\\\");
          break;
        case '/':
          sb.append("\\/");
          break;
        case '\b':
          sb.append("\\b");
          break;
        case '\f':
          sb.append("\\f");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\t':
          sb.append("\\t");
          break;
        default:
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
      }
    }
    sb.append("\"");
    return sb.toString();
  }

  // Parsing methods
  private JUnitEvent parseEvent(String json) {
    JsonParser parser = new JsonParser(json);
    parser.skipWhitespace();

    // Handle null literal
    if (parser.peek() == 'n') {
      parser.expectLiteral("null");
      parser.skipWhitespace();
      if (parser.hasNext()) {
        throw new IllegalArgumentException("Unexpected characters after null");
      }
      return null;
    }

    parser.expect('{');
    parser.skipWhitespace();

    String type = null;
    String containerId = null;
    String testId = null;
    JUnitEvent.Status status = null;

    while (parser.hasNext() && parser.peek() != '}') {
      String key = parser.parseString();
      parser.skipWhitespace();
      parser.expect(':');
      parser.skipWhitespace();

      switch (key) {
        case "type":
          type = parser.parseString();
          break;
        case "containerId":
          containerId = parser.parseString();
          break;
        case "testId":
          testId = parser.parseString();
          break;
        case "status":
          String statusStr = parser.parseString();
          try {
            status = JUnitEvent.Status.valueOf(statusStr);
          } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status value: " + statusStr);
          }
          break;
        default:
          // Skip unknown field
          parser.skipValue();
      }

      parser.skipWhitespace();
      if (parser.hasNext() && parser.peek() == ',') {
        parser.next();
        parser.skipWhitespace();
      } else if (parser.hasNext() && parser.peek() != '}') {
        // If we're not at the end of the object and there's no comma, that's an error
        throw new IllegalArgumentException(
            "Expected ',' or '}' but found '" + parser.peek() + "' at position " + parser.pos);
      }
    }

    if (!parser.hasNext()) {
      throw new IllegalArgumentException("Unexpected end of input while parsing object");
    }

    parser.expect('}');
    parser.skipWhitespace();

    // Check for extra characters after the JSON object
    if (parser.hasNext()) {
      throw new IllegalArgumentException(
          "Unexpected characters after JSON object at position " + parser.pos);
    }

    if (type == null) {
      throw new IllegalArgumentException("Missing type field in JSON");
    }

    return createEvent(type, containerId, testId, status);
  }

  private JUnitEvent createEvent(String type, String containerId, String testId,
      JUnitEvent.Status status) {
    switch (type) {
      case "ContainerStarted":
        if (containerId == null) {
          throw new IllegalArgumentException("Missing containerId for ContainerStarted");
        }
        return new JUnitEvent.ContainerStarted(containerId);
      case "ContainerFinished":
        if (containerId == null) {
          throw new IllegalArgumentException("Missing containerId for ContainerFinished");
        }
        return new JUnitEvent.ContainerFinished(containerId);
      case "ContainerSkipped":
        if (containerId == null) {
          throw new IllegalArgumentException("Missing containerId for ContainerSkipped");
        }
        return new JUnitEvent.ContainerSkipped(containerId);
      case "TestSkipped":
        if (testId == null) {
          throw new IllegalArgumentException("Missing testId for TestSkipped");
        }
        return new JUnitEvent.TestSkipped(testId);
      case "TestFinished":
        if (testId == null) {
          throw new IllegalArgumentException("Missing testId for TestFinished");
        }
        if (status == null) {
          throw new IllegalArgumentException("Missing status for TestFinished");
        }
        return new JUnitEvent.TestFinished(testId, status);
      default:
        throw new IllegalArgumentException("Unknown event type: " + type);
    }
  }

  // Simple JSON parser
  private static class JsonParser {

    private final String input;
    private int pos;

    JsonParser(String input) {
      this.input = input;
      this.pos = 0;
    }

    boolean hasNext() {
      return pos < input.length();
    }

    char peek() {
      if (pos >= input.length()) {
        return '\0';
      }
      return input.charAt(pos);
    }

    char next() {
      if (pos >= input.length()) {
        throw new IllegalArgumentException("Unexpected end of input");
      }
      return input.charAt(pos++);
    }

    void expect(char expected) {
      if (!hasNext()) {
        throw new IllegalArgumentException(
            String.format("Expected '%c' but reached end of input", expected)
        );
      }
      char actual = next();
      if (actual != expected) {
        throw new IllegalArgumentException(
            String.format("Expected '%c' but found '%c' at position %d", expected, actual, pos - 1)
        );
      }
    }

    void expectLiteral(String literal) {
      for (int i = 0; i < literal.length(); i++) {
        if (!hasNext() || next() != literal.charAt(i)) {
          throw new IllegalArgumentException("Expected literal: " + literal);
        }
      }
    }

    void skipWhitespace() {
      while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
        pos++;
      }
    }

    String parseString() {
      skipWhitespace();
      if (!hasNext()) {
        throw new IllegalArgumentException("Unexpected end of input while parsing string");
      }

      if (peek() != '"') {
        throw new IllegalArgumentException(
            "Expected '\"' but found '" + peek() + "' at position " + pos);
      }

      expect('"');
      StringBuilder sb = new StringBuilder();

      while (hasNext()) {
        char c = next();

        if (c == '"') {
          break;
        }

        if (c == '\\') {
          if (!hasNext()) {
            throw new IllegalArgumentException("Unexpected end of input after escape");
          }

          char escape = next();
          switch (escape) {
            case '"':
              sb.append('"');
              break;
            case '\\':
              sb.append('\\');
              break;
            case '/':
              sb.append('/');
              break;
            case 'b':
              sb.append('\b');
              break;
            case 'f':
              sb.append('\f');
              break;
            case 'n':
              sb.append('\n');
              break;
            case 'r':
              sb.append('\r');
              break;
            case 't':
              sb.append('\t');
              break;
            case 'u':
              // Parse Unicode escape \\uXXXX
              if (pos + 4 > input.length()) {
                throw new IllegalArgumentException("Incomplete Unicode escape");
              }
              String hex = input.substring(pos, pos + 4);
              try {
                int code = Integer.parseInt(hex, 16);
                sb.append((char) code);
                pos += 4;
              } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid Unicode escape: \\u" + hex);
              }
              break;
            default:
              throw new IllegalArgumentException("Invalid escape: \\" + escape);
          }
        } else {
          sb.append(c);
        }
      }

      return sb.toString();
    }

    void skipValue() {
      skipWhitespace();
      if (!hasNext()) {
        return;
      }

      char c = peek();

      if (c == '"') {
        parseString();
      } else if (c == '{') {
        skipObject();
      } else if (c == '[') {
        skipArray();
      } else if (c == 't' || c == 'f' || c == 'n') {
        skipLiteral();
      } else if (c == '-' || (c >= '0' && c <= '9')) {
        skipNumber();
      } else {
        throw new IllegalArgumentException("Unexpected character while skipping value: " + c);
      }
    }

    private void skipObject() {
      expect('{');
      skipWhitespace();

      while (hasNext() && peek() != '}') {
        if (peek() == '"') {
          parseString(); // key
          skipWhitespace();
          expect(':');
          skipValue(); // value

          skipWhitespace();
          if (hasNext() && peek() == ',') {
            next();
            skipWhitespace();
          } else if (hasNext() && peek() != '}') {
            // If we're not at the end of the object and there's no comma, that's an error
            throw new IllegalArgumentException(
                "Expected ',' or '}' but found '" + peek() + "' at position " + pos);
          }
        } else {
          throw new IllegalArgumentException(
              "Expected '\"' but found '" + peek() + "' at position " + pos);
        }
      }

      if (!hasNext()) {
        throw new IllegalArgumentException("Unexpected end of input in object");
      }
      expect('}');
    }

    private void skipArray() {
      expect('[');
      skipWhitespace();

      while (hasNext() && peek() != ']') {
        skipValue();
        skipWhitespace();
        if (hasNext() && peek() == ',') {
          next();
          skipWhitespace();
        } else if (hasNext() && peek() != ']') {
          throw new IllegalArgumentException(
              "Expected ',' or ']' but found '" + peek() + "' at position " + pos);
        }
      }

      if (!hasNext()) {
        throw new IllegalArgumentException("Unexpected end of input in array");
      }
      expect(']');
    }

    private void skipLiteral() {
      if (input.startsWith("true", pos)) {
        pos += 4;
      } else if (input.startsWith("false", pos)) {
        pos += 5;
      } else if (input.startsWith("null", pos)) {
        pos += 4;
      } else {
        throw new IllegalArgumentException("Invalid literal at position " + pos);
      }
    }

    private void skipNumber() {
      boolean hasDigits = false;
      while (hasNext()) {
        char c = peek();
        if (c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E' || (c >= '0' && c <= '9')) {
          if (c >= '0' && c <= '9') {
            hasDigits = true;
          }
          pos++;
        } else {
          break;
        }
      }
      if (!hasDigits) {
        throw new IllegalArgumentException("Invalid number at position " + pos);
      }
    }
  }
}
