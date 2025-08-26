package com.github.build;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.build.deps.Coordinates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * @author noavarice
 * @since 1.0.0
 */
@DisplayName("Coordinates DTO tests")
class CoordinatesTest {

  @DisplayName("Check parsing")
  @ParameterizedTest(name = "Check parsing {0}")
  @ValueSource(strings = {
      "org.springframework.boot:spring-boot-starter-web:3.2.5", // without classifier
      "org.springframework.boot:spring-boot-starter-web:jar:3.2.5", // with classifier
  })
  void testParsing(final String dependencyString) {
    final var expected = new Coordinates(
        "org.springframework.boot",
        "spring-boot-starter-web",
        "3.2.5"
    );
    assertThat(Coordinates.parse(dependencyString)).isEqualTo(expected);
  }
}
