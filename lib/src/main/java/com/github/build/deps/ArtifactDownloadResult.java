package com.github.build.deps;

import java.io.InputStream;
import java.util.Objects;

/**
 * Result of resolving dependency artifact from remote artifact repository.
 *
 * @author noavarice
 * @see RemoteRepository
 * @since 1.0.0
 */
public record ArtifactDownloadResult(InputStream stream) {

  public ArtifactDownloadResult {
    Objects.requireNonNull(stream);
  }
}
