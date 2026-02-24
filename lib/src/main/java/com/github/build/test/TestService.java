package com.github.build.test;

import static java.util.stream.Collectors.joining;

import com.github.build.Project;
import com.github.build.SourceSet;
import com.github.build.deps.Dependency;
import com.github.build.deps.DependencyConstraints;
import com.github.build.deps.DependencyService;
import com.github.build.deps.GroupArtifact;
import com.github.build.deps.GroupArtifactVersion;
import com.github.build.util.JavaCommandBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
  public TestResults withJUnit(
      final Path workdir,
      final Project project,
      final JUnitTestArgs args
  ) {
    Objects.requireNonNull(workdir);
    Objects.requireNonNull(project);
    Objects.requireNonNull(args);
    log.info("[project={}] Setting up tests", project.id());

    final TestRuntime testRuntime = getTestRuntime(workdir, project, args);

    final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    try (final var classLoader = createModifiedClassLoader(
        args.testRuntimeParentClassLoader(),
        testRuntime.classpath()
    )) {
      // setting classpath for JUnit test engine search algorithm
      Thread.currentThread().setContextClassLoader(classLoader);
      @SuppressWarnings("unchecked")
      final var taskType = (Class<Function<JUnitTestTaskArgs, TestResults>>) classLoader.loadClass(
          "com.github.build.junit.JUnitTestTask"
      );
      final var taskConstructor = taskType.getDeclaredConstructor();
      final var task = taskConstructor.newInstance();
      final var taskArgs = new JUnitTestTaskArgs(testRuntime.classesDir());
      return task.apply(taskArgs);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } catch (final ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    } finally {
      Thread.currentThread().setContextClassLoader(originalClassLoader);
    }
  }

  // TODO: handle failed tests
  // TODO: pass environment and other JVM properties
  public TestResults withJUnitAsProcess(
      final Path workdir,
      final Project project,
      final JUnitTestArgs args,
      final List<JavaCommandBuilder.Agent> agents,
      final List<String> systemProperties,
      final Duration timeout
  ) {
    Objects.requireNonNull(workdir);
    Objects.requireNonNull(project);
    Objects.requireNonNull(args);

    log.info("[project={}] Setting up tests", project.id());
    final TestRuntime testRuntime = getTestRuntime(workdir, project, args);

    // creating file for the test process to store results to (if finishes properly)
    final Path resultsPath;
    try {
      // TODO: rework this mechanism
      resultsPath = Files.createTempFile("test-results", ".properties");
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    final var commandBuilder = new JavaCommandBuilder(
        testRuntime.classpath(),
        agents,
        systemProperties,
        "com.github.build.junit.JUnitTestTask",
        List.of(testRuntime.classesDir().toString(), resultsPath.toString())
    );
    final ProcessBuilder processBuilder = new ProcessBuilder()
        .command(commandBuilder.toCommand())
        .directory(workdir.toFile())
        // TODO: handle child process logging
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD);

    final Process process;
    try {
      // TODO: handle child process death when parent is being killed
      process = processBuilder.start();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    log.debug("[project={}] Test process {} created", project.id(), process.pid());
    final boolean exited;
    try {
      exited = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }

    if (!exited) {
      log.error("[project={}] Test process {} timed out, destroying", project.id(), process.pid());
      process.destroyForcibly();
      throw new IllegalStateException();
    }

    if (process.exitValue() != 0) {
      log.error("[project={}] Test process {} exited with code {}",
          project.id(),
          process.pid(),
          process.exitValue()
      );
      throw new IllegalStateException();
    }

    final var properties = new Properties();
    try (final var is = Files.newInputStream(
        resultsPath,
        StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE
    )) {
      properties.load(is);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    return new TestResults(
        Long.parseLong(properties.getProperty("testsSucceededCount")),
        Long.parseLong(properties.getProperty("testsFailedCount")),
        Long.parseLong(properties.getProperty("testsSkippedCount"))
    );
  }

  private TestRuntime getTestRuntime(
      final Path workdir,
      final Project project,
      final JUnitTestArgs args
  ) {
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

    // adding build library runtime
    final var remoteDependencies = new ArrayList<GroupArtifactVersion>();
    final var testRuntimeClasspath = new ArrayList<>(args.buildRuntimeClasspath());

    // adding user classes
    testRuntimeClasspath.add(testClasses);
    testRuntimeClasspath.add(testResources);

    final DependencyConstraints constraints = project.testSourceSet().dependencyConstraints();
    // adding JUnit Platform
    {
      // TODO: allow overriding launcher version (e.g., via method arguments)
      final var ga = GroupArtifact.parse("org.junit.platform:junit-platform-launcher");
      final String launcherVersion = constraints.getConstraint(ga);
      remoteDependencies.add(ga.withVersion(launcherVersion));
    }

    addSourceSetRuntimeClasspath(
        workdir, project, project.testSourceSet(), testRuntimeClasspath, remoteDependencies
    );

    // TODO: figure out how we can configure logging when there's no SLF4J provider in the test classpath.
    // Remember that logging should be configurable and compatible
    // with how the rest of the build program logs its progress
    if (!classpathContainsSlf4jProvider(testRuntimeClasspath)) {
      final GroupArtifact fallbackGa = args.slf4jProviderFallback().groupArtifact();
      final String constrainedVersion = constraints.getConstraint(fallbackGa);
      final GroupArtifactVersion slf4jProviderFallback = constrainedVersion != null
          ? fallbackGa.withVersion(constrainedVersion)
          : args.slf4jProviderFallback();
      log.debug("Test runtime classpath has no SLF4J provider, adding fallback provider {}",
          slf4jProviderFallback
      );
      remoteDependencies.addFirst(slf4jProviderFallback);
    }

    if (!remoteDependencies.isEmpty()) {
      final Set<GroupArtifactVersion> artifacts = dependencyService.resolveTransitive(
          remoteDependencies,
          // TODO: should we add constraints from main source set here too?
          project.testSourceSet().dependencyConstraints()
      );
      final Map<GroupArtifactVersion, Path> localArtifacts = dependencyService.fetchToLocal(
          artifacts
      );
      testRuntimeClasspath.addAll(localArtifacts.values());
    }

    if (log.isDebugEnabled()) {
      final String prettyPrintedClasspath = testRuntimeClasspath
          .stream()
          .map(Object::toString)
          .collect(joining(System.lineSeparator(), "[", "]"));
      log.debug("Run tests with classpath {}", prettyPrintedClasspath);
    }
    return new TestRuntime(testClasses, testRuntimeClasspath);
  }

  private record TestRuntime(Path classesDir, List<Path> classpath) {

    private TestRuntime {
      Objects.requireNonNull(classesDir);
      classpath = List.copyOf(classpath);
    }
  }

  private boolean classpathContainsSlf4jProvider(final Collection<Path> classpath) {
    final URL[] urls = classpath
        .stream()
        .map(path -> {
          try {
            return path.toUri().toURL();
          } catch (final MalformedURLException e) {
            throw new IllegalStateException(e);
          }
        })
        .toArray(URL[]::new);
    final ClassLoader parent = ClassLoader.getPlatformClassLoader();
    try (final var classLoader = new URLClassLoader(urls, parent)) {
      try {
        classLoader.loadClass("org.slf4j.Logger");
      } catch (final ClassNotFoundException e) {
        // no SLF4J API at all
        return false;
      }

      final Class<?> slf4jServiceProviderClass;
      try {
        slf4jServiceProviderClass = classLoader.loadClass("org.slf4j.spi.SLF4JServiceProvider");
      } catch (final ClassNotFoundException e) {
        // probably SLF4J up to version 1.7.x
        return false;
      }
      final var serviceLoader = ServiceLoader.load(slf4jServiceProviderClass, classLoader);
      return serviceLoader.iterator().hasNext();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void addSourceSetRuntimeClasspath(
      final Path workdir,
      final Project project,
      final SourceSet sourceSet,
      final Collection<Path> classpath,
      final List<GroupArtifactVersion> remoteDependencies
  ) {

    for (final Dependency dependency : sourceSet.runtimeClasspath()) {
      switch (dependency) {
        case Dependency.OnProject onProject -> {
          final Project dependingProject = onProject.project();
          final Path jarPath = workdir
              .resolve(dependingProject.path())
              .resolve(dependingProject.artifactLayout().rootDir())
              // TODO: customize JAR path/filename
              .resolve(dependingProject.id() + ".jar");
          classpath.add(jarPath);
        }
        case Dependency.OnSourceSet onSourceSet -> {
          final Path classesDir = workdir
              .resolve(project.path())
              .resolve(project.artifactLayout().rootDir())
              .resolve(project.artifactLayout().classesDir())
              .resolve(onSourceSet.sourceSet().id().value());
          final Path resourcesDir = workdir
              .resolve(project.path())
              .resolve(project.artifactLayout().rootDir())
              .resolve(project.artifactLayout().resourcesDir())
              .resolve(onSourceSet.sourceSet().id().value());
          classpath.add(classesDir);
          classpath.add(resourcesDir);
          addSourceSetRuntimeClasspath(
              workdir, project, onSourceSet.sourceSet(), classpath, remoteDependencies
          );
        }
        case Dependency.Jar file -> classpath.add(file.path());
        case Dependency.Remote.WithVersion withVersion -> remoteDependencies.add(withVersion.gav());
        case Dependency.Remote.WithoutVersion withoutVersion -> {
          final DependencyConstraints constraints = sourceSet.dependencyConstraints();
          @Nullable
          final String version = constraints.getConstraint(withoutVersion.ga());
          if (version == null) {
            log.error("Dependency has no version and no associated source set-wise constraint");
            throw new IllegalArgumentException();
          }

          remoteDependencies.add(withoutVersion.ga().withVersion(version));
        }
      }
    }
  }

  private static URLClassLoader createModifiedClassLoader(
      final ClassLoader parent,
      final Collection<Path> paths
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
    return new URLClassLoader(additionalTestClasspathEntries, parent);
  }
}
