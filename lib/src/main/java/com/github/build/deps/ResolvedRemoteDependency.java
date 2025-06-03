package com.github.build.deps;

import com.github.build.util.PathUtils;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A {@link Dependency.Remote} that has been resolved and downloaded to a local file system.
 *
 * @author noavarice
 * @since 1.0.0
 */
public record ResolvedRemoteDependency(Path jarPath) {

  public ResolvedRemoteDependency {
    Objects.requireNonNull(jarPath);
    PathUtils.checkAbsolute(jarPath);
    jarPath = jarPath.normalize();
  }
}
