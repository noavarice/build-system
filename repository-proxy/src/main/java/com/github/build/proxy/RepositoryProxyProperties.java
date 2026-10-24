package com.github.build.proxy;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Repository proxy configuration.
 *
 * @param localRepository Maven local repository the proxy stores proxied artifacts in
 * @param repositories    Mapping from exposed repository ID to upstream repository base URI
 * @param connectTimeout  Timeout for opening a connection to an upstream repository
 * @param requestTimeout  Timeout for reading a response from an upstream repository
 * @author noavarice
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "repository-proxy")
public record RepositoryProxyProperties(
        Path localRepository,
        Map<String, URI> repositories,
        @DefaultValue("10s") Duration connectTimeout,
        @DefaultValue("60s") Duration requestTimeout) {

    public RepositoryProxyProperties {
        Objects.requireNonNull(localRepository, "repository-proxy.local-repository must be set");
        if (repositories == null || repositories.isEmpty()) {
            throw new IllegalArgumentException("repository-proxy.repositories must contain at least one repository");
        }
        repositories = Map.copyOf(repositories);
    }
}
