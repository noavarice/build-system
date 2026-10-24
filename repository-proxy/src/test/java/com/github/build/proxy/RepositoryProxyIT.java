package com.github.build.proxy;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * HTTP-level tests for the repository proxy. Starts the whole application on a random port with a
 * temporary local repository and a stub upstream repository, then drives it over HTTP.
 *
 * @author noavarice
 * @since 1.0.0
 */
@DisplayName("Tests for repository proxy")
class RepositoryProxyIT {

  private static final byte[] JAR = "demo-jar-content".getBytes(StandardCharsets.UTF_8);

  private static final byte[] POM = "<project/>".getBytes(StandardCharsets.UTF_8);

  private static final String JAR_PATH = "/org/example/demo/1.0/demo-1.0.jar";

  private static final String METADATA_PATH = "/org/example/demo/maven-metadata.xml";

  private final Map<String, byte[]> upstreamFiles = new HashMap<>();

  private HttpServer upstream;

  private Path localRepository;

  private ConfigurableApplicationContext application;

  private int port;

  @BeforeEach
  void startServer() throws IOException {
    upstreamFiles.clear();
    upstreamFiles.put(JAR_PATH, JAR);
    upstreamFiles.put("/org/example/demo/1.0/demo-1.0.pom", POM);
    upstreamFiles.put(METADATA_PATH, "<metadata/>".getBytes(StandardCharsets.UTF_8));

    upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    upstream.createContext("/", exchange -> {
      final byte[] body = upstreamFiles.get(exchange.getRequestURI().getPath());
      if (body == null) {
        exchange.sendResponseHeaders(404, -1);
        exchange.close();
        return;
      }

      exchange.sendResponseHeaders(200, body.length);
      try (final OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    });
    upstream.start();

    localRepository = Files.createTempDirectory("repository-proxy-it");
    application = SpringApplication.run(
        RepositoryProxyApplication.class,
        "--server.port=0",
        "--repository-proxy.local-repository=" + localRepository,
        "--repository-proxy.repositories.stub=http://127.0.0.1:" + upstream.getAddress().getPort()
    );
    port = application.getEnvironment().getProperty("local.server.port", Integer.class);

    RestAssured.baseURI = "http://127.0.0.1";
    RestAssured.port = port;
  }

  @AfterEach
  void stopServer() {
    if (application != null) {
      application.close();
    }
    if (upstream != null) {
      upstream.stop(0);
    }
  }

  @DisplayName("Check artifact is proxied and stored in local repository")
  @Test
  void testProxiesAndStoresArtifact() {
    final byte[] body = given()
        .when()
        .get("/repository/stub" + JAR_PATH)
        .then()
        .statusCode(200)
        .contentType("application/java-archive")
        .extract()
        .asByteArray();

    assertThat(body).isEqualTo(JAR);
    assertThat(localRepository.resolve("stub" + JAR_PATH)).hasBinaryContent(JAR);
  }

  @DisplayName("Check stored artifact is served when upstream is unavailable")
  @Test
  void testServesStoredArtifactWhenUpstreamUnavailable() {
    given().get("/repository/stub" + JAR_PATH).then().statusCode(200);

    upstream.stop(0);
    upstream = null;

    final byte[] body = given()
        .when()
        .get("/repository/stub" + JAR_PATH)
        .then()
        .statusCode(200)
        .extract()
        .asByteArray();

    assertThat(body).isEqualTo(JAR);
  }

  @DisplayName("Check POM is proxied")
  @Test
  void testProxiesPom() {
    given()
        .when()
        .get("/repository/stub/org/example/demo/1.0/demo-1.0.pom")
        .then()
        .statusCode(200)
        .body(org.hamcrest.Matchers.equalTo("<project/>"));
  }

  @DisplayName("Check metadata is always refetched")
  @Test
  void testRefetchesMetadata() {
    given()
        .get("/repository/stub" + METADATA_PATH)
        .then()
        .statusCode(200)
        .body(org.hamcrest.Matchers.equalTo("<metadata/>"));

    upstreamFiles.put(METADATA_PATH, "<metadata updated=\"true\"/>".getBytes(StandardCharsets.UTF_8));

    given()
        .get("/repository/stub" + METADATA_PATH)
        .then()
        .statusCode(200)
        .body(org.hamcrest.Matchers.equalTo("<metadata updated=\"true\"/>"));
  }

  @DisplayName("Check missing artifact returns 404")
  @Test
  void testMissingArtifactReturnsNotFound() {
    given()
        .when()
        .get("/repository/stub/org/example/demo/9.9/demo-9.9.jar")
        .then()
        .statusCode(404);
  }

  @DisplayName("Check unknown repository returns 404")
  @Test
  void testUnknownRepositoryReturnsNotFound() {
    given()
        .when()
        .get("/repository/unknown" + JAR_PATH)
        .then()
        .statusCode(404);
  }

  @DisplayName("Check path traversal is rejected")
  @Test
  void testPathTraversalIsRejected() {
    final int status = given()
        .when()
        .get("/repository/stub/%2e%2e/%2e%2e/etc/passwd")
        .then()
        .extract()
        .statusCode();

    assertThat(status).isIn(400, 404);
  }

  @DisplayName("Check HEAD is supported")
  @Test
  void testHeadIsSupported() {
    given()
        .when()
        .head("/repository/stub" + JAR_PATH)
        .then()
        .statusCode(200);
  }

  @DisplayName("Check health endpoint")
  @Test
  void testHealthEndpoint() {
    given().when().get("/health").then().statusCode(200);
  }
}
