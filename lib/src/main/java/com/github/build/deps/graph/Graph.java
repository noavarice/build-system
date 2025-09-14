package com.github.build.deps.graph;

import static java.util.stream.Collectors.toUnmodifiableMap;

import com.github.build.deps.ArtifactCoordinates;
import com.github.build.deps.Coordinates;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author noavarice
 * @since 1.0.0
 */
public final class Graph {

  private static final Logger log = LoggerFactory.getLogger(Graph.class);

  private final Set<Node> nodes = new HashSet<>();

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

  public Graph resolve() {
    record State(
        GraphPath path,
        Set<Node> nodes,
        Set<ArtifactCoordinates> exclusions,
        Map<ArtifactCoordinates, String> overrides
    ) {

      State {
        Objects.requireNonNull(path);
        nodes = Set.copyOf(nodes);
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

        result.add(node.value, node.exclusions, currentState.path);
        queue.add(currentState.next(node));
      }
    } while (!queue.isEmpty());

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

    private final Set<Node> nodes = new HashSet<>();

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
