package com.github.build.proxy;

import java.util.Locale;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Maven repository proxy HTTP API.
 *
 * @author noavarice
 * @since 1.0.0
 */
@RestController
public final class ProxyController {

  private final ArtifactProxyService artifactProxyService;

  public ProxyController(final ArtifactProxyService artifactProxyService) {
    this.artifactProxyService = artifactProxyService;
  }

  @GetMapping("/health")
  public ResponseEntity<Void> health() {
    return ResponseEntity.ok().build();
  }

  @RequestMapping(
      value = "/repository/{repositoryId}/{*artifactPath}",
      method = {RequestMethod.GET, RequestMethod.HEAD}
  )
  public ResponseEntity<Resource> artifact(
      @PathVariable("repositoryId") final String repositoryId,
      @PathVariable("artifactPath") final String artifactPath
  ) {
    final Resource resource = artifactProxyService.resolve(repositoryId, artifactPath);
    return ResponseEntity.ok()
        .contentType(mediaType(artifactPath))
        .body(resource);
  }

  private static MediaType mediaType(final String artifactPath) {
    return switch (extension(artifactPath)) {
      case "jar" -> MediaType.parseMediaType("application/java-archive");
      case "pom", "xml" -> MediaType.APPLICATION_XML;
      case "module", "json" -> MediaType.APPLICATION_JSON;
      case "sha1", "sha256", "sha512", "md5", "asc", "txt" -> MediaType.TEXT_PLAIN;
      default -> MediaType.APPLICATION_OCTET_STREAM;
    };
  }

  private static String extension(final String artifactPath) {
    final int dot = artifactPath.lastIndexOf('.');
    return dot < 0 ? "" : artifactPath.substring(dot + 1).toLowerCase(Locale.US);
  }
}
