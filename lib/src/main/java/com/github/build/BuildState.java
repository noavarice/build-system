package com.github.build;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author noavarice
 */
public final class BuildState {

  private static final Logger log = LoggerFactory.getLogger(BuildState.class);

  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  private boolean buildFailed = false;

  private final Map<Project, ProjectState> projectToState;

  private final ProjectDag dag;

  public BuildState(final ProjectDag dag) {
    this.dag = Objects.requireNonNull(dag);
    this.projectToState = new HashMap<>();
    for (final Project project : dag.listAll()) {
      projectToState.put(project, new ProjectState());
    }
  }

  public void markProjectAsBuilding(final Project project) {
    final var writeLock = lock.writeLock();
    writeLock.lock();
    try {
      final ProjectState state = Objects.requireNonNull(projectToState.get(project));
      if (state.buildStarted) {
        log.warn("Build for project {} is already started", project);
      } else {
        state.buildStarted = true;
      }
    } finally {
      writeLock.unlock();
    }
  }

  private static final class ProjectState {

    private boolean buildStarted = false;

    private boolean jarCreated = false;
  }

  public Set<Project> findUnlockedBy(final Project project) {
    Objects.requireNonNull(project);
    final var readLock = lock.readLock();
    readLock.lock();
    try {
      if (buildFailed) {
        log.debug("Cannot schedule building next projects - build failed");
        return Set.of();
      }

      final var result = new HashSet<Project>();
      final Set<Project> dependentProjects = dag.getDependent(project);
      for (final Project dependentProject : dependentProjects) {
        final Map<Project, ProjectDag.DependencyType> dependencies = dag.getDependencies(
            dependentProject
        );

        boolean availableForBuild = true;
        for (final Project dependencyProject : dependencies.keySet()) {
          final ProjectDag.DependencyType type = Objects.requireNonNull(
              dependencies.get(dependencyProject)
          );
          if (type == ProjectDag.DependencyType.SEQUENTIAL) {
            final ProjectState state = Objects.requireNonNull(
                projectToState.get(dependencyProject)
            );
            if (!state.jarCreated) {
              availableForBuild = false;
              break;
            }
          }
        }

        if (availableForBuild) {
          result.add(dependentProject);
        }
      }

      return result;
    } finally {
      readLock.unlock();
    }
  }

  public void markProjectAsJarCreated(final Project project) {
    final var writeLock = lock.writeLock();
    writeLock.lock();
    try {
      final ProjectState state = Objects.requireNonNull(projectToState.get(project));
      state.jarCreated = true;
    } finally {
      writeLock.unlock();
    }
  }

  public void markBuildAsFailed() {
    final var writeLock = lock.writeLock();
    writeLock.lock();
    buildFailed = true;
    writeLock.unlock();
  }
}
