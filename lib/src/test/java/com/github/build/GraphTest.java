package com.github.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.github.build.deps.Coordinates;
import com.github.build.deps.graph.Graph;
import com.github.build.deps.graph.GraphPath;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
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

  @DisplayName("Test Spring Boot Starter Tomcat resolved dependencies")
  @Test
  void testSpringBootStarterTomcatResolvedDependencies() {
    final var graph = new Graph();
    graph.add(
        jakartaAnnotations,
        Set.of(),
        GraphPath.ROOT
    );
    graph.add(
        tomcatEmbedEl,
        Set.of(),
        GraphPath.ROOT
    );
    graph.add(
        tomcatAnnotations,
        Set.of(),
        new GraphPath(starterTomcat, tomcatEmbedCore)
    );
    graph.add(
        tomcatAnnotations,
        Set.of(),
        new GraphPath(starterTomcat, tomcatEmbedWebsocket, tomcatEmbedCore)
    );

    final Set<Coordinates> expected = Set.of(
        starterTomcat,
        jakartaAnnotations,
        tomcatEmbedCore,
        tomcatEmbedEl,
        tomcatEmbedWebsocket
    );
    assertThat(graph.resolve()).isEqualTo(expected);
  }
}
