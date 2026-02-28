package com.github.build.test.junit;

import com.github.build.test.TestResults;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author noavarice
 * @since 1.0.0
 */
public final class AccumulateTestResults implements Consumer<JUnitEvent> {

  private static final Logger log = LoggerFactory.getLogger(AccumulateTestResults.class);

  private final AtomicInteger succeeded = new AtomicInteger(0);

  private final AtomicInteger skipped = new AtomicInteger(0);

  private final AtomicInteger failed = new AtomicInteger(0);

  @Override
  public void accept(final JUnitEvent event) {
    switch (event) {
      case JUnitEvent.ContainerStarted containerStarted ->
          log.debug("JUnit test container started: {}", containerStarted.containerId());
      case JUnitEvent.ContainerSkipped containerSkipped -> {
        log.info("JUnit test container skipped: {}", containerSkipped.containerId());
        skipped.incrementAndGet();
      }
      case JUnitEvent.ContainerFinished containerFinished ->
          log.info("JUnit test container finished: {}", containerFinished.containerId());
      case JUnitEvent.TestFinished testFinished -> {
        switch (testFinished.status()) {
          case SUCCESSFUL -> {
            log.trace("JUnit test succeeded: {}", testFinished.testId());
            succeeded.incrementAndGet();
          }
          case ABORTED -> log.warn("JUnit test aborted: {}", testFinished.testId());
          case FAILED -> {
            log.error("JUnit test failed: {}", testFinished.testId());
            failed.incrementAndGet();
          }
        }
      }
      case JUnitEvent.TestSkipped testSkipped -> {
        log.debug("JUnit test skipped: {}", testSkipped.testId());
        skipped.incrementAndGet();
      }
    }
  }

  public TestResults toTestResults() {
    return new TestResults(succeeded.get(), failed.get(), skipped.get());
  }
}
