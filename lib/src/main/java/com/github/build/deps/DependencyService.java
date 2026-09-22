package com.github.build.deps;

import com.github.build.Project;
import com.github.build.SourceSet;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author noavarice
 */
public interface DependencyService {

  /**
   * @deprecated Use {@link #resolveTransitive(List, DependencyConstraints)} as more general method
   */
  @Deprecated(forRemoval = true)
  Set<GroupArtifactVersion> resolveTransitive(GroupArtifactVersion artifact);

  Set<GroupArtifactVersion> resolveTransitive(
      List<GroupArtifactVersion> artifacts,
      DependencyConstraints constraints
  );

  Map<GroupArtifactVersion, Path> resolveCompileClasspath(SourceSet sourceSet);

  Set<GroupArtifactVersion> resolve(Project project);

  Map<GroupArtifactVersion, Path> fetchToLocal(Set<GroupArtifactVersion> artifacts);

  Path fetchToLocal(GroupArtifactVersion artifact, String classifier);

  DependencyConstraints getConstraints(
      GroupArtifactVersion bom,
      GroupArtifactVersion... other
  );
}
