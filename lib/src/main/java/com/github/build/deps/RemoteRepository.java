package com.github.build.deps;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Works with remote artifact repositories.
 *
 * @author noavarice
 * @since 1.0.0
 */
public final class RemoteRepository {

  private static final Logger log = LoggerFactory.getLogger(RemoteRepository.class);

  private final URI baseUri;

  private final HttpClient client;

  public RemoteRepository(final URI baseUri, final HttpClient client) {
    this.baseUri = Objects.requireNonNull(baseUri);
    this.client = Objects.requireNonNull(client);
  }

  /**
   * Downloads dependency JAR.
   *
   * @param dependency Dependency coordinates
   * @return Resolution result, never null, empty if no dependency found
   */
  public Optional<ArtifactResolutionResult> download(final Dependency.RemoteExact dependency) {
    final var uri = buildUri(dependency);
    log.info("Downloading {} from {}", dependency, uri);
    final var request = HttpRequest
        .newBuilder(uri)
        .GET()
        .build();
    final HttpResponse<InputStream> response;
    try {
      response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }

    final boolean is2xx = response.statusCode() >= 200 && response.statusCode() < 300;
    if (!is2xx) {
      log.warn("Downloading {} failed, response status: {}", dependency, response.statusCode());
      return Optional.empty();
    }

    final var result = new ArtifactResolutionResult(response.body());
    return Optional.of(result);
  }

  private URI buildUri(final Dependency.RemoteExact dep) {
    // TODO: fragile - use dedicated URI builder
    final String result = baseUri
        + "/" + dep.groupId().replace('.', '/')
        + '/' + dep.artifactId()
        + '/' + dep.version()
        + '/' + dep.artifactId() + '-' + dep.version() + ".jar";
    try {
      return new URI(result);
    } catch (final URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }
}
