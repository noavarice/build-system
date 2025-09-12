package com.github.build;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
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

  private final Coordinates starterTomcat = Coordinates.parse(
      "org.springframework.boot:spring-boot-starter-tomcat:jar:3.2.5"
  );

  private final Coordinates tomcatEmbedCore = Coordinates.parse(
      "org.apache.tomcat.embed:tomcat-embed-core:jar:10.1.20"
  );

  private final Coordinates tomcatEmbedEl = Coordinates.parse(
      "org.apache.tomcat.embed:tomcat-embed-el:jar:10.1.20"
  );

  private final Coordinates tomcatEmbedWebsocket = Coordinates.parse(
      "org.apache.tomcat.embed:tomcat-embed-websocket:jar:10.1.20"
  );

  private final Coordinates tomcatAnnotations = Coordinates.parse(
      "org.apache.tomcat:tomcat-annotations-api:jar:10.1.20"
  );

  private final Coordinates jakartaAnnotations = Coordinates.parse(
      "jakarta.annotation:jakarta.annotation-api:2.1.1"
  );

  @DisplayName("Test Spring Boot Starter Tomcat graph")
  @TestFactory
  DynamicTest[] testSpringBootStarterTomcatGraph() {
    final var graph = new Graph();

    final var annotationsPath = new GraphPath(List.of(starterTomcat, tomcatEmbedWebsocket,
        tomcatEmbedCore));
    graph.add(tomcatAnnotations, Set.of(), annotationsPath);

    final var elPath = new GraphPath(starterTomcat);
    graph.add(tomcatEmbedEl, Set.of(), elPath);

    return new DynamicTest[]{
        dynamicTest(
            "Check annotations dependency",
            () -> assertTrue(graph.contains(annotationsPath.addLast(tomcatAnnotations)))
        ),
        dynamicTest(
            "Check El dependency",
            () -> assertTrue(graph.contains(elPath.addLast(tomcatEmbedEl)))
        ),
    };
  }

  @DisplayName("Test Spring Boot Starter Tomcat resolved graph")
  @TestFactory
  DynamicTest[] testSpringBootStarterTomcatResolvedDependencies() {
    final var graph = new Graph();
    graph.add(
        jakartaAnnotations,
        Set.of(),
        new GraphPath(starterTomcat)
    );
    graph.add(
        tomcatEmbedEl,
        Set.of(),
        new GraphPath(starterTomcat)
    );
    graph.add(
        tomcatEmbedCore,
        Set.of(tomcatAnnotations.artifactCoordinates()),
        new GraphPath(starterTomcat)
    );
    graph.add(
        tomcatAnnotations,
        Set.of(),
        new GraphPath(starterTomcat, tomcatEmbedCore)
    );
    graph.add(
        tomcatEmbedWebsocket,
        Set.of(tomcatAnnotations.artifactCoordinates()),
        new GraphPath(starterTomcat)
    );
    graph.add(
        tomcatAnnotations,
        Set.of(),
        new GraphPath(starterTomcat, tomcatEmbedWebsocket, tomcatEmbedCore)
    );

    final var tomcatAnnotationsWebsocketPath = new GraphPath(
        starterTomcat,
        tomcatEmbedWebsocket,
        tomcatEmbedCore,
        tomcatAnnotations
    );
    assumeTrue(graph.contains(tomcatAnnotationsWebsocketPath));

    final var tomcatAnnotationsCorePath = new GraphPath(
        starterTomcat,
        tomcatEmbedCore,
        tomcatAnnotations
    );
    assumeTrue(graph.contains(tomcatAnnotationsCorePath));

    final Graph resolvedGraph = graph.resolve();

    return new DynamicTest[]{
        dynamicTest(
            "Check Jakarta Annotations dependency is present via Starter",
            () -> {
              final var path = new GraphPath(starterTomcat, jakartaAnnotations);
              assertTrue(resolvedGraph.contains(path));
            }
        ),
        dynamicTest(
            "Check Tomcat Embed El dependency is present via Starter",
            () -> {
              final var path = new GraphPath(starterTomcat, tomcatEmbedEl);
              assertTrue(resolvedGraph.contains(path));
            }
        ),
        dynamicTest(
            "Check Tomcat Embed Core dependency is present via Starter",
            () -> {
              final var path = new GraphPath(starterTomcat, tomcatEmbedCore);
              assertTrue(resolvedGraph.contains(path));
            }
        ),
        dynamicTest(
            "Check Tomcat Embed Core dependency is not present via Starter -> Websocket",
            () -> {
              final var path = new GraphPath(starterTomcat, tomcatEmbedWebsocket, tomcatEmbedCore);
              assertFalse(resolvedGraph.contains(path));
            }
        ),
        dynamicTest(
            "Check Tomcat annotations dependency is not present via Starter -> Websocket -> Core",
            () -> assertFalse(resolvedGraph.contains(tomcatAnnotationsWebsocketPath))
        ),
        dynamicTest(
            "Check Tomcat annotations dependency is not present via Starter -> Core",
            () -> assertFalse(resolvedGraph.contains(tomcatAnnotationsCorePath))
        ),
    };
  }
}
