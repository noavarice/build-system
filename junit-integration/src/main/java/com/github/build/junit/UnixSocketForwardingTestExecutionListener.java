package com.github.build.junit;

import com.github.build.test.junit.JUnitEvent;
import com.github.build.util.UnixSocketClient;
import java.util.List;
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
    if (testIdentifier.isTest()) {
      switch (testExecutionResult.getStatus()) {
        case SUCCESSFUL ->
            client.send(new JUnitEvent.TestFinished(id, JUnitEvent.Status.SUCCESSFUL));
        case ABORTED -> client.send(new JUnitEvent.TestFinished(id, JUnitEvent.Status.ABORTED));
        case FAILED -> {
          final var t = testExecutionResult.getThrowable().orElse(null);
          client.send(new JUnitEvent.TestFailed(
              id,
              testIdentifier.getDisplayName(),
              t != null ? t.getClass().getName() : null,
              t != null ? t.getMessage() : null,
              t != null ? List.of(t.getStackTrace()) : null
          ));
        }
      }
    } else {
      client.send(new JUnitEvent.ContainerFinished(id));
    }
  }
}
