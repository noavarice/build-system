package com.github.build.deps;

import static java.util.stream.Collectors.toUnmodifiableMap;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.maven.pom._4_0.Model;
import org.apache.maven.pom._4_0.Parent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

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
  public Optional<ArtifactResolutionResult> download(final Dependency.Remote.Exact dependency) {
    final var uri = buildUri(dependency, ".jar");
    log.info("Downloading {} JAR from {}", dependency, uri);
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
      log.warn("Downloading {} JAR failed, response status: {}", dependency, response.statusCode());
      return Optional.empty();
    }

    final var result = new ArtifactResolutionResult(response.body());
    return Optional.of(result);
  }

  public Optional<Pom> getPom(final Dependency.Remote.Exact dependency) {
    final URI uri = buildUri(dependency, ".pom");
    log.debug("Downloading {} POM from {}", dependency, uri);
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
      log.warn("Downloading {} POM failed, response status: {}", dependency, response.statusCode());
      return Optional.empty();
    }

    final Unmarshaller unmarshaller;
    try {
      final var context = JAXBContext.newInstance(Model.class);
      unmarshaller = context.createUnmarshaller();
    } catch (final JAXBException e) {
      throw new IllegalStateException(e);
    }

    final Model model;
    try (final InputStream is = response.body()) {
      @SuppressWarnings("unchecked")
      final var element = (JAXBElement<Model>) unmarshaller.unmarshal(is);
      model = element.getValue();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } catch (final JAXBException e) {
      throw new IllegalStateException(e);
    }

    final Pom result = toPom(model);
    return Optional.of(result);
  }

  private static Pom toPom(final Model model) {
    final Pom.Parent parent = mapParent(model.getParent());
    final List<Pom.Dependency> dependencies = mapDependencies(model.getDependencies());
    final Map<String, String> properties = mapProperties(model.getProperties());
    return new Pom(
        model.getGroupId(),
        model.getArtifactId(),
        model.getVersion(),
        parent,
        properties,
        dependencies
    );
  }

  private static Pom.Parent mapParent(final Parent parent) {
    if (parent == null) {
      return null;
    }

    return new Pom.Parent(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
  }

  private static List<Pom.Dependency> mapDependencies(final Model.Dependencies container) {
    if (container == null) {
      return List.of();
    }

    final var dependencies = container.getDependency();
    if (dependencies == null || dependencies.isEmpty()) {
      return List.of();
    }

    return dependencies
        .stream()
        .map(d -> new Pom.Dependency(d.getGroupId(), d.getArtifactId(), d.getVersion()))
        .toList();
  }

  private static Map<String, String> mapProperties(final Model.Properties container) {
    if (container == null) {
      return Map.of();
    }

    return container.getAny()
        .stream()
        .collect(toUnmodifiableMap(
            Node::getNodeName,
            element -> element.getFirstChild().getNodeValue()
        ));
  }

  private URI buildUri(final Dependency.Remote.Exact dep, final String suffix) {
    // TODO: fragile - use dedicated URI builder
    final String result = baseUri
        + "/" + dep.groupId().replace('.', '/')
        + '/' + dep.artifactId()
        + '/' + dep.version()
        + '/' + dep.artifactId() + '-' + dep.version() + suffix;
    try {
      return new URI(result);
    } catch (final URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }
}
