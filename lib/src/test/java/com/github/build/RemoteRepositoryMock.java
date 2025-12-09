package com.github.build;

import com.github.build.deps.ArtifactDownloadResult;
import com.github.build.deps.GroupArtifact;
import com.github.build.deps.GroupArtifactVersion;
import com.github.build.deps.MavenVersion;
import com.github.build.deps.Pom;
import com.github.build.deps.RemoteRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author noavarice
 */
public final class RemoteRepositoryMock implements RemoteRepository {

  private static final Logger log = LoggerFactory.getLogger(RemoteRepositoryMock.class);

  private final RemoteRepository delegate;

  private final Map<GroupArtifactVersion, Pom> mockedPoms = new ConcurrentHashMap<>();

  public RemoteRepositoryMock(final RemoteRepository delegate) {
    this.delegate = delegate;
  }

  public void mockPom(final GroupArtifactVersion gav, final Pom pom) {
    if (mockedPoms.containsKey(gav)) {
      log.warn("{} already mocked", gav);
    }

    mockedPoms.put(gav, pom);
  }

  @Override
  public Optional<ArtifactDownloadResult> download(final GroupArtifactVersion dependency) {
    return delegate.download(dependency);
  }

  @Override
  public Optional<Pom> getPom(final GroupArtifactVersion gav) {
    return mockedPoms.containsKey(gav)
        ? Optional.ofNullable(mockedPoms.get(gav))
        : delegate.getPom(gav);
  }

  @Override
  public Optional<String> findMax(final GroupArtifact ga, final MavenVersion.Range range) {
    return delegate.findMax(ga, range);
  }
}
