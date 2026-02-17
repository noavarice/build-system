package com.github.build;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.build.jar.JarManifest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * @author noavarice
 * @since 1.0.0
 */
@DisplayName("JarManifest tests")
class JarManifestTest {

  @DisplayName("Check empty manifest is an empty string")
  @Test
  void testEmptyManifest_EmptyString() {
    final var manifest = JarManifest.builder().build();
    assertThat(manifest.toString()).isEmpty();
  }

  @DisplayName("Check non-empty manifest is a non-empty string")
  @Test
  void testNonEmptyManifest_NonEmptyString() {
    final var manifest = JarManifest
        .builder()
        .setVersion("1.0")
        .setCreatedBy("17.0.18 (Oracle Corporation)")
        .setImplementationTitle("spring-security-core")
        .setImplementationVersion("7.0.0")
        .build();
    assertThat(manifest.toString()).isEqualTo("""
        Manifest-Version: 1.0
        Created-By: 17.0.18 (Oracle Corporation)
        Implementation-Title: spring-security-core
        Implementation-Version: 7.0.0
        """);
  }
}
