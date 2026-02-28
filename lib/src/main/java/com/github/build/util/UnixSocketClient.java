package com.github.build.util;

import com.github.build.test.EventCodec;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles listening events coming over Unix socket at the specified path.
 *
 * @author noavarice
 * @since 1.0.0
 */
public final class UnixSocketClient<T> {

  private static final Logger log = LoggerFactory.getLogger(UnixSocketClient.class);

  public static <T> UnixSocketClient<T> of(final Path socketPath, final EventCodec<T> codec) {
    final Path absoluteSocketPath = Objects.requireNonNull(socketPath).normalize().toAbsolutePath();
    final var socketAddress = UnixDomainSocketAddress.of(absoluteSocketPath);
    return new UnixSocketClient<>(absoluteSocketPath, socketAddress, codec);
  }

  private final Path socketPath;

  private final SocketAddress socketAddress;

  /**
   * For deserializing events from raw byte array.
   */
  private final EventCodec<T> codec;

  private UnixSocketClient(
      final Path socketPath,
      final SocketAddress socketAddress,
      final EventCodec<T> codec
  ) {
    this.socketPath = Objects.requireNonNull(socketPath);
    this.socketAddress = socketAddress;
    this.codec = Objects.requireNonNull(codec);
  }

  public void send(final T event) {
    final byte[] bytes = codec.toBytes(event);
    final ByteBuffer buf = ByteBuffer.wrap(bytes);
    try (final var clientChannel = SocketChannel.open(socketAddress)) {
      clientChannel.write(buf);
    } catch (final IOException e) {
      log.warn("Failed to send event over Unix socket {}", socketPath, e);
    }
  }
}
