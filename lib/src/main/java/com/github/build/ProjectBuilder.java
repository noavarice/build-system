package com.github.build;

import java.nio.file.Path;

/**
 * @author noavarice
 * @since 1.0.0
 */
public interface ProjectBuilder {

  ProjectBuilder withVersion(String version);

  ProjectBuilder withPath(String path);

  ProjectBuilder withPath(Path path);

  ProjectBuilder withSourceSet(SourceSet sourceSet);

  ProjectBuilder withArtifactLayout(Project.ArtifactLayout artifactLayout);
}
