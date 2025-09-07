package com.github.build.deps.graph;

import static java.util.stream.Collectors.joining;

import com.github.build.deps.Coordinates;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author noavarice
 * @since 1.0.0
 */
public final class GraphPath implements Iterable<Coordinates> {

  private final List<Coordinates> nodes;

  public GraphPath(final Coordinates value) {
    this(List.of(value));
  }

  public GraphPath(final List<Coordinates> nodes) {
    this.nodes = List.copyOf(nodes);
  }

  public GraphPath addLast(final Coordinates node) {
    final var result = new ArrayList<>(nodes);
    result.add(node);
    return new GraphPath(result);
  }

  public Coordinates getLast() {
    return nodes.getLast();
  }

  @Override
  public String toString() {
    return nodes
        .stream()
        .map(Coordinates::toString)
        .collect(joining(" -> "));
  }

  @Override
  public Iterator<Coordinates> iterator() {
    return nodes.stream().iterator();
  }
}
