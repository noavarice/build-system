package com.github.build.test.junit;

import java.util.Objects;

/**
 * @author noavarice
 * @since 1.0.0
 */
public sealed interface JUnitEvent {

  record ContainerStarted(String containerId) implements JUnitEvent {

    public ContainerStarted {
      Objects.requireNonNull(containerId);
    }
  }

  record ContainerFinished(String containerId) implements JUnitEvent {

    public ContainerFinished {
      Objects.requireNonNull(containerId);
    }
  }

  record ContainerSkipped(String containerId) implements JUnitEvent {

    public ContainerSkipped {
      Objects.requireNonNull(containerId);
    }
  }

  record TestSkipped(String testId) implements JUnitEvent {

    public TestSkipped {
      Objects.requireNonNull(testId);
    }
  }

  record TestFinished(String testId, Status status) implements JUnitEvent {

    public TestFinished {
      Objects.requireNonNull(testId);
      Objects.requireNonNull(status);
    }
  }

  enum Status {
    SUCCESSFUL,
    ABORTED,
    FAILED,
  }
}
