package com.github.build.proxy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Minimal dependency-free health probe used by container health checks, so images do not need
 * {@code curl} or {@code wget}. Exits with code {@code 0} when the given URL responds with a 2xx
 * status and {@code 1} otherwise.
 *
 * @author noavarice
 * @since 1.0.0
 */
public final class HealthCheck {

    private HealthCheck() {}

    public static void main(final String[] args) {
        final String url = args.length > 0 ? args[0] : "http://localhost:8080/health";
        int status = -1;
        try {
            final var request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            status = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.discarding())
                    .statusCode();
        } catch (final Exception e) {
            status = -1;
        }

        System.exit(status >= 200 && status < 300 ? 0 : 1);
    }
}
