package com.github.build.test;

import com.github.build.deps.GroupArtifactVersion;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Test arguments.
 *
 * @param buildRuntimeClasspath        Path to build library(-ies) JAR
 * @param testRuntimeParentClassLoader Parent classloader for test runtime custom classloader
 * @author noavarice
 * @since 1.0.0
 */
public record JUnitTestArgs(
    Set<Path> buildRuntimeClasspath,
    @Nullable ClassLoader testRuntimeParentClassLoader,
    GroupArtifactVersion slf4jProviderFallback
) {

  public JUnitTestArgs {
    buildRuntimeClasspath = Set.copyOf(buildRuntimeClasspath);
    if (buildRuntimeClasspath.isEmpty()) {
      throw new IllegalArgumentException();
    }

    Objects.requireNonNull(slf4jProviderFallback);
  }

  public JUnitTestArgs(final Set<Path> buildRuntimeClasspath) {
    this(buildRuntimeClasspath, null, DEFAULT_SLF4J_PROVIDER_FALLBACK);
  }

  private static final GroupArtifactVersion DEFAULT_SLF4J_PROVIDER_FALLBACK = GroupArtifactVersion
      .parse("ch.qos.logback:logback-classic:1.5.21");
}
