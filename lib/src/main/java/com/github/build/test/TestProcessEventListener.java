package com.github.build.test;

import com.github.build.Project;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens for test process events over Unix socket.
 *
 * @author noavarice
 * @since 1.0.0
 */
public final class TestProcessEventListener implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(TestProcessEventListener.class);

  // TODO: for log correlation only, replace with MDC
  private final Project.Id projectId;

  private final ServerSocketChannel socketChannel;

  private final AtomicBoolean stopListening = new AtomicBoolean(false);

  private final CountDownLatch waitLatch = new CountDownLatch(1);

  public TestProcessEventListener(
      final Project.Id projectId,
      final ServerSocketChannel socketChannel
  ) {
    this.projectId = projectId;
    this.socketChannel = socketChannel;
  }

  @Override
  public void run() {
    log.debug("[project={}] Start listening for test process events", projectId);
    while (!stopListening.get()) {
      final byte[] bytes;
      try (final SocketChannel clientChannel = socketChannel.accept()) {
        // TODO: value is random, consider some specific value
        final var buffer = ByteBuffer.allocate(4096);
        int bytesRead = clientChannel.read(buffer);
        if (bytesRead < 0) {
          continue;
        }

        bytes = new byte[bytesRead];
        buffer.flip();
        buffer.get(bytes);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }

      final var message = new String(bytes, StandardCharsets.UTF_8);
      // FIXME: handle actual events, not just plaintext strings
      log.debug("[project={}] Test process message: {}", projectId, message);
    }
    waitLatch.countDown();
  }

  /**
   * Marks listener as stopped and waits up to the specified duration until it actually stops.
   *
   * @param waitingDuration How long to wait until listener stops
   * @return True if listener stopped, false if waiting time elapsed
   */
  public boolean stop(final Duration waitingDuration) {
    stopListening.set(true);
    try {
      return waitLatch.await(waitingDuration.toMillis(), TimeUnit.MILLISECONDS);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
