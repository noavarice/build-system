package com.github.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.github.build.deps.GroupArtifactVersion;
import com.github.build.deps.graph.GraphPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

@DisplayName("Graph path tests")
class GraphPathTest {

  private final GroupArtifactVersion slf4jApi = GroupArtifactVersion.parse(
      "org.slf4j:slf4j-api:2.0.17"
  );

  private final GroupArtifactVersion slf4jApiPrevious = GroupArtifactVersion.parse(
      "org.slf4j:slf4j-api:2.0.16"
  );

  private final GroupArtifactVersion logbackCore = GroupArtifactVersion.parse(
      "ch.qos.logback:logback-core:1.5.18"
  );

  private final GroupArtifactVersion logbackClassic = GroupArtifactVersion.parse(
      "ch.qos.logback:logback-classic:1.5.18"
  );

  @DisplayName("Check adding to empty path works")
  @Test
  void testAddToEmpty() {
    final var empty = GraphPath.ROOT;
    final var nonEmpty = empty.addLast(slf4jApi);
    assertThat(nonEmpty.getLast()).isSameAs(slf4jApi);
  }

  @DisplayName("Check removing works")
  @TestFactory
  DynamicTest[] testRemoving() {
    final var expected = GraphPath.ROOT;
    final var actual = new GraphPath(slf4jApi).removeLast();
    return new DynamicTest[]{
        dynamicTest("Check paths are equal", () -> assertThat(actual).isEqualTo(expected)),
        dynamicTest("Check paths are not the same", () -> assertThat(actual).isNotSameAs(expected)),
    };
  }

  @DisplayName("Check equals method works")
  @TestFactory
  DynamicTest[] testEquals() {
    final var path1 = new GraphPath(slf4jApi);
    final var path2 = new GraphPath(slf4jApi);
    return new DynamicTest[]{
        dynamicTest("Check paths are equal", () -> assertThat(path1).isEqualTo(path2)),
        dynamicTest("Check paths are not the same", () -> assertThat(path1).isNotSameAs(path2)),
    };
  }

  @DisplayName("Check duplicate detection works")
  @TestFactory
  DynamicTest[] testDuplicates() {
    final var path = new GraphPath(logbackClassic, slf4jApi);
    final var duplicate = new GraphPath(logbackClassic, slf4jApiPrevious);
    final var nonDuplicate = new GraphPath(logbackClassic, logbackCore);
    return new DynamicTest[]{
        dynamicTest(
            "Check same path is considered equal",
            () -> assertThat(path.isDuplicateWith(path)).isTrue()
        ),
        dynamicTest(
            "Check paths are duplicate",
            () -> assertThat(path.isDuplicateWith(duplicate)).isTrue()
        ),
        dynamicTest(
            "Check paths are not the same",
            () -> assertThat(path.isDuplicateWith(nonDuplicate)).isFalse()
        ),
    };
  }
}
