package com.github.build.deps.graph;

import com.github.build.deps.GroupArtifact;
import com.github.build.deps.GroupArtifactVersion;
import com.github.build.deps.MavenVersion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author noavarice
 * @since 1.0.0
 */
public final class Graph {

  private static final Logger log = LoggerFactory.getLogger(Graph.class);

  private final List<Node> nodes = new ArrayList<>();

  public void add(
      final GraphValue value,
      final Set<GroupArtifact> exclusions,
      final GraphPath path
  ) {
    Objects.requireNonNull(value);
    Objects.requireNonNull(exclusions);
    Objects.requireNonNull(path);

    var currentPath = GraphPath.ROOT;
    var currentNodes = this.nodes;

    final var exclusionsDuringPath = new HashSet<>();
    final var excludedBy = new HashSet<GraphPath>();

    for (final GraphValue pathPart : path) {
      boolean found = false;
      for (final Node currentNode : currentNodes) {
        if (currentNode.value.equals(pathPart)) {
          currentPath = currentPath.addLast(pathPart);
          if (exclusionsDuringPath.contains(pathPart.groupArtifact())) {
            excludedBy.add(currentPath);
          }

          exclusionsDuringPath.addAll(currentNode.exclusions);
          currentNodes = currentNode.nodes;
          found = true;
          break;
        }
      }

      if (!found) {
        throw new IllegalArgumentException("Path not found");
      }
    }

    if (exclusionsDuringPath.contains(value.groupArtifact())) {
      excludedBy.add(path);
    }

    final var newNode = new Node(value, exclusions, excludedBy);
    currentNodes.add(newNode);
  }

  public boolean removeLast(final GraphPath path) {
    Objects.requireNonNull(path);
    if (path.isRoot()) {
      return false;
    }

    var currentNodes = this.nodes;
    for (final GraphValue value : path.removeLast()) {
      boolean found = false;
      for (final Node currentNode : currentNodes) {
        if (currentNode.value.equals(value)) {
          currentNodes = currentNode.nodes;
          found = true;
          break;
        }
      }

      if (!found) {
        return false;
      }
    }

    final GraphValue toRemove = path.getLast();
    return currentNodes.removeIf(node -> node.value.equals(toRemove));
  }

  // TODO: should this method account for exclusions?
  public boolean contains(final GraphPath path) {
    Objects.requireNonNull(path);

    var currentNodes = nodes;
    for (final GraphValue value : path) {
      boolean found = false;
      for (final Node currentNode : currentNodes) {
        if (currentNode.value.equals(value)) {
          currentNodes = currentNode.nodes;
          found = true;
          break;
        }
      }

      if (!found) {
        return false;
      }
    }

    return true;
  }

  public Set<GraphPath> findAllPaths(final GroupArtifact groupArtifact) {
    Objects.requireNonNull(groupArtifact);
    return findAllPaths(c -> c.groupArtifact().equals(groupArtifact));
  }

  private Set<GraphPath> findAllPaths(final Predicate<GraphValue> condition) {
    Objects.requireNonNull(condition);

    record State(GraphPath path, List<Node> nodes) {

      State {
        Objects.requireNonNull(path);
        nodes = List.copyOf(nodes);
      }

      State next(final Node node) {
        return new State(path.addLast(node.value), node.nodes);
      }
    }

    final var queue = new ArrayList<State>();
    queue.addLast(new State(GraphPath.ROOT, nodes));

    final var result = new HashSet<GraphPath>();
    while (!queue.isEmpty()) {
      final State state = queue.removeFirst();
      for (final Node node : state.nodes) {
        final GraphValue gav = node.value;
        if (condition.test(gav)) {
          result.add(state.path.addLast(gav));
        } else if (!node.nodes.isEmpty()) {
          queue.addLast(state.next(node));
        }
      }
    }

    return result;
  }

  public Graph resolve() {
    record State(GraphPath path, List<Node> nodes) {

      State {
        Objects.requireNonNull(path);
        nodes = List.copyOf(nodes);
      }

      State next(final Node node) {
        return new State(path.addLast(node.value), node.nodes);
      }
    }

    final var queue = new ArrayList<State>();
    queue.add(new State(GraphPath.ROOT, nodes));

    final var result = new Graph();

    final Map<GroupArtifact, List<GraphPath>> artifactPaths = new HashMap<>();
    do {
      final State currentState = queue.removeFirst();
      for (final Node node : currentState.nodes) {
        final GroupArtifact groupArtifact = node.value.groupArtifact();
        final boolean excluded = !node.excludedBy.isEmpty();
        if (excluded) {
          if (log.isDebugEnabled()) {
            log.debug("{} is excluded by {}", groupArtifact, node.excludedBy);
          } else {
            log.debug("{} is excluded", groupArtifact);
          }
          continue;
        }

        final List<GraphPath> paths = artifactPaths
            .computeIfAbsent(groupArtifact, ignored -> new ArrayList<>());
        paths.add(currentState.path.addLast(node.value));
        result.add(node.value, node.exclusions, currentState.path);
        queue.add(currentState.next(node));
      }
    } while (!queue.isEmpty());

    // resolving conflicts
    for (final GroupArtifact groupArtifact : artifactPaths.keySet()) {
      final var paths = artifactPaths.get(groupArtifact);
      if (paths.size() < 2) {
        continue;
      }

      log.debug("Found conflicts for {}: {}", groupArtifact, paths);
      GraphPath picked = paths.getFirst();
      for (int i = 1; i < paths.size(); i++) {
        final GraphPath path = paths.get(i);
        if (path.isDuplicateWith(picked)) {
          log.debug("Path {} is duplicate with {}, picking {} as latest", path, picked, path);
          result.removeLast(picked);
          picked = path;
        } else {
          log.debug("Path {} conflicts with {}, picking {} as earliest", path, picked, picked);
          result.removeLast(path);
        }
      }
    }

    return result;
  }

  public Set<GroupArtifactVersion> toDependencies() {
    if (nodes.isEmpty()) {
      return Set.of();
    }

    final var queue = new ArrayList<>(nodes);
    final var result = new HashSet<GroupArtifactVersion>();

    do {
      final Node currentNode = queue.removeFirst();
      switch (currentNode.value.version()) {
        case MavenVersion.Exact exact ->
            result.add(currentNode.value.groupArtifact().withVersion(exact.value()));
        case MavenVersion.Range range ->
            throw new UnsupportedOperationException(); // FIXME: handle ranges
      }
      queue.addAll(currentNode.nodes);
    } while (!queue.isEmpty());

    return Set.copyOf(result);
  }

  private static final class Node {

    private final GraphValue value;

    private final Set<GroupArtifact> exclusions;

    private final Set<GraphPath> excludedBy;

    private final List<Node> nodes = new ArrayList<>();

    private Node(
        final GraphValue value,
        final Set<GroupArtifact> exclusions,
        final Set<GraphPath> excludedBy
    ) {
      this.value = Objects.requireNonNull(value);
      this.exclusions = Set.copyOf(exclusions);
      this.excludedBy = Set.copyOf(excludedBy);
    }

    @Override
    public String toString() {
      return value.toString();
    }
  }
}
