package com.github.build;

import com.github.build.deps.GroupArtifact;
import com.github.build.deps.GroupArtifactVersion;

/**
 * @author noavarice
 * @since 1.0.0
 */
public sealed interface ExtraSourceSetDependency permits Project, GroupArtifact,
    GroupArtifactVersion, MainSourceSetArgs, LocalJar {

}
