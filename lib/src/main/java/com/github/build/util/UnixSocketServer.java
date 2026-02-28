package com.github.build.util;

import com.github.build.test.EventCodec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles receiving events coming over Unix socket at the specified path.
 *
 * @author noavarice
 * @since 1.0.0
 */
public final class UnixSocketServer<T> implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(UnixSocketServer.class);

  public static <T> UnixSocketServer<T> of(
      final Path socketPath,
      final EventCodec<T> codec,
      final Consumer<T> handler
  ) {
    final Path absoluteSocketPath = Objects.requireNonNull(socketPath).normalize().toAbsolutePath();
    Objects.requireNonNull(codec);
    Objects.requireNonNull(handler);

    final ServerSocketChannel socketChannel;
    try {
      socketChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    final var socketAddress = UnixDomainSocketAddress.of(absoluteSocketPath);
    try {
      socketChannel.bind(socketAddress);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    return new UnixSocketServer<>(
        absoluteSocketPath,
        socketChannel,
        codec,
        handler
    );
  }

  private final Path socketPath;

  private final ServerSocketChannel socketChannel;

  /**
   * For deserializing events from raw byte array.
   */
  private final EventCodec<T> codec;

  /**
   * Performs actual event handling.
   */
  private final Consumer<T> handler;

  private final AtomicBoolean stopListening = new AtomicBoolean(false);

  private UnixSocketServer(
      final Path socketPath,
      final ServerSocketChannel socketChannel,
      final EventCodec<T> codec,
      final Consumer<T> handler
  ) {
    this.socketPath = Objects.requireNonNull(socketPath);
    this.socketChannel = Objects.requireNonNull(socketChannel);
    this.codec = Objects.requireNonNull(codec);
    this.handler = Objects.requireNonNull(handler);
  }

  public void listen() {
    log.debug("Start listening for events over Unix socket {}", socketPath);
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

      final T event;
      try {
        event = codec.toValue(bytes);
      } catch (final Exception e) {
        log.warn("Failed to decode event", e);
        continue;
      }

      handler.accept(event);
    }

    log.debug("Stopped listening for events over Unix socket {}", socketPath);
  }

  @Override
  public void close() {
    log.debug("Stop listening for events over Unix socket {}", socketPath);
    stopListening.set(true);

    log.debug("Closing channel for Unix socket {}", socketPath);
    try {
      socketChannel.close();
    } catch (final IOException e) {
      log.warn("Failed to close channel for Unix socket {}", socketPath, e);
    }

    log.debug("Deleting Unix socket {}", socketPath);
    try {
      Files.deleteIfExists(socketPath);
    } catch (final IOException e) {
      log.warn("Failed to delete Unix socket {}", socketPath, e);
    }
  }
}
