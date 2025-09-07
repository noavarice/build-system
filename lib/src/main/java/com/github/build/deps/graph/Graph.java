package com.github.build.deps.graph;

import com.github.build.deps.ArtifactCoordinates;
import com.github.build.deps.Coordinates;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author noavarice
 * @since 1.0.0
 */
public final class Graph {

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
