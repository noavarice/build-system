package com.github.build.deps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * @author noavarice
 * @since 1.0.0
 */
@DisplayName("Tests for GroupArtifact class")
class GroupArtifactTest {

  @DisplayName("Check parsing works")
  @TestFactory
  DynamicTest[] testParsingWorks() {
    final String value = "ch.qos.logback:logback-core";
    final GroupArtifact ga = GroupArtifact.parse(value);
    return new DynamicTest[]{
        dynamicTest("Check group ID", () -> assertEquals("ch.qos.logback", ga.groupId())),
        dynamicTest("Check artifact ID", () -> assertEquals("logback-core", ga.artifactId())),
    };
  }
}
