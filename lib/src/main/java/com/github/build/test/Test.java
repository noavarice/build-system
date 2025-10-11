package com.github.build.test;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathRoots;

import com.github.build.Project;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author noavarice
 */
public final class Test {

  private static final Logger log = LoggerFactory.getLogger(Test.class);

  private Test() {
  }

  public static TestResults withJUnit(final Path workdir, final Project project) {
    Objects.requireNonNull(workdir);
    Objects.requireNonNull(project);
    log.info("[project={}] Setting up tests", project.id());

    final var summaryListener = new SummaryGeneratingListener();
    try (final LauncherSession session = LauncherFactory.openSession()) {
      final Launcher launcher = session.getLauncher();
      launcher.registerTestExecutionListeners(summaryListener);

      final Path mainClasses = workdir
          .resolve(project.path())
          .resolve(project.artifactLayout().rootDir())
          .resolve(project.artifactLayout().classesDir())
          .resolve("main");
      final Path testClasses = workdir
          .resolve(project.path())
          .resolve(project.artifactLayout().rootDir())
          .resolve(project.artifactLayout().classesDir())
          .resolve("test");
      final LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder
          .request()
          .selectors(selectClasspathRoots(Set.of(mainClasses, testClasses)))
          .build();
      final TestPlan testPlan = launcher.discover(request);
      if (!testPlan.containsTests()) {
        log.warn("[project={}] No tests found", project.id());
        return TestResults.NO_TESTS_FOUND;
      }

      launcher.execute(testPlan);
    }

    final TestExecutionSummary summary = summaryListener.getSummary();
    log.info("[project={}] Tests finished in {}, succeeded {}, failed {}, skipped {}",
        project.id(),
        summary.getTimeFinished(),
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
