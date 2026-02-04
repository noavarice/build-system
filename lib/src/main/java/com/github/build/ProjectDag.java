package com.github.build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Directed acyclic graph representing dependencies between projects during build process.
 *
 * @author noavarice
 */
public final class ProjectDag {

  // each project corresponds to exactly one node in the graph
  // despite the number of other project that might depend on it
  private final Map<Project, Node> projectToNode = new HashMap<>();

  private final Set<Project> leaves = new HashSet<>();

  public void add(final Project parent, final Project child, final DependencyType dependencyType) {
    Objects.requireNonNull(parent);
    Objects.requireNonNull(child);
    // TODO: check cycles

    final Node parentNode = projectToNode.computeIfAbsent(parent, Node::new);
    final Node childNode = projectToNode.computeIfAbsent(child, Node::new);

    // check parent node, child node will have the same link type
    if (parentNode.children.get(child) != DependencyType.SEQUENTIAL || dependencyType
        != DependencyType.PARALLEL) {
      parentNode.children.put(child, dependencyType);
      childNode.parents.put(parent, dependencyType);
    }

    leaves.remove(parent);
    if (childNode.children.isEmpty()) {
      leaves.add(child);
    }
  }

  public int size() {
    return projectToNode.size();
  }

  public Set<Project> getDependent(final Project project) {
    Objects.requireNonNull(project);
    return Set.copyOf(projectToNode.get(project).parents.keySet());
  }

  public Map<Project, DependencyType> getDependencies(final Project dependentProject) {
    Objects.requireNonNull(dependentProject);
    return Map.copyOf(projectToNode.get(dependentProject).children);
  }

  public Set<Project> availableForBuild() {
    final var result = new HashSet<Project>();
    final var queue = new ArrayList<>(leaves);
    while (!queue.isEmpty()) {
      final Project project = queue.removeFirst();
      result.add(project);
      final Node node = Objects.requireNonNull(projectToNode.get(project));
      node.parents.forEach((parentProject, dependencyType) -> {
        if (dependencyType == DependencyType.PARALLEL) {
          final Node parentNode = Objects.requireNonNull(projectToNode.get(parentProject));
          final boolean allParallel = parentNode.children.values()
              .stream()
              .allMatch(lt -> lt == DependencyType.PARALLEL);
          if (allParallel) {
            queue.add(parentProject);
          }
        }
      });
    }

    return result;
  }

  @Override
  public String toString() {
    return projectToNode.keySet().toString();
  }

  public Set<Project> listAll() {
    return Set.copyOf(projectToNode.keySet());
  }

  private static final class Node {

    private final Project project;

    private final Map<Project, DependencyType> parents = new HashMap<>();

    private final Map<Project, DependencyType> children = new HashMap<>();

    private Node(final Project project) {
      this.project = project;
    }
  }

  /**
   * Type of dependency between projects relative to compilation process.
   */
  public enum DependencyType {
    SEQUENTIAL,
    PARALLEL,
  }
}
