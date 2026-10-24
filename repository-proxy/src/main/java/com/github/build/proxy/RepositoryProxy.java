package com.github.build.proxy;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Embedded repository proxy. Starts the application on an ephemeral port so the proxy can be
 * started programmatically (for example from tests) without external infrastructure.
 *
 * @author noavarice
 * @since 1.0.0
 */
public final class RepositoryProxy implements AutoCloseable {

    public static final String MAVEN_CENTRAL_ID = "maven-central";

    public static final URI MAVEN_CENTRAL = URI.create("https://repo1.maven.org/maven2");

    private static final Logger log = LoggerFactory.getLogger(RepositoryProxy.class);

    private final ConfigurableApplicationContext application;

    private final int port;

    private RepositoryProxy(final ConfigurableApplicationContext application, final int port) {
        this.application = application;
        this.port = port;
    }

    /**
     * Starts the proxy on an ephemeral port.
     *
     * @param localRepository Maven local repository to store proxied artifacts in
     * @param repositories    Mapping from exposed repository ID to upstream repository base URI
     * @return Started proxy, never null
     */
    public static RepositoryProxy start(final Path localRepository, final Map<String, URI> repositories) {
        Objects.requireNonNull(localRepository, "localRepository");
        Objects.requireNonNull(repositories, "repositories");
        if (repositories.isEmpty()) {
            throw new IllegalArgumentException("At least one upstream repository is required");
        }

        final var arguments = new ArrayList<String>();
        arguments.add("--spring.main.banner-mode=off");
        arguments.add("--server.port=0");
        arguments.add("--repository-proxy.local-repository=" + localRepository.toAbsolutePath());
        repositories.forEach((id, uri) -> arguments.add("--repository-proxy.repositories." + id + '=' + uri));

        final ConfigurableApplicationContext application =
                SpringApplication.run(RepositoryProxyApplication.class, arguments.toArray(String[]::new));
        final Integer port = application.getEnvironment().getProperty("local.server.port", Integer.class);
        if (port == null) {
            application.close();
            throw new IllegalStateException("Repository proxy did not expose its port");
        }

        log.info("Repository proxy started on port {}", port);
        return new RepositoryProxy(application, port);
    }

    public int port() {
        return port;
    }

    public URI baseUri() {
        return URI.create("http://localhost:" + port);
    }

    public URI repositoryUri(final String repositoryId) {
        return URI.create(baseUri() + "/repository/" + repositoryId);
    }

    @Override
    public void close() {
        application.close();
    }
}
