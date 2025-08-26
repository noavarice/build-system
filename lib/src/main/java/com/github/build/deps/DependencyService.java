package com.github.build.deps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

  public Set<Coordinates> resolveTransitive(final Dependency.Remote.Exact dependency) {
    final var result = new HashSet<Coordinates>();

    final var queue = new ArrayList<Coordinates>();
    queue.addLast(dependency.coordinates());

    final var handled = new HashSet<Coordinates>();

    while (!queue.isEmpty()) {
      final Coordinates current = queue.removeFirst();
      if (handled.contains(current)) {
        continue;
      } else {
        handled.add(current);
      }

      log.info("Resolving direct dependencies for {}", current);

      // resolve POM and all of its parents, so it's possible to resolve versions
      // for transitive dependencies (e.g., when dependency version is set implicitly
      // or explicitly but via POM property)
      final Pom pom = findPom(current);
      poms.putIfAbsent(pom.coordinates(), pom);
      final List<Pom> parents = resolveParents(pom);
      log.info("Resolved {} parents: {}", current, parents.stream().map(Pom::coordinates).toList());

      final var properties = new HashMap<String, String>();
      final var dependencyManagement = new HashMap<ArtifactCoordinates, String>();
      final var dependencies = new HashMap<ArtifactCoordinates, String>();

      final var parentsAndCurrent = new ArrayList<>(parents);
      parentsAndCurrent.add(pom);

      for (final Pom parent : parentsAndCurrent) {
        // accumulating parent properties
        properties.put("project.version", parent.version());
        if (parent.parent() != null) {
          properties.put("project.parent.version", parent.parent().version());
        }
        properties.putAll(parent.properties());

        // resolving dependency management versions and accumulating them
        for (final Pom.Dependency d : parent.dependencyManagement()) {
          final String version = Objects.requireNonNull(d.version());
          final String exactVersion = resolveExactVersion(version, properties);
          dependencyManagement.put(d.artifactCoordinates(), exactVersion);
        }

        // resolving and accumulating explicit dependencies
        for (final Pom.Dependency d : parent.dependencies()) {
          if (d.optional()) {
            log.info("Skipping optional dependency {}", d.artifactCoordinates());
            continue;
          }

          switch (d.scope()) {
            case COMPILE, RUNTIME -> {
              final String exactVersion = resolveExactVersion(d, properties, dependencyManagement);
              dependencies.put(d.artifactCoordinates(), exactVersion);
            }
            default -> log.info("Skipping non-compile, non-runtime dependency {} (scope {})",
                d.artifactCoordinates(),
                d.scope()
            );
          }
        }
      }

      final var moreToResolve = new ArrayList<Coordinates>(dependencies.size());
      dependencies.forEach((artifactCoordinates, version) -> {
        final var coordinates = new Coordinates(
            artifactCoordinates.groupId(),
            artifactCoordinates.artifactId(),
            version
        );
        if (!handled.contains(coordinates)) {
          moreToResolve.add(coordinates);
        }
      });

      log.info("Found {} dependencies for resolution: {}", moreToResolve.size(), moreToResolve);
      moreToResolve.forEach(queue::addLast);
      result.addAll(moreToResolve);
    }

    return result;
  }

  private List<Pom> resolveParents(final Pom pom) {
    log.info("Resolving parents for {}", pom.coordinates());
    final var result = new ArrayList<Pom>();
    Pom.Parent parent = pom.parent();
    while (parent != null) {
      final Pom parentPom = poms.computeIfAbsent(parent.coordinates(), this::findPom);
      parent = parentPom.parent();
      result.addFirst(parentPom);
    }

    return result;
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

  /**
   * Resolves dependency version as a value placeholder or as part of dependency management.
   *
   * @param dependency           Dependency to resolve version for
   * @param properties           POM properties accumulated from parent POMs
   * @param dependencyManagement Dependency management resolved and accumulated from parent POMs
   * @return Exact (non-placeholder, non-null) dependency version
   */
  private static String resolveExactVersion(
      final Pom.Dependency dependency,
      final Map<String, String> properties,
      final Map<ArtifactCoordinates, String> dependencyManagement
  ) {
    final ArtifactCoordinates coordinates = dependency.artifactCoordinates();
    final String version = dependency.version();

    // resolving version from dependency management
    if (version == null) {
      final String exactVersion = dependencyManagement.get(coordinates);
      if (exactVersion == null) {
        // TODO: rework error model
        throw new IllegalStateException();
      }

      return exactVersion;
    }

    return resolveExactVersion(version, properties);
  }

  private static String resolveExactVersion(
      final String version,
      final Map<String, String> properties
  ) {
    // resolving version from properties
    String foundVersion = version;

    // resolving in cycle because property value can be another placeholder
    while (foundVersion != null && foundVersion.startsWith("${") && foundVersion.endsWith("}")) {
      final String propertyName = foundVersion.substring(2, foundVersion.length() - 1);
      foundVersion = properties.get(propertyName);
      if (foundVersion != null) {
        foundVersion = foundVersion.strip();
      }
    }

    if (foundVersion == null) {
      // TODO: rework error model
      throw new IllegalStateException("");
    }

    return foundVersion;
  }
}
