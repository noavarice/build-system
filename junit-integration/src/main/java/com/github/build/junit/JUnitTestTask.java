package com.github.build.junit;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathRoots;

import com.github.build.test.JUnitTestTaskArgs;
import com.github.build.test.TestResults;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/**
 * @author noavarice
 * @since 1.0.0
 */
public final class JUnitTestTask implements Function<JUnitTestTaskArgs, TestResults> {

  private static final Logger log = LoggerFactory.getLogger(JUnitTestTask.class);

  @Override
  public TestResults apply(final JUnitTestTaskArgs args) {
    final var summaryListener = new SummaryGeneratingListener();

    final LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder
        .request()
        .selectors(selectClasspathRoots(Set.of(args.testClassesDir())))
        .build();

    try (final LauncherSession session = LauncherFactory.openSession()) {
      final Launcher launcher = session.getLauncher();
      launcher.registerTestExecutionListeners(summaryListener);
      final TestPlan testPlan = launcher.discover(request);
      if (!testPlan.containsTests()) {
        log.warn("No tests found"); // TODO: add project ID correlation
        return TestResults.NO_TESTS_FOUND;
      }

      final TestExecutionListener executionListener = new TestExecutionListener() {
        @Override
        public void executionFinished(
            final TestIdentifier testIdentifier,
            final TestExecutionResult testExecutionResult
        ) {
          final Level level = switch (testExecutionResult.getStatus()) {
            case SUCCESSFUL, ABORTED -> Level.DEBUG;
            case FAILED -> Level.ERROR;
          };
          final Optional<Throwable> t = testExecutionResult.getThrowable();
          if (t.isPresent()) {
            log.atLevel(level).log("{} {}",
                testIdentifier.getUniqueId(),
                testExecutionResult.getStatus(),
                t.get()
            );
          } else {
            log.atLevel(level).log("{} {}",
                testIdentifier.getUniqueId(),
                testExecutionResult.getStatus()
            );
          }
        }
      };
      launcher.execute(testPlan, executionListener);
    }

    final TestExecutionSummary summary = summaryListener.getSummary();
    final Duration duration = Duration.between(
        Instant.ofEpochMilli(summary.getTimeStarted()),
        Instant.ofEpochMilli(summary.getTimeFinished())
    );
    // TODO: add project ID correlation
    final Level level = summary.getTestsFailedCount() > 0 ? Level.ERROR : Level.INFO;
    log.atLevel(level).log("{} tests finished in {}, succeeded {}, failed {}, skipped {}",
        summary.getTestsFoundCount(),
        duration,
        summary.getTestsSucceededCount(),
        summary.getTestsFailedCount(),
        summary.getTestsSkippedCount()
    );

    return new TestResults(
        summary.getTestsSucceededCount(),
        summary.getTestsFailedCount(),
        summary.getTestsSkippedCount()
    );
  }
}
