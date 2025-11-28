package com.github.build.deps;

import com.github.build.deps.graph.Graph;
import com.github.build.deps.graph.GraphPath;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author noavarice
 * @since 1.0.0
 */
public final class DependencyService {

  private static final Logger log = LoggerFactory.getLogger(DependencyService.class);

  private final List<RemoteRepository> remoteRepositories;

  private final LocalRepository localRepository;

  private final Map<GroupArtifactVersion, Pom> poms = new ConcurrentHashMap<>();

  public DependencyService(
      final List<RemoteRepository> remoteRepositories,
      final LocalRepository localRepository
  ) {
    if (remoteRepositories.isEmpty()) {
      throw new IllegalArgumentException();
    }
    this.remoteRepositories = List.copyOf(remoteRepositories);
    this.localRepository = Objects.requireNonNull(localRepository);
  }

  public Set<GroupArtifactVersion> resolveTransitive(final GroupArtifactVersion artifact) {
    final var queue = new ArrayList<GraphPath>();
    queue.addLast(new GraphPath(artifact));

    final var graph = new Graph();
    graph.add(artifact, Set.of(), GraphPath.ROOT);

    while (!queue.isEmpty()) {
      final GraphPath currentPath = queue.removeLast();
      final GroupArtifactVersion current = currentPath.getLast();

      log.info("Resolving direct dependencies for {}", current);

      // resolve POM and all of its parents, so it's possible to resolve versions
      // for transitive dependencies (e.g., when dependency version is set implicitly
      // or explicitly but via POM property)
      final Pom pom = poms.computeIfAbsent(current, this::findPom);
      final List<Pom> parents = resolveParents(pom);
      log.debug("Resolved {} parents: {}", current, parents.stream().map(Pom::gav).toList());

      final var properties = new HashMap<String, String>();
      final var dependencyManagement = new HashMap<GroupArtifact, String>();
      final var dependencies = new HashMap<GroupArtifact, String>();

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
          if (d.scope() == Pom.Dependency.Scope.IMPORT) {
            final String version = resolveExactVersion(d, properties, dependencyManagement);
            final GroupArtifactVersion gav = d.groupArtifact()
                .withVersion(version);
            importDependencyManagement(gav, dependencyManagement);
            continue;
          }

          final String version = Objects.requireNonNull(d.version());
          final String exactVersion = resolveExactVersion(version, properties);
          dependencyManagement.put(d.groupArtifact(), exactVersion);
        }

        // resolving and accumulating explicit dependencies
        for (final Pom.Dependency d : parent.dependencies()) {
          if (d.optional()) {
            log.debug("Skipping optional dependency {}", d.groupArtifact());
            continue;
          }

          switch (d.scope()) {
            case COMPILE, RUNTIME -> {
              final String exactVersion = resolveExactVersion(d, properties, dependencyManagement);
              dependencies.put(d.groupArtifact(), exactVersion);
              graph.add(
                  d.groupArtifact().withVersion(exactVersion),
                  d.exclusions(),
                  currentPath
              );
            }
            default -> log.debug("Skipping non-compile, non-runtime dependency {} (scope {})",
                d.groupArtifact(),
                d.scope()
            );
          }
        }
      }

      final var moreToResolve = new ArrayList<GroupArtifactVersion>(dependencies.size());
      dependencies.forEach((artifactCoordinates, version) -> {
        final GroupArtifactVersion gav = artifactCoordinates.withVersion(version);
        moreToResolve.add(gav);
      });

      log.debug("Found {} dependencies for resolution: {}", moreToResolve.size(), moreToResolve);
      moreToResolve
          .stream()
          .map(currentPath::addLast)
          .forEach(queue::addLast);
    }

    final Graph resolved = graph.resolve();
    return resolved.toDependencies();
  }

  // TODO: add tests
  private void importDependencyManagement(
      final GroupArtifactVersion importing,
      final Map<GroupArtifact, String> importTo
  ) {
    final Pom pom = poms.computeIfAbsent(importing, this::findPom);
    final List<Pom> parents = resolveParents(pom);
    final var parentsAndCurrent = new ArrayList<>(parents);
    parentsAndCurrent.add(pom);

    final var properties = new HashMap<String, String>();
    final var dependencyManagement = new HashMap<GroupArtifact, String>();
    for (final Pom parent : parentsAndCurrent) {
      // accumulating parent properties
      properties.put("project.version", parent.version());
      if (parent.parent() != null) {
        properties.put("project.parent.version", parent.parent().version());
      }
      properties.putAll(parent.properties());

      // resolving dependency management versions and accumulating them
      for (final Pom.Dependency d : parent.dependencyManagement()) {
        if (d.scope() == Pom.Dependency.Scope.IMPORT) {
          final String version = resolveExactVersion(d, properties, dependencyManagement);
          final GroupArtifactVersion gav = d.groupArtifact()
              .withVersion(version);
          importDependencyManagement(gav, dependencyManagement);
          continue;
        }

        final String version = Objects.requireNonNull(d.version());
        final String exactVersion = resolveExactVersion(version, properties);
        dependencyManagement.put(d.groupArtifact(), exactVersion);
      }
    }

    if (log.isDebugEnabled()) {
      final var imported = new ArrayList<GroupArtifactVersion>(dependencyManagement.size());
      dependencyManagement.forEach((groupArtifact, version) -> {
        final GroupArtifactVersion gav = groupArtifact.withVersion(version);
        imported.add(gav);
      });

      log.debug("Importing from {}: {}", importing, imported);
    }

    importTo.putAll(dependencyManagement);
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
      final Map<GroupArtifact, String> dependencyManagement
  ) {
    final GroupArtifact groupArtifact = dependency.groupArtifact();
    final String version = dependency.version();

    // resolving version from dependency management
    if (version == null) {
      final String exactVersion = dependencyManagement.get(groupArtifact);
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

  /**
   * Fetches dependencies from remote repositories and saves them to local repository.
   *
   * @param artifacts Artifacts to fetch
   * @return Mapping from artifact to its path in the file system
   */
  @SuppressWarnings("resource")
  public Map<GroupArtifactVersion, Path> fetchToLocal(final Set<GroupArtifactVersion> artifacts) {
    Objects.requireNonNull(artifacts);
    if (artifacts.isEmpty()) {
      return Map.of();
    }

    final var result = new HashMap<GroupArtifactVersion, Path>();
    for (final GroupArtifactVersion gav : artifacts) {
      if (localRepository.jarPresent(gav)) {
        final Path path = localRepository.getPath(gav);
        log.debug("{} already fetched to {}", gav, path);
        result.put(gav, path);
        continue;
      }

      log.debug("{} is missing locally, fetching", gav);
      @Nullable InputStream jarInputStream = null;
      for (final RemoteRepository remoteRepository : remoteRepositories) {
        final Optional<ArtifactDownloadResult> artifactResolutionResult = remoteRepository.download(
            gav
        );
        if (artifactResolutionResult.isEmpty()) {
          log.debug("{} JAR in {}: not found", gav, remoteRepository);
        } else {
          log.debug("{} JAR in {}: found", gav, remoteRepository);
          jarInputStream = artifactResolutionResult.get().stream();
          break;
        }
      }

      if (jarInputStream == null) {
        // TODO: introduce specific exception
        throw new IllegalStateException(gav + " JAR not found in any remote repository");
      }

      final byte[] jarBytes;
      try {
        jarBytes = jarInputStream.readAllBytes();
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }

      final Path jarPath = localRepository.saveJar(gav, jarBytes);
      log.debug("{} fetched and saved to {}", gav, jarPath);
      result.put(gav, jarPath);
    }

    return Map.copyOf(result);
  }

  public DependencyConstraints getConstraints(
      final GroupArtifactVersion bom,
      final GroupArtifactVersion... other
  ) {
    final List<GroupArtifactVersion> boms = new ArrayList<>();
    Objects.requireNonNull(bom);
    boms.add(bom);
    if (other != null) {
      for (final GroupArtifactVersion gav : other) {
        Objects.requireNonNull(gav);
        boms.add(gav);
      }
    }

    log.debug("Getting dependency constraints from {}", boms);
    final var builder = DependencyConstraints.builder();
    for (final GroupArtifactVersion gav : boms) {
      final Pom current = findPom(gav);
      final List<Pom> parents = resolveParents(current);
      if (log.isDebugEnabled()) {
        final List<GroupArtifactVersion> parentGavs = parents
            .stream()
            .map(Pom::gav)
            .toList();
        log.debug("Getting constraints from {} and its parents {}", gav, parentGavs);
      }

      final List<Pom> all = new ArrayList<>(parents);
      all.add(current);

      for (final Pom pom : all) {
        for (final Pom.Dependency dependency : pom.dependencyManagement()) {
          builder.withExactVersion(dependency.groupArtifact(), dependency.version());
        }
      }
    }
    return builder.build();
  }

  private List<Pom> resolveParents(final Pom pom) {
    log.debug("Resolving parents for {}", pom.gav());
    final var result = new ArrayList<Pom>();
    Pom.Parent parent = pom.parent();
    while (parent != null) {
      final Pom parentPom = poms.computeIfAbsent(parent.gav(), this::findPom);
      parent = parentPom.parent();
      result.addFirst(parentPom);
    }

    return result;
  }

  private Pom findPom(final GroupArtifactVersion gav) {
    for (final RemoteRepository repository : remoteRepositories) {
      final Optional<Pom> pomOpt = repository.getPom(gav);
      if (pomOpt.isPresent()) {
        return pomOpt.get();
      }
    }

    log.error("Failed to find POM for {} in the following repositories: {}",
        gav,
        remoteRepositories
    );
    throw new IllegalStateException();
  }
}
