package com.github.build.deps;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author noavarice
 * @since 1.0.0
 */
public final class DependencyService {

  private static final Logger log = LoggerFactory.getLogger(DependencyService.class);

  private final List<RemoteRepository> remoteRepositories;

  private final Map<Coordinates, Pom> poms = new ConcurrentHashMap<>();

  public DependencyService(final List<RemoteRepository> remoteRepositories) {
    this.remoteRepositories = List.copyOf(remoteRepositories);
    if (remoteRepositories.isEmpty()) {
      throw new IllegalArgumentException();
    }
  }

  public List<Pom.Dependency> resolveTransitive(final Dependency.Remote.Exact dependency) {
    final var result = new ArrayList<Pom.Dependency>();

    final var queue = new ArrayList<Coordinates>();
    queue.addLast(dependency.coordinates());

    final var handled = new HashSet<Coordinates>();

    while (!queue.isEmpty()) {
      final Coordinates current = queue.getFirst();
      if (handled.contains(current)) {
        continue;
      } else {
        handled.add(current);
      }

      // resolve POM and all of its parents, so it's possible to resolve versions
      // for transitive dependencies (e.g., when dependency version is set implicitly
      // or explicitly but via POM property)
      final Pom pom = findPom(current);
      poms.putIfAbsent(pom.coordinates(), pom);
      resolveParents(pom);

      result.addAll(pom.dependencies());
      for (final Pom.Dependency transitive : pom.dependencies()) {
        // FIXME: search for dependency version
        throw new UnsupportedOperationException();
      }
    }

    return result;
  }

  private void resolveParents(final Pom pom) {
    Pom.Parent parent = pom.parent();
    while (parent != null && !poms.containsKey(parent.coordinates())) {
      final Pom parentPom = findPom(parent.coordinates());
      poms.putIfAbsent(parentPom.coordinates(), parentPom);
      parent = parentPom.parent();
    }
  }

  private Pom findPom(final Coordinates dependency) {
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
