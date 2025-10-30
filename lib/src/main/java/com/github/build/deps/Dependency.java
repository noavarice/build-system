package com.github.build.deps;

import com.github.build.Project;
import com.github.build.SourceSet;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Project dependency.
 *
 * @author noavarice
 * @since 1.0.0
 */
public sealed interface Dependency {

  /**
   * Designates dependency on some other project.
   *
   * @param project Project
   */
  record OnProject(Project project) implements Dependency {

    public OnProject {
      Objects.requireNonNull(project);
    }
  }

  /**
   * Designates dependency on source set from the same project.
   *
   * @param sourceSet Source set
   */
  record OnSourceSet(SourceSet sourceSet) implements Dependency {

    public OnSourceSet {
      Objects.requireNonNull(sourceSet);
    }
  }

  /**
   * Designates dependency on local file.
   *
   * @param path File path
   */
  record Jar(Path path) implements Dependency {

    public Jar {
      Objects.requireNonNull(path);
    }
  }

  /**
   * Dependency to be resolved via {@link RemoteRepository}.
   */
  sealed interface Remote extends Dependency {

    /**
     * Designates dependency without version that is hosted somewhere in the remote repository.
     *
     * @param ga Artifact coordinates
     */
    record WithoutVersion(GroupArtifact ga) implements Remote {

      public WithoutVersion {
        Objects.requireNonNull(ga);
      }

      @Override
      public String toString() {
        return ga.toString();
      }
    }

    /**
     * Designates dependency with known version that is hosted somewhere in the remote repository.
     *
     * @param gav Artifact coordinates
     */
    record WithVersion(GroupArtifactVersion gav) implements Remote {

      public WithVersion {
        Objects.requireNonNull(gav);
      }

      @Override
      public String toString() {
        return gav.toString();
      }
    }
  }
}
