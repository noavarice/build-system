package com.github.build.deps.graph;

import static java.util.stream.Collectors.toUnmodifiableMap;

import com.github.build.deps.ArtifactCoordinates;
import com.github.build.deps.Coordinates;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
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
      final Coordinates coordinates,
      final Set<ArtifactCoordinates> exclusions,
      final GraphPath path
  ) {
    Objects.requireNonNull(coordinates);
    Objects.requireNonNull(exclusions);
    Objects.requireNonNull(path);

    var currentNodes = this.nodes;
    for (final Coordinates value : path) {
      boolean found = false;
      for (final Node currentNode : currentNodes) {
        if (currentNode.value.equals(value)) {
          currentNodes = currentNode.nodes;
          found = true;
          break;
        }
      }

      if (!found) {
        final var missingTransitiveNode = new Node(value, exclusions);
        currentNodes.add(missingTransitiveNode);
        currentNodes = missingTransitiveNode.nodes;
      }
    }

    final var newNode = new Node(coordinates, exclusions);
    currentNodes.add(newNode);
  }

  public boolean removeLast(final GraphPath path) {
    Objects.requireNonNull(path);
    if (path.isRoot()) {
      return false;
    }

    var currentNodes = this.nodes;
    for (final Coordinates value : path.removeLast()) {
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

    final Coordinates toRemove = path.getLast();
    return currentNodes.removeIf(node -> node.value.equals(toRemove));
  }

  public boolean contains(final GraphPath path) {
    Objects.requireNonNull(path);

    var currentNodes = nodes;
    for (final Coordinates value : path) {
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

  public Set<GraphPath> findAllPaths(final Coordinates coordinates) {
    Objects.requireNonNull(coordinates);
    return findAllPaths(c -> c.equals(coordinates));
  }

  public Set<GraphPath> findAllPaths(final ArtifactCoordinates coordinates) {
    Objects.requireNonNull(coordinates);
    return findAllPaths(c -> c.artifactCoordinates().equals(coordinates));
  }

  private Set<GraphPath> findAllPaths(final Predicate<Coordinates> condition) {
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
        final Coordinates artifact = node.value;
        if (condition.test(artifact)) {
          result.add(state.path.addLast(artifact));
        } else if (!node.nodes.isEmpty()) {
          queue.addLast(state.next(node));
        }
      }
    }

    return result;
  }

  public Graph resolve() {
    record State(
        GraphPath path,
        List<Node> nodes,
        Set<ArtifactCoordinates> exclusions,
        Map<ArtifactCoordinates, String> overrides
    ) {

      State {
        Objects.requireNonNull(path);
        nodes = List.copyOf(nodes);
        exclusions = Set.copyOf(exclusions);
        overrides = Map.copyOf(overrides);
      }

      State next(final Node node) {
        final var nextExclusions = new HashSet<>(exclusions);
        nextExclusions.addAll(node.exclusions);

        final var nextOverrides = new HashMap<>(overrides);
        final var currentLevel = nodes
            .stream()
            .collect(toUnmodifiableMap(n -> n.value.artifactCoordinates(), n -> n.value.version()));
        nextOverrides.putAll(currentLevel);

        return new State(path.addLast(node.value), node.nodes, nextExclusions, nextOverrides);
      }
    }

    final var queue = new ArrayList<State>();
    queue.add(new State(GraphPath.ROOT, nodes, Set.of(), Map.of()));

    final var result = new Graph();

    final Map<ArtifactCoordinates, List<GraphPath>> artifactPaths = new HashMap<>();
    do {
      final State currentState = queue.removeFirst();
      for (final Node node : currentState.nodes) {
        final ArtifactCoordinates artifact = node.value.artifactCoordinates();
        final boolean excluded = currentState.exclusions.contains(artifact);
        if (excluded) {
          log.info("{} is excluded", artifact);
          continue;
        }

        final boolean overridden = currentState.overrides.containsKey(artifact);
        final String currentVersion = node.value.version();
        if (overridden) {
          log.info("{} version {} is overridden by version {}",
              artifact,
              currentVersion,
              currentState.overrides.get(artifact)
          );
          continue;
        }

        final List<GraphPath> paths = artifactPaths
            .computeIfAbsent(artifact, ignored -> new ArrayList<>());
        final Set<Coordinates> coordinates = paths
            .stream()
            .map(GraphPath::getLast)
            .collect(Collectors.toUnmodifiableSet());
        if (!coordinates.contains(node.value)) {
          final GraphPath anotherPath = currentState.path.addLast(node.value);
          paths.add(anotherPath);
        }
        result.add(node.value, node.exclusions, currentState.path);
        queue.add(currentState.next(node));
      }
    } while (!queue.isEmpty());

    // resolving conflicts
    for (final ArtifactCoordinates artifact : artifactPaths.keySet()) {
      final var paths = artifactPaths.get(artifact);
      if (paths.size() < 2) {
        continue;
      }

      log.info("Found conflicts for {}: {}", artifact, paths);
      GraphPath picked = paths.getFirst();
      for (int i = 1; i < paths.size(); i++) {
        final GraphPath path = paths.get(i);
        if (path.isDuplicateWith(picked)) {
          log.info("Path {} is duplicate with {}, picking {} as latest", path, picked, path);
          result.removeLast(picked);
          picked = path;
        } else {
          log.info("Path {} conflicts with {}, picking {} as earliest", path, picked, picked);
          result.removeLast(path);
        }
      }
    }

    return result;
  }

  public Set<Coordinates> toDependencies() {
    if (nodes.isEmpty()) {
      return Set.of();
    }

    final var queue = new ArrayList<>(nodes);
    final var result = new HashSet<Coordinates>();

    do {
      final Node currentNode = queue.removeFirst();
      result.add(currentNode.value);
      queue.addAll(currentNode.nodes);
    } while (!queue.isEmpty());

    return Set.copyOf(result);
  }

  private static final class Node {

    private final Coordinates value;

    private final Set<ArtifactCoordinates> exclusions;

    private final List<Node> nodes = new ArrayList<>();

    private Node(final Coordinates value, final Set<ArtifactCoordinates> exclusions) {
      this.value = Objects.requireNonNull(value);
      this.exclusions = Set.copyOf(exclusions);
    }

    @Override
    public String toString() {
      return value.toString();
    }
  }
}
