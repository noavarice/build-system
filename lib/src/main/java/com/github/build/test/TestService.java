package com.github.build.test;

import static java.util.stream.Collectors.joining;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathRoots;

import com.github.build.Project;
import com.github.build.deps.Dependency;
import com.github.build.deps.DependencyConstraints;
import com.github.build.deps.DependencyService;
import com.github.build.deps.GroupArtifactVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
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
 */
public final class TestService {

  private static final Logger log = LoggerFactory.getLogger(TestService.class);

  private final DependencyService dependencyService;

  public TestService(final DependencyService dependencyService) {
    this.dependencyService = Objects.requireNonNull(dependencyService);
  }

  // TODO: handle failed tests
  public TestResults withJUnit(final Path workdir, final Project project) {
    Objects.requireNonNull(workdir);
    Objects.requireNonNull(project);
    log.info("[project={}] Setting up tests", project.id());

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

    final var testRuntimeClasspath = new HashSet<Path>();
    // adding user classes
    testRuntimeClasspath.add(mainClasses);
    testRuntimeClasspath.add(testClasses);

    // adding JUnit Platform
//    {
//      final Set<GroupArtifactVersion> artifacts = dependencyService.resolveTransitive(
//          GroupArtifactVersion.parse("org.junit.platform:junit-platform-launcher:1.13.4")
//      );
//      final Map<GroupArtifactVersion, Path> localArtifacts = dependencyService.fetchToLocal(
//          artifacts
//      );
//
//      testRuntimeClasspath.addAll(localArtifacts.values());
//    }

    for (final Dependency dependency : project.testSourceSet().runtimeClasspath()) {
      switch (dependency) {
        case Dependency.OnProject onProject -> {
          final Project dependingProject = onProject.project();
          final Path mainSourceSetClassesDir = workdir
              .resolve(dependingProject.path())
              .resolve(dependingProject.artifactLayout().rootDir())
              .resolve(dependingProject.artifactLayout().classesDir())
              .resolve(dependingProject.mainSourceSet().id().toString());
          testRuntimeClasspath.add(mainSourceSetClassesDir);
        }
        case Dependency.OnSourceSet onSourceSet -> {
          final Path sourceSetClassesDir = workdir
              .resolve(project.path())
              .resolve(project.artifactLayout().rootDir())
              .resolve(project.artifactLayout().classesDir())
              .resolve(onSourceSet.sourceSet().id().toString());
          testRuntimeClasspath.add(sourceSetClassesDir);
        }
        case Dependency.Jar file -> testRuntimeClasspath.add(file.path());
        case Dependency.Remote.WithVersion withVersion -> {
          final GroupArtifactVersion gav = withVersion.gav();
          final Set<GroupArtifactVersion> artifacts = dependencyService.resolveTransitive(gav);
          final Map<GroupArtifactVersion, Path> localArtifacts = dependencyService.fetchToLocal(
              artifacts
          );
          testRuntimeClasspath.addAll(localArtifacts.values());
        }
        case Dependency.Remote.WithoutVersion withoutVersion -> {
          final DependencyConstraints constraints = project.testSourceSet().dependencyConstraints();
          @Nullable
          final String version = constraints.getConstraint(withoutVersion.ga());
          if (version == null) {
            log.error("Dependency has no version and no associated source set-wise constraint");
            throw new IllegalStateException();
          }

          final GroupArtifactVersion gav = withoutVersion.ga().withVersion(version);
          final Set<GroupArtifactVersion> artifacts = dependencyService.resolveTransitive(gav);
          final Map<GroupArtifactVersion, Path> localArtifacts = dependencyService.fetchToLocal(
              artifacts
          );
          testRuntimeClasspath.addAll(localArtifacts.values());
        }
      }
    }

    if (log.isDebugEnabled()) {
      final String prettyPrintedClasspath = testRuntimeClasspath
          .stream()
          .map(Object::toString)
          .collect(joining(System.lineSeparator(), "[", "]"));
      log.debug("Run tests with classpath {}", prettyPrintedClasspath);
    }

    try (final var classLoader = createModifiedClassLoader(
        originalClassLoader.getParent(),
        testRuntimeClasspath
    )) {
      Thread.currentThread().setContextClassLoader(classLoader);
      final var summaryListener = new SummaryGeneratingListener();
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
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader);
    }
  }

  private static URLClassLoader createModifiedClassLoader(
      final ClassLoader parent,
      final Set<Path> paths
  ) throws MalformedURLException {
    final URL[] additionalTestClasspathEntries = paths
        .stream()
        .map(path -> {
          try {
            return path.toUri().toURL();
          } catch (final MalformedURLException e) {
            throw new UncheckedIOException(e);
          }
        })
        .toArray(URL[]::new);
    return URLClassLoader.newInstance(additionalTestClasspathEntries, parent);
  }
}
