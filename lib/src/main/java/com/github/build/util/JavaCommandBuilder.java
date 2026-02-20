package com.github.build.util;

import static java.util.stream.Collectors.joining;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.lang.model.SourceVersion;
import org.jspecify.annotations.Nullable;

/**
 * Command builder for Java processes.
 *
 * @author noavarice
 * @since 1.0.0
 */
public record JavaCommandBuilder(
    List<Path> classpath,
    List<Agent> agents,
    List<String> systemProperties,
    @Nullable String mainClass,
    List<String> args
) {

  public JavaCommandBuilder {
    classpath = List.copyOf(classpath);
    systemProperties = List.copyOf(systemProperties);

    agents = List.copyOf(agents);

    if (mainClass != null) {
      if (mainClass.isBlank()) {
        throw new IllegalArgumentException();
      }

      mainClass = mainClass.strip();
      if (!SourceVersion.isName(mainClass)) {
        throw new IllegalArgumentException();
      }
    }

    args = List.copyOf(args);
  }

  public record Agent(Path jarPath, @Nullable String options) {

    public Agent {
      Objects.requireNonNull(jarPath);

      if (options != null) {
        if (options.isBlank()) {
          throw new IllegalArgumentException();
        }

        options = options.strip();
      }
    }
  }

  public List<String> toCommand() {
    final var command = new ArrayList<String>();
    command.add("java");
    if (!classpath.isEmpty()) {
      final String classpath = this.classpath
          .stream()
          .map(Path::toString)
          .collect(joining(":"));
      command.add("-classpath");
      command.add(classpath);
    }

    for (final Agent agent : agents) {
      final var sb = new StringBuilder("-javaagent:").append(agent.jarPath);
      if (agent.options != null) {
        sb.append('=').append(agent.options);
      }

      command.add(sb.toString());
    }

    for (final String systemProperty : systemProperties) {
      command.add("-D" + systemProperty);
    }

    if (mainClass != null) {
      command.add(mainClass);
    }

    command.addAll(args);

    return command;
  }
}
