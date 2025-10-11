package com.github.build.test;

/**
 * @author noavarice
 */
public record TestResults(
    long testsSucceededCount,
    long testsFailedCount,
    long testsSkippedCount
) {

  public static final TestResults NO_TESTS_FOUND = new TestResults(0, 0, 0);

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
