package com.github.build.test;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathRoots;

import com.github.build.Project;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
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

    final var originalClassLoader = Thread.currentThread().getContextClassLoader();
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
    try (final var classLoader = createModifiedClassLoader(
        // TODO: tests should not be able to load build application classes
        originalClassLoader,
        mainClasses,
        testClasses
    )) {
      Thread.currentThread().setContextClassLoader(classLoader);
      try (final LauncherSession session = LauncherFactory.openSession()) {
        final Launcher launcher = session.getLauncher();
        launcher.registerTestExecutionListeners(summaryListener);

        final LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder
            .request()
            .selectors(selectClasspathRoots(Set.of(testClasses)))
            .build();
        final TestPlan testPlan = launcher.discover(request);
        if (!testPlan.containsTests()) {
          log.warn("[project={}] No tests found", project.id());
          return TestResults.NO_TESTS_FOUND;
        }

        launcher.execute(testPlan);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader);
    }

    final TestExecutionSummary summary = summaryListener.getSummary();
    final Duration duration = Duration.between(
        Instant.ofEpochMilli(summary.getTimeStarted()),
        Instant.ofEpochMilli(summary.getTimeFinished())
    );
    log.info("[project={}] Tests finished in {}, succeeded {}, failed {}, skipped {}",
        project.id(),
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

  private static URLClassLoader createModifiedClassLoader(
      final ClassLoader parent,
      final Path... paths
  ) throws MalformedURLException {
    final var additionalTestClasspathEntries = new URL[paths.length];
    for (int i = 0; i < paths.length; i++) {
      final URL url = paths[i].toUri().toURL();
      additionalTestClasspathEntries[i] = url;
    }

    return URLClassLoader.newInstance(additionalTestClasspathEntries, parent);
  }
}
