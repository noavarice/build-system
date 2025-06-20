package com.github.build.deps;

import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Project dependency.
 *
 * @author noavarice
 * @since 1.0.0
 */
public sealed interface Dependency {

  /**
   * Defines where this dependency is used (e.g., as part of compilation classpath).
   */
  enum Scope {
    /**
     * Dependency with this scope will be used as part of source set compilation classpath.
     */
    COMPILE,
  }

  /**
   * Designates dependency on local file.
   *
   * @param path  File path
   * @param scope Dependency scope (compilation classpath, runtime classpath, etc.)
   */
  record File(Path path, Scope scope) implements Dependency {

    public File {
      Objects.requireNonNull(path);
      // not checking if file exists as its may not be present for now
      Objects.requireNonNull(scope);
    }
  }

  /**
   * Dependency to be resolved via {@link RemoteRepository}.
   */
  sealed interface Remote extends Dependency {

    String groupId();

    String artifactId();

    /**
     * Remote dependency with version present.
     * <p>
     * This interface is handy when you need to operate upon some exact version of remote dependency
     * (e.g., when downloading actual dependency JAR).
     */
    sealed interface WithVersion extends Remote permits Remote.Exact, Remote.Bom {

      String version();
    }

    /**
     * Designates dependency that is hosted somewhere in the remote repository.
     *
     * @param groupId    Group ID
     * @param artifactId Artifact ID
     * @param version    Optional artifact version
     * @param scope      Dependency scope (compilation classpath, runtime classpath, etc.)
     */
    record Lax(
        String groupId,
        String artifactId,
        @Nullable String version,
        Scope scope
    ) implements Remote {

      public Lax {
        groupId = Objects.requireNonNull(groupId).strip();
        if (groupId.isBlank()) {
          throw new IllegalArgumentException();
        }

        artifactId = Objects.requireNonNull(artifactId).strip();
        if (artifactId.isBlank()) {
          throw new IllegalArgumentException();
        }

        if (version != null) {
          version = version.strip();
          if (version.isBlank()) {
            throw new IllegalArgumentException();
          }
        }

        Objects.requireNonNull(scope);
      }
    }

    /**
     * Designates dependency with known version that is hosted somewhere in the remote repository.
     *
     * @param groupId    Group ID
     * @param artifactId Artifact ID
     * @param version    Artifact version
     */
    record Exact(
        String groupId,
        String artifactId,
        String version
    ) implements Remote, WithVersion {

      public Exact {
        groupId = Objects.requireNonNull(groupId).strip();
        if (groupId.isBlank()) {
          throw new IllegalArgumentException();
        }

        artifactId = Objects.requireNonNull(artifactId).strip();
        if (artifactId.isBlank()) {
          throw new IllegalArgumentException();
        }

        version = Objects.requireNonNull(version).strip();
        if (version.isBlank()) {
          throw new IllegalArgumentException();
        }
      }

      @Override
      public String toString() {
        return groupId + ':' + artifactId + ':' + version;
      }
    }

    /**
     * Designates Bill of Materials dependency.
     *
     * @param groupId    Group ID
     * @param artifactId Artifact ID
     * @param version    Artifact version
     */
    record Bom(
        String groupId,
        String artifactId,
        String version
    ) implements Remote, WithVersion {

      public Bom {
        groupId = Objects.requireNonNull(groupId).strip();
        if (groupId.isBlank()) {
          throw new IllegalArgumentException();
        }

        artifactId = Objects.requireNonNull(artifactId).strip();
        if (artifactId.isBlank()) {
          throw new IllegalArgumentException();
        }

        version = Objects.requireNonNull(version).strip();
        if (version.isBlank()) {
          throw new IllegalArgumentException();
        }
      }

      @Override
      public String toString() {
        return groupId + ':' + artifactId + ':' + version;
      }
    }
  }
}
