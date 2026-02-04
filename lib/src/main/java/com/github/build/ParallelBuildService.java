package com.github.build;

import com.github.build.deps.Dependency;
import com.github.build.test.JUnitTestArgs;
import com.github.build.test.TestResults;
import com.github.build.test.TestService;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author noavarice
 */
public final class ParallelBuildService {

  private static final Logger log = LoggerFactory.getLogger(ParallelBuildService.class);

  private final BuildService buildService;

  private final TestService testService;

  public ParallelBuildService(final BuildService buildService, final TestService testService) {
    this.buildService = buildService;
    this.testService = testService;
  }

  public void buildAll(final Path workdir, final Set<Project> projects, final BuildAllArgs args) {
    Objects.requireNonNull(workdir);
    Objects.requireNonNull(projects);
    Objects.requireNonNull(args);
    if (projects.isEmpty()) {
      return;
    }

    final ProjectDag dag = createDag(projects, true);
    final var eventBus = new BuildEventBus(Executors.newSingleThreadExecutor());

    final Map<Project, CompletableFuture<Void>> jobs = new ConcurrentHashMap<>();
    final var buildState = new BuildState(dag);
    final var cdl = new CountDownLatch(dag.size());
    final Function<Project, CompletableFuture<Void>> scheduleProjectBuild = project -> {
      final Runnable job = new DefaultBuildJob(
          buildService, testService, project, workdir, eventBus
      );
      buildState.markProjectAsBuilding(project);
      return CompletableFuture
          .runAsync(job, args.executorService())
          .thenRun(cdl::countDown);
    };
    eventBus.subscribe(event -> {
      switch (event) {
        case BuildEvent.BuildFailed ignored -> buildState.markBuildAsFailed();
        case BuildEvent.ProjectJarCreated projectJarCreated -> {
          final Project project = projectJarCreated.project();
          buildState.markProjectAsJarCreated(project);
          final Set<Project> nextProjects = buildState.findUnlockedBy(project);
          for (final Project nextProject : nextProjects) {
            jobs.computeIfAbsent(nextProject, scheduleProjectBuild);
          }
        }
      }
    });
    log.info("Building projects {}", dag);

    final Set<Project> independentProjects = dag.availableForBuild();
    for (final Project project : independentProjects) {
      jobs.computeIfAbsent(project, scheduleProjectBuild);
    }

    final Duration timeout = args.timeout();
    final boolean reachedZero;
    try {
      reachedZero = cdl.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }

    if (!reachedZero) {
      for (final Future<?> future : jobs.values()) {
        future.cancel(true);
      }

      // TODO: use proper exception
      throw new IllegalStateException("Build timed out");
    }
  }

  private static ProjectDag createDag(
      final Set<Project> projects,
      final boolean runTests
  ) {
    final var dag = new ProjectDag();

    final var queue = new ArrayList<>(projects);
    while (!queue.isEmpty()) {
      final Project project = queue.removeFirst();

      for (final Dependency dependency : project.mainSourceSet().compileClasspath()) {
        if (dependency instanceof Dependency.OnProject onProject) {
          final Project dependentOn = onProject.project();
          dag.add(project, dependentOn, ProjectDag.DependencyType.SEQUENTIAL);
          queue.addLast(dependentOn);
        }
      }

      final ProjectDag.DependencyType dependencyType = runTests
          ? ProjectDag.DependencyType.SEQUENTIAL
          : ProjectDag.DependencyType.PARALLEL;
      for (final Dependency dependency : project.mainSourceSet().runtimeClasspath()) {
        if (dependency instanceof Dependency.OnProject onProject) {
          final Project dependentOn = onProject.project();
          dag.add(project, dependentOn, dependencyType);
          queue.addLast(dependentOn);
        }
      }

      if (runTests) {
        for (final Dependency dependency : project.testSourceSet().compileClasspath()) {
          if (dependency instanceof Dependency.OnProject onProject) {
            final Project dependentOn = onProject.project();
            dag.add(project, dependentOn, ProjectDag.DependencyType.SEQUENTIAL);
            queue.addLast(dependentOn);
          }
        }

        for (final Dependency dependency : project.testSourceSet().runtimeClasspath()) {
          if (dependency instanceof Dependency.OnProject onProject) {
            final Project dependentOn = onProject.project();
            // sequential because we need to actually run test program,
            // so we need runtime dependency project to build in advance
            dag.add(project, dependentOn, ProjectDag.DependencyType.SEQUENTIAL);
            queue.addLast(dependentOn);
          }
        }
      }
    }

    return dag;
  }

  private static final class DefaultBuildJob implements Runnable {

    private final BuildService buildService;

    private final TestService testService;

    private final Project project;

    private final Path workdir;

    private final BuildEventPublisher eventPublisher;

    private DefaultBuildJob(
        final BuildService buildService,
        final TestService testService,
        final Project project,
        final Path workdir,
        final BuildEventPublisher eventPublisher
    ) {
      this.buildService = buildService;
      this.testService = testService;
      this.project = project;
      this.workdir = workdir;
      this.eventPublisher = eventPublisher;
    }

    @Override
    public void run() {
      try {
        final boolean mainCompiled = buildService.compileMain(workdir, project);
        if (!mainCompiled) {
          log.error("Build failed");
          eventPublisher.publish(new BuildEvent.BuildFailed());
          return;
        }

        buildService.copyResources(workdir, project, SourceSet.Id.MAIN);
        buildService.createJar(workdir, project);
        eventPublisher.publish(new BuildEvent.ProjectJarCreated(project));

        final boolean testCompiled = buildService.compileTest(workdir, project);
        if (!testCompiled) {
          log.error("Build failed");
          eventPublisher.publish(new BuildEvent.BuildFailed());
          return;
        }
        buildService.copyResources(workdir, project, SourceSet.Id.TEST);

        final String buildRuntimePathStr = System.getProperty("buildRuntimePath");
        final Path buildRuntimePath = Path.of(buildRuntimePathStr);
        final var testArgs = new JUnitTestArgs(
            Set.of(buildRuntimePath),
            ClassLoader.getPlatformClassLoader()
        );
        final TestResults results = testService.withJUnit(workdir, project, testArgs);
        if (results.testsFailedCount() > 0) {
          log.error("Build failed");
          eventPublisher.publish(new BuildEvent.BuildFailed());
        }
      } catch (final Exception e) {
        log.error("Build failed", e);
        eventPublisher.publish(new BuildEvent.BuildFailed());
      }
    }
  }
}
