package com.github.build.deps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * @author noavarice
 * @since 1.0.0
 */
@DisplayName("Tests for dependency constraint container")
class DependencyConstraintsTest {

  @DisplayName("Check empty containers are equal")
  @Test
  void checkEmptyEqual() {
    final var dc1 = DependencyConstraints
        .builder()
        .build();
    final var dc2 = DependencyConstraints
        .builder()
        .build();
    assertEquals(dc1, dc2);
  }

  @DisplayName("Check non-empty containers are equal")
  @Test
  void checkNonEmptyEqual() {
    final var dc1 = DependencyConstraints
        .builder()
        .withExactVersion(new GroupArtifact("ch.qos.logback", "logback-core"), "1.5.17")
        .build();
    final var dc2 = DependencyConstraints
        .builder()
        .withExactVersion(new GroupArtifact("ch.qos.logback", "logback-core"), "1.5.17")
        .build();
    assertEquals(dc1, dc2);
  }

  @DisplayName("Check non-empty different containers are not equal")
  @Test
  void checkNonEmptyDifferentAreNotEqual() {
    final var dc1 = DependencyConstraints
        .builder()
        .withExactVersion(new GroupArtifact("ch.qos.logback", "logback-core"), "1.5.17")
        .withExactVersion(new GroupArtifact("ch.qos.logback", "logback-classic"), "1.5.17")
        .build();
    final var dc2 = DependencyConstraints
        .builder()
        .withExactVersion(new GroupArtifact("ch.qos.logback", "logback-core"), "1.5.17")
        .build();
    assertNotEquals(dc1, dc2);
  }
}
