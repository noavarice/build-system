package com.github.build;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.github.build.deps.Coordinates;
import com.github.build.deps.graph.Graph;
import com.github.build.deps.graph.GraphPath;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * @author noavarice
 * @since 1.0.0
 */
@DisplayName("Dependency graph model test")
class GraphTest {

  @DisplayName("Test Spring Boot Starter Tomcat graph")
  @TestFactory
  DynamicTest[] testSpringBootStarterTomcatGraph() {
    final var starterTomcat = Coordinates.parse(
        "org.springframework.boot:spring-boot-starter-tomcat:jar:3.2.5"
    );
    final var embedCore = Coordinates.parse(
        "org.apache.tomcat.embed:tomcat-embed-core:jar:10.1.20"
    );
    final var embedEl = Coordinates.parse(
        "org.apache.tomcat.embed:tomcat-embed-el:jar:10.1.20"
    );
    final var embedWebsocket = Coordinates.parse(
        "org.apache.tomcat.embed:tomcat-embed-websocket:jar:10.1.20"
    );
    final var annotations = Coordinates.parse(
        "org.apache.tomcat:tomcat-annotations-api:jar:10.1.20"
    );

    final var graph = new Graph();

    final var annotationsPath = new GraphPath(List.of(starterTomcat, embedWebsocket, embedCore));
    graph.add(annotations, Set.of(), annotationsPath);

    final var elPath = new GraphPath(starterTomcat);
    graph.add(embedEl, Set.of(), elPath);

    return new DynamicTest[]{
        dynamicTest(
            "Check annotations dependency",
            () -> assertTrue(graph.contains(annotationsPath.addLast(annotations)))
        ),
        dynamicTest(
            "Check El dependency",
            () -> assertTrue(graph.contains(elPath.addLast(embedEl)))
        ),
    };
  }
}
