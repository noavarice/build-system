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
public record ArtifactResolutionResult(InputStream stream) {

  public ArtifactResolutionResult {
    Objects.requireNonNull(stream);
  }
}
