package com.github.build.test;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathRoots;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
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

/**
 * @author noavarice
 * @since 1.0.0
 */
public final class JUnitTestTask implements Function<Map<String, Object>, Map<String, Object>> {

  private static final Logger log = LoggerFactory.getLogger(JUnitTestTask.class);

  @Override
  public Map<String, Object> apply(final Map<String, Object> args) {
    final var summaryListener = new SummaryGeneratingListener();

    final var testClassesDir = (Path) Objects.requireNonNull(args.get("testClassesDir"));
    final LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder
        .request()
        .selectors(selectClasspathRoots(Set.of(testClassesDir)))
        .build();

    try (final LauncherSession session = LauncherFactory.openSession()) {
      final Launcher launcher = session.getLauncher();
      launcher.registerTestExecutionListeners(summaryListener);
      final TestPlan testPlan = launcher.discover(request);
      if (!testPlan.containsTests()) {
        log.warn("No tests found"); // TODO: add project ID correlation
        return Map.of();
      }

      final TestExecutionListener executionListener = new TestExecutionListener() {
        @Override
        public void executionFinished(
            final TestIdentifier testIdentifier,
            final TestExecutionResult testExecutionResult
        ) {
          final Optional<Throwable> t = testExecutionResult.getThrowable();
          if (t.isPresent()) {
            log.info("{} {}",
                testIdentifier.getUniqueId(),
                testExecutionResult.getStatus(),
                t.get()
            );
          } else {
            log.info("{} {}",
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
    log.info("{} tests finished in {}, succeeded {}, failed {}, skipped {}",
        summary.getTestsFoundCount(),
        duration,
        summary.getTestsSucceededCount(),
        summary.getTestsFailedCount(),
        summary.getTestsSkippedCount()
    );

    return Map.of(
        "succeeded", summary.getTestsSucceededCount(),
        "failed", summary.getTestsFailedCount(),
        // FIXME: skipped test count is incorrect
        "skipped", summary.getTestsSkippedCount()
    );
  }
}
