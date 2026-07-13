package com.github.build.util;

import static java.util.stream.Collectors.joining;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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
public final class JavaCommandBuilder {

  private final String javaPath;
  private final List<Path> classpath;
  private final List<Agent> agents;
  private final List<String> systemProperties;
  @Nullable private final String mainClass;
  private final List<String> args;

  private JavaCommandBuilder(
      final String javaPath,
      final List<Path> classpath,
      final List<Agent> agents,
      final List<String> systemProperties,
      @Nullable final String mainClass,
      final List<String> args
  ) {
    this.javaPath = Objects.requireNonNull(javaPath);

    this.classpath = List.copyOf(classpath);
    this.agents = List.copyOf(agents);
    this.systemProperties = List.copyOf(systemProperties);

    if (mainClass != null) {
      if (mainClass.isBlank()) {
        throw new IllegalArgumentException();
      }
      this.mainClass = mainClass.strip();
      if (!SourceVersion.isName(this.mainClass)) {
        throw new IllegalArgumentException();
      }
    } else {
      this.mainClass = null;
    }

    this.args = List.copyOf(args);
  }

  public static Builder builder() {
    return new Builder();
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
    command.add(javaPath);
    if (!classpath.isEmpty()) {
      final String cp = this.classpath
          .stream()
          .map(Path::toString)
          .collect(joining(":"));
      command.add("-classpath");
      command.add(cp);
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

  public static final class Builder {

    private String javaPath = "java";
    private final List<Path> classpath = new ArrayList<>();
    private final List<Agent> agents = new ArrayList<>();
    private final List<String> systemProperties = new ArrayList<>();
    @Nullable private String mainClass;
    private final List<String> args = new ArrayList<>();

    private Builder() {}

    public Builder javaPath(final String javaPath) {
      this.javaPath = Objects.requireNonNull(javaPath);
      return this;
    }

    public Builder classpath(final Path path, final Path... other) {
      this.classpath.add(path);
      Collections.addAll(this.classpath, other);
      return this;
    }

    public Builder classpath(final List<Path> classpath) {
      this.classpath.addAll(classpath);
      return this;
    }

    public Builder agent(final Agent agent) {
      this.agents.add(agent);
      return this;
    }

    public Builder agents(final List<Agent> agents) {
      this.agents.addAll(agents);
      return this;
    }

    public Builder systemProperty(final String systemProperty) {
      this.systemProperties.add(systemProperty);
      return this;
    }

    public Builder systemProperties(final List<String> systemProperties) {
      this.systemProperties.addAll(systemProperties);
      return this;
    }

    public Builder mainClass(@Nullable final String mainClass) {
      this.mainClass = mainClass;
      return this;
    }

    public Builder arg(final String arg) {
      this.args.add(arg);
      return this;
    }

    public Builder args(final List<String> args) {
      this.args.addAll(args);
      return this;
    }

    public JavaCommandBuilder build() {
      return new JavaCommandBuilder(
          javaPath,
          classpath,
          agents,
          systemProperties,
          mainClass,
          args
      );
    }
  }
}
