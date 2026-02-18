package com.github.build.compile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * @author noavarice
 */
@DisplayName("Compiler options domain object tests")
class CompilerOptionsTest {

  @DisplayName("Check converting object to list of compiler option strings")
  @Test
  void testToList() {
    final var options = CompilerOptions
        .builder()
        .release("17")
        .parameters(true)
        .build();
    assertThat(options.toList()).isEqualTo(List.of("--release", "17", "-parameters"));
  }
}
