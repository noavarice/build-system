package com.github.build.test.junit;

import java.nio.file.Path;
import java.util.Objects;

/**
 * @author noavarice
 * @since 1.0.0
 */
public record JUnitTestTaskArgs(Path testClassesDir) {

  public JUnitTestTaskArgs {
    Objects.requireNonNull(testClassesDir);
    testClassesDir = testClassesDir.normalize().toAbsolutePath();
  }
}
