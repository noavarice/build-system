package com.github.build.deps.graph;

import static java.util.stream.Collectors.joining;

import com.github.build.deps.Coordinates;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * @author noavarice
 * @since 1.0.0
 */
public final class GraphPath implements Iterable<Coordinates> {

  public static final GraphPath ROOT = new GraphPath();

  private final List<Coordinates> nodes;

  public GraphPath(final Coordinates... values) {
    this(List.of(values));
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

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final GraphPath that = (GraphPath) o;
    return Objects.equals(nodes, that.nodes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nodes);
  }
}
