package com.github.build.deps;

import java.util.Optional;

/**
 * @author noavarice
 */
public interface RemoteRepository {

  Optional<ArtifactDownloadResult> download(GroupArtifactVersion dependency);

  Optional<Pom> getPom(GroupArtifactVersion gav);

  Optional<String> findMax(GroupArtifact ga, MavenVersion.Range range);
}
