package com.github.build.test;

import java.util.List;

/**
 * @author noavarice
 */
public record TestResults(
    long testsSucceededCount,
    long testsFailedCount,
    long testsSkippedCount,
    List<TestFailure> failures
) {

  public static final TestResults NO_TESTS_FOUND = new TestResults(0, 0, 0, List.of());

  public TestResults {
    checkPositive(testsSucceededCount);
    checkPositive(testsFailedCount);
    checkPositive(testsSkippedCount);
  }

  private void checkPositive(final long value) {
    if (value < 0) {
      throw new IllegalArgumentException();
    }
  }
}
