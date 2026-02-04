package com.github.build;

import java.util.Objects;

/**
 * @author noavarice
 */
public sealed interface BuildEvent {

  record BuildFailed() implements BuildEvent {

  }

  record ProjectJarCreated(Project project) implements BuildEvent {

    public ProjectJarCreated {
      Objects.requireNonNull(project);
    }
  }
}
