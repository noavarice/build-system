package com.github.build.proxy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

/**
 * Puts repository proxy request context ({@code repo}, {@code path}, {@code req}) into the SLF4J
 * {@link MDC} for the duration of a scope and restores the previous values on close.
 *
 * <p>The values are rendered by the logging pattern, so log messages never hardcode the repository
 * id, artifact path or outgoing request number. Scopes can be nested and used with
 * try-with-resources:
 *
 * <pre>{@code
 * try (final var ignored = ProxyLogContext.request(repositoryId, artifactPath)) {
 *   log.info("GET {}", uri);
 * }
 * }</pre>
 *
 * @author noavarice
 * @since 1.0.0
 */
public final class ProxyLogContext implements AutoCloseable {

    /**
     * Exposed repository ID MDC key.
     */
    public static final String REPOSITORY = "repo";

    /**
     * Repository-relative artifact path MDC key.
     */
    public static final String PATH = "path";

    /**
     * Outgoing upstream request number MDC key.
     */
    public static final String REQUEST = "req";

    private final Map<String, @Nullable String> previousValues;

    private ProxyLogContext(final Map<String, String> values) {
        this.previousValues = new LinkedHashMap<>(values.size());
        values.forEach((key, value) -> {
            previousValues.put(key, MDC.get(key));
            MDC.put(key, value);
        });
    }

    public static ProxyLogContext request(final String repositoryId, final String artifactPath) {
        return new ProxyLogContext(Map.of(REPOSITORY, repositoryId, PATH, artifactPath));
    }

    public static ProxyLogContext outgoingRequest(final long requestNumber) {
        return new ProxyLogContext(Map.of(REQUEST, Long.toString(requestNumber)));
    }

    @Override
    public void close() {
        previousValues.forEach((key, value) -> {
            if (value == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, value);
            }
        });
    }
}
