package com.github.build.junit;

import com.github.build.test.junit.JUnitEvent;
import com.github.build.util.UnixSocketClient;
import java.util.Objects;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

/**
 * {@link TestExecutionListener} that forwards test execution events to the specified Unix socket.
 *
 * @author noavarice
 * @since 1.0.0
 */
public final class UnixSocketForwardingTestExecutionListener implements TestExecutionListener {

  private final UnixSocketClient<JUnitEvent> client;

  public UnixSocketForwardingTestExecutionListener(final UnixSocketClient<JUnitEvent> client) {
    this.client = Objects.requireNonNull(client);
  }

  @Override
  public void executionSkipped(final TestIdentifier testIdentifier, final String reason) {
    final String id = testIdentifier.getUniqueId();
    if (testIdentifier.isTest()) {
      client.send(new JUnitEvent.TestSkipped(id));
    } else {
      client.send(new JUnitEvent.ContainerSkipped(id));
    }
  }

  @Override
  public void executionFinished(
      final TestIdentifier testIdentifier,
      final TestExecutionResult testExecutionResult
  ) {
    final String id = testIdentifier.getUniqueId();
    final JUnitEvent.Status status = switch (testExecutionResult.getStatus()) {
      case SUCCESSFUL -> JUnitEvent.Status.SUCCESSFUL;
      case ABORTED -> JUnitEvent.Status.ABORTED;
      case FAILED -> JUnitEvent.Status.FAILED;
    };
    if (testIdentifier.isTest()) {
      client.send(new JUnitEvent.TestFinished(id, status));
    } else {
      client.send(new JUnitEvent.ContainerFinished(id));
    }
  }
}
