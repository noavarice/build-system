package com.github.build.junit;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathRoots;

import com.github.build.test.TestResults;
import com.github.build.test.junit.JUnitEvent;
import com.github.build.test.junit.JUnitEventJsonCodec;
import com.github.build.test.junit.JUnitTestTaskArgs;
import com.github.build.util.UnixSocketClient;
import java.nio.file.Path;
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

  public static void main(final String[] args) {
    try {
      final var unixSocketPath = Path.of(args[0]);
      final UnixSocketClient<JUnitEvent> client = UnixSocketClient.of(
          unixSocketPath,
          new JUnitEventJsonCodec()
      );
      final var testExecutionListener = new UnixSocketForwardingTestExecutionListener(client);

      final Path testClassesDir = Path.of(args[1]);
      final var taskArgs = new JUnitTestTaskArgs(testClassesDir);
      runTests(taskArgs, testExecutionListener);
    } catch (final Exception e) {
      log.error("Test task failed", e);
      System.exit(1);
      return;
    }

    System.exit(0);
  }

  @Override
  public TestResults apply(final JUnitTestTaskArgs args) {
    final var summaryListener = new SummaryGeneratingListener();
    runTests(args, summaryListener);

    final TestExecutionSummary summary = summaryListener.getSummary();
    return new TestResults(
        summary.getTestsSucceededCount(),
        summary.getTestsFailedCount(),
        summary.getTestsSkippedCount()
    );
  }

  // TODO: handle situation when no test engines can be found
  private static void runTests(
      final JUnitTestTaskArgs args,
      final TestExecutionListener... testExecutionListeners
  ) {
    final LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder
        .request()
        .selectors(selectClasspathRoots(Set.of(args.testClassesDir())))
        .build();

    try (final LauncherSession session = LauncherFactory.openSession()) {
      final Launcher launcher = session.getLauncher();
      if (testExecutionListeners != null) {
        launcher.registerTestExecutionListeners(testExecutionListeners);
      }

      final TestPlan testPlan = launcher.discover(request);
      if (!testPlan.containsTests()) {
        log.warn("No tests found"); // TODO: add project ID correlation
        return;
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
  }
}
