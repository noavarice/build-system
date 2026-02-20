package com.github.build.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * @author noavarice
 * @since 1.0.0
 */
@DisplayName("Java process command tests")
class JavaCommandBuilderTest {

  @DisplayName("Check creating simple command")
  @Test
  void testRunningSimpleJavaAppWorks() {
    final var commandBuilder = new JavaCommandBuilder(
        List.of(Path.of("/path").resolve("to").resolve("jar")),
        List.of(),
        List.of(),
        "org.example.HelloWorld",
        List.of("Hello, world!")
    );
    final List<String> command = commandBuilder.toCommand();
    assertThat(command).isEqualTo(
        List.of(
            "java",
            "-classpath",
            "/path/to/jar",
            "org.example.HelloWorld",
            "Hello, world!"
        )
    );
  }

  @DisplayName("Check creating command with agents")
  @Test
  void testCreatingBuilderWithAgents() {
    final var mockito = Path.of("/path").resolve("to").resolve("mockito.jar");
    final var jacoco = Path.of("/path").resolve("to").resolve("jacoco.jar");
    final var commandBuilder = new JavaCommandBuilder(
        List.of(),
        List.of(
            new JavaCommandBuilder.Agent(mockito, null),
            new JavaCommandBuilder.Agent(jacoco, "append=true")
        ),
        List.of(),
        "org.example.Main",
        List.of()
    );
    final List<String> command = commandBuilder.toCommand();
    assertThat(command).isEqualTo(List.of(
        "java",
        "-javaagent:/path/to/mockito.jar",
        "-javaagent:/path/to/jacoco.jar=append=true",
        "org.example.Main"
    ));
  }
}
