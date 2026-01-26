package com.github.build.deps;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * @author noavarice
 */
public interface DependencyService {

  Set<GroupArtifactVersion> resolveTransitive(GroupArtifactVersion artifact);

  Map<GroupArtifactVersion, Path> fetchToLocal(Set<GroupArtifactVersion> artifacts);

  DependencyConstraints getConstraints(
      GroupArtifactVersion bom,
      GroupArtifactVersion... other
  );
}
