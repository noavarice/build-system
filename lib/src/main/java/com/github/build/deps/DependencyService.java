package com.github.build.deps;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author noavarice
 * @since 1.0.0
 */
public final class DependencyService {

  private static final Logger log = LoggerFactory.getLogger(DependencyService.class);

  private final List<RemoteRepository> remoteRepositories;

  public DependencyService(final List<RemoteRepository> remoteRepositories) {
    this.remoteRepositories = List.copyOf(remoteRepositories);
    if (remoteRepositories.isEmpty()) {
      throw new IllegalArgumentException();
    }
  }

  public List<Pom.Dependency> resolveTransitive(final Dependency.Remote.Exact dependency) {
    final var result = new ArrayList<Pom.Dependency>();

    final var queue = new ArrayList<Dependency.Remote.Exact>();
    queue.addLast(dependency);

    final var handled = new HashSet<Dependency.Remote.Exact>();

    while (!queue.isEmpty()) {
      final Dependency.Remote.Exact current = queue.getFirst();
      if (handled.contains(current)) {
        continue;
      } else {
        handled.add(current);
      }

      final Pom pom = findPom(current);
      result.addAll(pom.dependencies());
      pom.dependencies()
          .stream()
          .map(d -> new Dependency.Remote.Exact(
              d.groupId(),
              d.artifactId(),
              d.version()
          ))
          .forEach(queue::addLast);
    }

    return result;
  }

  private Pom findPom(final Dependency.Remote.Exact dependency) {
    for (final RemoteRepository repository : remoteRepositories) {
      final Optional<Pom> pomOpt = repository.getPom(dependency);
      if (pomOpt.isPresent()) {
        return pomOpt.get();
      }
    }

    log.error("Failed to find POM for {} in the following repositories: {}",
        dependency,
        remoteRepositories
    );
    throw new IllegalStateException();
  }
}
