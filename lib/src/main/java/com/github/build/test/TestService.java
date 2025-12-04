package com.github.build.test;

import static java.util.stream.Collectors.joining;

import com.github.build.Project;
import com.github.build.deps.Dependency;
import com.github.build.deps.DependencyConstraints;
import com.github.build.deps.DependencyService;
import com.github.build.deps.GroupArtifactVersion;
import com.github.build.util.ParentLastClassLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
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

    final Path testClasses = workdir
        .resolve(project.path())
        .resolve(project.artifactLayout().rootDir())
        .resolve(project.artifactLayout().classesDir())
        .resolve("test");
    final Path testResources = workdir
        .resolve(project.path())
        .resolve(project.artifactLayout().rootDir())
        .resolve(project.artifactLayout().resourcesDir())
        .resolve("test");

    final var testRuntimeClasspath = new HashSet<Path>();
    // adding user classes
    testRuntimeClasspath.add(testClasses);
    testRuntimeClasspath.add(testResources);

    // adding JUnit Platform
    {
      final Set<GroupArtifactVersion> artifacts = dependencyService.resolveTransitive(
          GroupArtifactVersion.parse("org.junit.platform:junit-platform-launcher:1.13.4")
      );
      final Map<GroupArtifactVersion, Path> localArtifacts = dependencyService.fetchToLocal(
          artifacts
      );

      testRuntimeClasspath.addAll(localArtifacts.values());
    }

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
          final Path mainSourceSetResourcesDir = workdir
              .resolve(dependingProject.path())
              .resolve(dependingProject.artifactLayout().rootDir())
              .resolve(dependingProject.artifactLayout().resourcesDir())
              .resolve(dependingProject.mainSourceSet().id().toString());
          testRuntimeClasspath.add(mainSourceSetResourcesDir);
        }
        case Dependency.OnSourceSet onSourceSet -> {
          final Path sourceSetClassesDir = workdir
              .resolve(project.path())
              .resolve(project.artifactLayout().rootDir())
              .resolve(project.artifactLayout().classesDir())
              .resolve(onSourceSet.sourceSet().id().toString());
          testRuntimeClasspath.add(sourceSetClassesDir);
          final Path sourceSetResourcesDir = workdir
              .resolve(project.path())
              .resolve(project.artifactLayout().rootDir())
              .resolve(project.artifactLayout().resourcesDir())
              .resolve(onSourceSet.sourceSet().id().toString());
          testRuntimeClasspath.add(sourceSetResourcesDir);
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

    final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    try (final var classLoader = createModifiedClassLoader(
        // TODO: consider isolating test classpath from build program classpath
        getClass().getClassLoader(),
        testRuntimeClasspath
    )) {
      // setting classpath for JUnit test engine search algorithm
      Thread.currentThread().setContextClassLoader(classLoader);
      @SuppressWarnings("unchecked")
      final var taskType = (Class<Function<Map<String, Object>, Map<String, Object>>>) classLoader.loadClass(
          "com.github.build.test.JUnitTestTask"
      );
      final var taskConstructor = taskType.getDeclaredConstructor();
      final var task = taskConstructor.newInstance();
      final Map<String, Object> args = Map.of("testClassesDir", testClasses);
      final Map<String, Object> result = task.apply(args);
      return new TestResults(
          (Long) result.getOrDefault("succeeded", 0L),
          (Long) result.getOrDefault("failed", 0L),
          (Long) result.getOrDefault("skipped", 0L)
      );
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } catch (final ReflectiveOperationException e) {
      throw new IllegalStateException(e);
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
    return new ParentLastClassLoader(additionalTestClasspathEntries, parent);
  }
}
