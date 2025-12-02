package com.github.build.deps;

import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.toUnmodifiableMap;
import static java.util.stream.Collectors.toUnmodifiableSet;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.pom._4_0.DependencyManagement;
import org.apache.maven.pom._4_0.Model;
import org.apache.maven.pom._4_0.Parent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLFilter;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;
import tools.jackson.databind.ObjectMapper;

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

  private final ObjectMapper objectMapper;

  public RemoteRepository(
      final URI baseUri,
      final HttpClient client,
      final ObjectMapper objectMapper
  ) {
    this.baseUri = Objects.requireNonNull(baseUri);
    this.client = Objects.requireNonNull(client);
    this.objectMapper = objectMapper;
  }

  @Override
  public String toString() {
    return baseUri.toString();
  }

  /**
   * Downloads dependency JAR.
   *
   * @param dependency Dependency coordinates
   * @return Resolution result, never null, empty if no dependency found
   */
  public Optional<ArtifactDownloadResult> download(final GroupArtifactVersion dependency) {
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

    final var result = new ArtifactDownloadResult(response.body());
    return Optional.of(result);
  }

  public Optional<Pom> getPom(final GroupArtifactVersion gav) {
    final URI uri = buildUri(gav, ".pom");
    log.debug("Downloading {} POM from {}", gav, uri);
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
      log.warn("Downloading {} POM failed, response status: {}", gav, response.statusCode());
      return Optional.empty();
    }

    final Model model;
    try (final InputStream is = response.body()) {
      final var spf = SAXParserFactory.newInstance();
      spf.setNamespaceAware(true);

      final XMLReader xmlReader = spf
          .newSAXParser()
          .getXMLReader();

      // some POMs may lack namespace declaration
      // which is necessary for JAXB unmarshalling to succeed
      final XMLFilter filter = new NamespaceAddingFilter();
      filter.setParent(xmlReader);

      final var inputSource = new InputSource(is);
      final var saxSource = new SAXSource(filter, inputSource);

      final var context = JAXBContext.newInstance(Model.class);
      final var unmarshaller = context.createUnmarshaller();
      @SuppressWarnings("unchecked")
      final var element = (JAXBElement<Model>) unmarshaller.unmarshal(saxSource);
      model = element.getValue();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } catch (final JAXBException | ParserConfigurationException | SAXException e) {
      throw new IllegalStateException(e);
    }

    final Pom result = toPom(model);
    return Optional.of(result);
  }

  /**
   * XML filter that adds Maven namespace if it's missing.
   * <p>
   * This way it's not necessary to change received XML.
   */
  public static final class NamespaceAddingFilter extends XMLFilterImpl {

    @Override
    public void startElement(
        final String uri,
        final String localName,
        final String qName,
        final Attributes attributes
    ) throws SAXException {
      final boolean uriPresent = uri != null && !uri.isBlank();
      super.startElement(
          uriPresent ? uri : "http://maven.apache.org/POM/4.0.0",
          localName,
          qName,
          attributes
      );
    }
  }

  private static Pom toPom(final Model model) {
    final Pom.Parent parent = mapParent(model.getParent());

    String groupId = model.getGroupId();
    if (groupId == null) {
      groupId = parent.groupId();
    }

    String version = model.getVersion();
    if (version == null) {
      version = parent.version();
    }

    final List<Pom.Dependency> dependencies = mapDependencies(model.getDependencies());
    final List<Pom.Dependency> dependencyManagement = model.getDependencyManagement() != null
        ? mapDependencies(model.getDependencyManagement().getDependencies())
        : List.of();
    final Map<String, String> properties = mapProperties(model.getProperties());
    return new Pom(
        groupId,
        model.getArtifactId(),
        version,
        parent,
        properties,
        dependencyManagement,
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
    return container != null ? mapDependencies(container.getDependency()) : List.of();
  }

  private static List<Pom.Dependency> mapDependencies(
      final DependencyManagement.Dependencies container
  ) {
    return container != null ? mapDependencies(container.getDependency()) : List.of();
  }

  private static List<Pom.Dependency> mapDependencies(
      final List<org.apache.maven.pom._4_0.Dependency> dependencies
  ) {
    if (dependencies == null || dependencies.isEmpty()) {
      return List.of();
    }

    return dependencies
        .stream()
        .map(d -> {
          final boolean optional = "true".equals(d.getOptional());
          final var scope = d.getScope() == null
              ? Pom.Dependency.Scope.COMPILE
              : Pom.Dependency.Scope.valueOf(d.getScope().toUpperCase(Locale.US));
          final Set<GroupArtifact> exclusions;
          if (d.getExclusions() == null) {
            exclusions = Set.of();
          } else {
            exclusions = d.getExclusions().getExclusion()
                .stream()
                .map(exclusion -> new GroupArtifact(
                    exclusion.getGroupId(),
                    exclusion.getArtifactId())
                )
                .collect(toUnmodifiableSet());
          }

          return new Pom.Dependency(
              d.getGroupId(),
              d.getArtifactId(),
              d.getVersion(),
              scope,
              exclusions,
              optional
          );
        })
        .toList();
  }

  private static Map<String, String> mapProperties(final Model.Properties container) {
    if (container == null) {
      return Map.of();
    }

    return container.getAny()
        .stream()
        .filter(Node::hasChildNodes)
        .collect(toUnmodifiableMap(
            Node::getNodeName,
            element -> element.getFirstChild().getNodeValue()
        ));
  }

  private URI buildUri(final GroupArtifactVersion gav, final String suffix) {
    // TODO: fragile - use dedicated URI builder
    final String result = baseUri
        + "/" + gav.groupId().replace('.', '/')
        + '/' + gav.artifactId()
        + '/' + gav.version()
        + '/' + gav.artifactId() + '-' + gav.version() + suffix;
    try {
      return new URI(result);
    } catch (final URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  public Optional<String> findMax(final GroupArtifact ga, final MavenVersion.Range range) {
    if (range.exactVersion()) {
      final String result = Objects.requireNonNull(range.lower()).value();
      return Optional.of(result);
    }

    if (range.invalid()) {
      log.warn("Invalid range {}:{}", ga, range);
      return Optional.empty();
    }

    final int pageSize = 50;
    final String baseUri = "https://search.maven.org/solrsearch/select"
        + "?q=g:" + ga.groupId()
        + "+AND+a:" + ga.artifactId()
        + "&rows=" + pageSize
        + "&core=gav&wt=json";
    log.debug("Listing {} versions", ga);
    final VersionSearchResponse firstResponse = queryVersions(baseUri);
    int start = pageSize;
    int searchLimit = firstResponse.response().numFound();

    final List<ComparableVersion> versions = new ArrayList<>(searchLimit);
    versions.addAll(
        firstResponse.response().docs()
            .stream()
            .map(VersionSearchResponse.Doc::version)
            .map(ComparableVersion::new)
            .toList()
    );

    while (start < searchLimit) {
      final String uri = baseUri + "&start=" + start;
      final VersionSearchResponse response = queryVersions(uri);
      versions.addAll(
          response.response().docs()
              .stream()
              .map(VersionSearchResponse.Doc::version)
              .map(ComparableVersion::new)
              .toList()
      );
      start += pageSize;
    }

    versions.sort(reverseOrder());

    for (final ComparableVersion version : versions) {
      if (range.contains(version)) {
        return Optional.of(version.getCanonical());
      }
    }

    return Optional.empty();
  }

  private VersionSearchResponse queryVersions(final String uriStr) {
    final var request = HttpRequest
        .newBuilder(URI.create(uriStr))
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
      log.error("Request {} failed with status {}", uriStr, response.statusCode());
      throw new IllegalStateException();
    }

    return objectMapper.readValue(response.body(), VersionSearchResponse.class);
  }

  private record VersionSearchResponse(Response response) {

    private VersionSearchResponse {
      Objects.requireNonNull(response);
    }

    private record Response(int numFound, List<Doc> docs) {

      private Response {
        Objects.requireNonNull(docs);
      }
    }

    private record Doc(@JsonProperty("v") String version) {

      private Doc {
        Objects.requireNonNull(version);
      }
    }
  }
}
