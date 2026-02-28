package com.github.build.junit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Objects;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

/**
 * {@link TestExecutionListener} that forwards test execution events to the specified Unix socket.
 *
 * @author noavarice
 * @since 1.0.0
 */
public final class UnixSocketForwardingTestExecutionListener implements TestExecutionListener {

  private final UnixDomainSocketAddress address;

  public UnixSocketForwardingTestExecutionListener(final Path unixSocketPath) {
    Objects.requireNonNull(unixSocketPath);
    address = UnixDomainSocketAddress.of(unixSocketPath);
  }

  @Override
  public void testPlanExecutionStarted(final TestPlan testPlan) {
    final String message = "Test plan execution started";
    send(message);
  }

  @Override
  public void executionFinished(
      final TestIdentifier testIdentifier,
      final TestExecutionResult testExecutionResult
  ) {
    final String message = MessageFormat.format(
        "Test {0} finished, result: {1}",
        testIdentifier.getUniqueId(),
        testExecutionResult.getStatus()
    );
    send(message);
  }

  private void send(final String message) {
    try (final var clientChannel = SocketChannel.open(address)) {
      final ByteBuffer buf = ByteBuffer.wrap(message.getBytes());
      clientChannel.write(buf);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
