package com.github.build;

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Starts the repository proxy in-process on an ephemeral port for integration tests.
 *
 * <p>The proxy classes are loaded reflectively: they are present on the Maven test classpath, but
 * not on the self-hosted build's test classpath, where tests fall back to the externally configured
 * proxy ({@code NEXUS_HOST}).
 *
 * @author noavarice
 */
public final class RepositoryProxySupport {

    private static final String LAUNCHER_CLASS = "com.github.build.proxy.RepositoryProxy";

    private static final String MAVEN_CENTRAL_ID = "maven-central";

    private static final URI MAVEN_CENTRAL = URI.create("https://repo1.maven.org/maven2");

    private static URI mavenCentralUri;

    private RepositoryProxySupport() {}

    /**
     * Returns the Maven Central repository URI, starting the embedded proxy on first use.
     *
     * @return Maven Central repository URI, never null
     */
    public static synchronized URI mavenCentralUri() {
        if (mavenCentralUri == null) {
            mavenCentralUri = resolveMavenCentralUri();
        }

        return mavenCentralUri;
    }

    private static URI resolveMavenCentralUri() {
        final Class<?> launcher;
        try {
            launcher = Class.forName(LAUNCHER_CLASS);
        } catch (final ClassNotFoundException e) {
            return externalMavenCentralUri();
        }

        try {
            final Object proxy = launcher.getMethod("start", Path.class, Map.class)
                    .invoke(null, mavenLocalRepository(), Map.of(MAVEN_CENTRAL_ID, MAVEN_CENTRAL));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> close(proxy)));

            return (URI) launcher.getMethod("repositoryUri", String.class).invoke(proxy, MAVEN_CENTRAL_ID);
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to start the embedded repository proxy", e);
        }
    }

    /**
     * Resolves the Maven local repository the proxy caches proxied artifacts in. Reusing the real
     * local repository keeps artifacts across runs instead of re-downloading them every time.
     *
     * @return Maven local repository path, never null
     */
    private static Path mavenLocalRepository() {
        final String configured = System.getProperty("maven.repo.local");
        if (configured != null) {
            return Path.of(configured).toAbsolutePath();
        }

        return Path.of(System.getProperty("user.home"), ".m2", "repository").toAbsolutePath();
    }

    private static URI externalMavenCentralUri() {
        final String host = Objects.requireNonNullElse(System.getenv("NEXUS_HOST"), "localhost");
        return URI.create("http://" + host + ":8081/repository/maven-central");
    }

    private static void close(final Object proxy) {
        try {
            proxy.getClass().getMethod("close").invoke(proxy);
        } catch (final ReflectiveOperationException e) {
            // ignored on shutdown
        }
    }
}
