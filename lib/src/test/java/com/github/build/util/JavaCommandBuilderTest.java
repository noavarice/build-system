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
    final var commandBuilder = JavaCommandBuilder
        .builder()
        .classpath(Path.of("/path").resolve("to").resolve("jar"))
        .mainClass("org.example.HelloWorld")
        .args(List.of("Hello, world!"))
        .build();
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
    final var commandBuilder = JavaCommandBuilder
        .builder()
        .agent(new JavaCommandBuilder.Agent(mockito, null))
        .agent(new JavaCommandBuilder.Agent(jacoco, "append=true"))
        .mainClass("org.example.Main")
        .build();
    final List<String> command = commandBuilder.toCommand();
    assertThat(command).isEqualTo(List.of(
        "java",
        "-javaagent:/path/to/mockito.jar",
        "-javaagent:/path/to/jacoco.jar=append=true",
        "org.example.Main"
    ));
  }

  @DisplayName("Check custom JDK path")
  @Test
  void testCustomJavaPath() {
    final var commandBuilder = JavaCommandBuilder
        .builder()
        .javaPath("/usr/lib/jvm/jdk-21/bin/java")
        .mainClass("org.example.Main")
        .build();
    final List<String> command = commandBuilder.toCommand();
    assertThat(command).isEqualTo(List.of(
        "/usr/lib/jvm/jdk-21/bin/java",
        "org.example.Main"
    ));
  }
}
