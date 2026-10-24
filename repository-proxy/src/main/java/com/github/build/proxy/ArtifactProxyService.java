package com.github.build.proxy;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Resolves artifacts from the local repository, proxying missing artifacts from their upstream
 * repository and storing them locally.
 *
 * @author noavarice
 * @since 1.0.0
 */
@Service
public final class ArtifactProxyService {

  private static final Logger log = LoggerFactory.getLogger(ArtifactProxyService.class);

  private static final Duration NEGATIVE_CACHE_TTL = Duration.ofMinutes(1);

  private final LocalRepositoryStore store;

  private final Map<String, URI> upstreamRepositories;

  private final RestClient restClient;

  private final Map<String, Instant> negativeCache = new ConcurrentHashMap<>();

  private final ConcurrentHashMap<String, Object> pathLocks = new ConcurrentHashMap<>();

  /**
   * Monotonic counter of requests issued to upstream repositories, exposed through the logging MDC
   * so the number of outgoing requests per run is directly visible.
   */
  private final AtomicLong outgoingRequests = new AtomicLong();

  public ArtifactProxyService(
      final RepositoryProxyProperties properties,
      final LocalRepositoryStore store
  ) {
    final var repositoryProxyProperties = Objects.requireNonNull(properties);
    this.store = Objects.requireNonNull(store);
    this.upstreamRepositories = repositoryProxyProperties.repositories();
    upstreamRepositories.forEach((id, uri) -> log.info("Remote repository '{}': {}", id, uri));

    final var requestFactory = new JdkClientHttpRequestFactory(
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(repositoryProxyProperties.connectTimeout())
            .build()
    );
    requestFactory.setReadTimeout(repositoryProxyProperties.requestTimeout());
    this.restClient = RestClient.builder()
        .requestFactory(requestFactory)
        .build();
  }

  /**
   * Returns the artifact content, fetching and storing it when it is not present locally.
   *
   * @param repositoryId Exposed repository ID
   * @param rawPath      Repository-relative artifact path (may keep the capture group's leading
   *                     slash)
   * @return Artifact resource
   * @throws ResponseStatusException If the repository is unknown, the path is invalid, the artifact
   *                                 is missing upstream or the upstream is unavailable
   */
  public Resource resolve(final String repositoryId, final String rawPath) {
    final URI upstream = upstreamRepositories.get(repositoryId);
    if (upstream == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown repository " + repositoryId);
    }

    final String artifactPath = stripLeadingSlash(rawPath);
    if (artifactPath.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing artifact path");
    }

    try (final var logContext = ProxyLogContext.request(repositoryId, artifactPath)) {
      final Path local;
      try {
        local = store.path(repositoryId, artifactPath);
      } catch (final IllegalArgumentException e) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
      }

      final String key = repositoryId + '/' + artifactPath;
      final boolean cacheable = isCacheable(artifactPath);
      if (cacheable && store.present(local)) {
        log.info("Serving from local repository");
        return new FileSystemResource(local);
      }

      if (isNegativelyCached(key)) {
        throw notFound(key);
      }

      final Object lock = pathLocks.computeIfAbsent(key, ignored -> new Object());
      synchronized (lock) {
        // another request may have resolved the artifact while this one was waiting for the lock
        if (cacheable && store.present(local)) {
          log.info("Serving from local repository");
          return new FileSystemResource(local);
        }
        if (isNegativelyCached(key)) {
          throw notFound(key);
        }

        final URI uri = appendPath(upstream, artifactPath);
        try (final var requestIgnored = ProxyLogContext.outgoingRequest(
            outgoingRequests.incrementAndGet())) {
          log.info("GET {}", uri);

          final HttpStatusCode status;
          try {
            status = fetch(uri, local);
          } catch (final RestClientException e) {
            log.warn("GET {} failed", uri, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upstream fetch failed", e);
          } catch (final UncheckedIOException e) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store artifact", e
            );
          }

          if (status.is2xxSuccessful()) {
            negativeCache.remove(key);
            log.debug("Stored at {}", local);
            return new FileSystemResource(local);
          }

          if (status.value() == 404 || status.value() == 410) {
            negativeCache.put(key, Instant.now().plus(NEGATIVE_CACHE_TTL));
            throw notFound(key);
          }

          log.warn("GET {} -> {}", uri, status.value());
          throw new ResponseStatusException(
              HttpStatus.BAD_GATEWAY, "Upstream returned " + status.value()
          );
        }
      }
    }
  }

  private HttpStatusCode fetch(final URI uri, final Path target) {
    return restClient.get().uri(uri).exchange((request, response) -> {
      final HttpStatusCode status = response.getStatusCode();
      if (status.is2xxSuccessful()) {
        try (final InputStream in = response.getBody()) {
          store.store(target, in);
        }
      }
      return status;
    });
  }

  private static URI appendPath(final URI upstream, final String artifactPath) {
    return UriComponentsBuilder.fromUri(upstream)
        .pathSegment(artifactPath.split("/"))
        .build()
        .toUri();
  }

  private boolean isNegativelyCached(final String key) {
    final Instant expiresAt = negativeCache.get(key);
    if (expiresAt == null) {
      return false;
    }
    if (expiresAt.isAfter(Instant.now())) {
      return true;
    }

    negativeCache.remove(key);
    return false;
  }

  private static ResponseStatusException notFound(final String key) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found: " + key);
  }

  private static boolean isCacheable(final String artifactPath) {
    return !artifactPath.endsWith("maven-metadata.xml") && !artifactPath.contains("SNAPSHOT");
  }

  private static String stripLeadingSlash(final String path) {
    return path.startsWith("/") ? path.substring(1) : path;
  }
}
