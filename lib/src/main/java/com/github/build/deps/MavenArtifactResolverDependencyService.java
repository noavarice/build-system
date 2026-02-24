package com.github.build.deps;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedCollection;
import java.util.Set;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dependency service implementation over Maven Artifact Resolver.
 *
 * @author noavarice
 */
public final class MavenArtifactResolverDependencyService implements DependencyService {

  private static final Logger log = LoggerFactory.getLogger(
      MavenArtifactResolverDependencyService.class
  );

  public static final RemoteRepository MAVEN_CENTRAL = new RemoteRepository
      .Builder("central", "default", "https://repo.maven.apache.org/maven2")
      .build();

  private final RepositorySystem repositorySystem;

  private final RepositorySystemSession repositorySystemSession;

  private final List<RemoteRepository> repositories;

  public MavenArtifactResolverDependencyService(
      final RepositorySystem repositorySystem,
      final RepositorySystemSession repositorySystemSession,
      final List<RemoteRepository> repositories
  ) {
    this.repositorySystem = Objects.requireNonNull(repositorySystem);
    this.repositorySystemSession = Objects.requireNonNull(repositorySystemSession);
    this.repositories = List.copyOf(repositories);
  }

  @Override
  public Set<GroupArtifactVersion> resolveTransitive(final GroupArtifactVersion artifact) {
    Objects.requireNonNull(artifact);
    final var aetherArtifact = new DefaultArtifact(
        artifact.groupId(),
        artifact.artifactId(),
        null,
        "jar",
        artifact.version()
    );
    final var request = new CollectRequest(
        List.of(new org.eclipse.aether.graph.Dependency(aetherArtifact, null)),
        null,
        repositories
    );
    // Cannot set root dependency - this way Aether will incorrectly
    // resolve optional dependencies (see OptionalDependencySelector).
    // So we set root artifact (not root dependency) which works
    // pretty much as a pseudo-dependency for entering resolved dependency
    // graph. Without root artifact we cannot start iterating over results.
    // Yet we don't have even a root artifact, so we're emulating its value.
    request.setRootArtifact(new DefaultArtifact(null, null, null, null));
    final CollectResult collectResult;
    try {
      collectResult = repositorySystem.collectDependencies(repositorySystemSession, request);
    } catch (DependencyCollectionException e) {
      throw new IllegalStateException(e);
    }

    final var result = new HashSet<GroupArtifactVersion>();
    final SequencedCollection<DependencyNode> queue = new ArrayList<>();
    collectResult.getRoot().getChildren().forEach(queue::addLast);
    while (!queue.isEmpty()) {
      final DependencyNode node = queue.removeFirst();
      final var gav = new GroupArtifactVersion(
          node.getArtifact().getGroupId(),
          node.getArtifact().getArtifactId(),
          node.getArtifact().getVersion()
      );
      result.add(gav);
      node.getChildren().forEach(queue::addLast);
    }

    return result;
  }

  @Override
  public Set<GroupArtifactVersion> resolveTransitive(
      final List<GroupArtifactVersion> artifacts,
      final DependencyConstraints constraints
  ) {
    Objects.requireNonNull(artifacts);
    Objects.requireNonNull(constraints);
    if (artifacts.isEmpty()) {
      return Set.of();
    }

    final List<Dependency> dependencies = artifacts
        .stream()
        .map(artifact -> new DefaultArtifact(
            artifact.groupId(),
            artifact.artifactId(),
            null,
            "jar",
            artifact.version()
        ))
        .map(artifact -> new org.eclipse.aether.graph.Dependency(artifact, null))
        .toList();
    final List<Dependency> managedDependencies = constraints
        .stream()
        .map(artifact -> new DefaultArtifact(
            artifact.groupId(),
            artifact.artifactId(),
            null,
            "jar",
            artifact.version()
        ))
        .map(artifact -> new org.eclipse.aether.graph.Dependency(artifact, null))
        .toList();
    final var request = new CollectRequest(
        dependencies,
        managedDependencies,
        repositories
    );
    // Cannot set root dependency - this way Aether will incorrectly
    // resolve optional dependencies (see OptionalDependencySelector).
    // So we set root artifact (not root dependency) which works
    // pretty much as a pseudo-dependency for entering resolved dependency
    // graph. Without root artifact we cannot start iterating over results.
    // Yet we don't have even a root artifact, so we're emulating its value.
    request.setRootArtifact(new DefaultArtifact(null, null, null, null));
    final CollectResult collectResult;
    try {
      collectResult = repositorySystem.collectDependencies(repositorySystemSession, request);
    } catch (DependencyCollectionException e) {
      throw new IllegalStateException(e);
    }

    final var result = new HashSet<GroupArtifactVersion>();
    final SequencedCollection<DependencyNode> queue = new ArrayList<>();
    collectResult.getRoot().getChildren().forEach(queue::addLast);
    while (!queue.isEmpty()) {
      final DependencyNode node = queue.removeFirst();
      final var gav = new GroupArtifactVersion(
          node.getArtifact().getGroupId(),
          node.getArtifact().getArtifactId(),
          node.getArtifact().getVersion()
      );
      result.add(gav);
      node.getChildren().forEach(queue::addLast);
    }

    return result;
  }

  @Override
  public Map<GroupArtifactVersion, Path> fetchToLocal(final Set<GroupArtifactVersion> artifacts) {
    Objects.requireNonNull(artifacts);
    final var artifactRequests = artifacts
        .stream()
        .map(gav -> new DefaultArtifact(
            gav.groupId(),
            gav.artifactId(),
            null,
            "jar",
            gav.version()
        ))
        .map(artifact -> new ArtifactRequest(artifact, repositories, null))
        .toList();

    // Cannot set root dependency - this way Aether will incorrectly
    // resolve optional dependencies (see OptionalDependencySelector).
    // So we set root artifact (not root dependency) which works
    // pretty much as a pseudo-dependency for entering resolved dependency
    // graph. Without root artifact we cannot start iterating over results.
    // Yet we don't have even a root artifact, so we're emulating its value.
    final List<ArtifactResult> resolveResult;
    try {
      resolveResult = repositorySystem.resolveArtifacts(
          repositorySystemSession,
          artifactRequests
      );
    } catch (ArtifactResolutionException e) {
      throw new IllegalStateException(e);
    }

    final var result = new HashMap<GroupArtifactVersion, Path>();
    for (final ArtifactResult artifactResult : resolveResult) {
      final Artifact artifact = artifactResult.getArtifact();
      final var localArtifactRequest = new LocalArtifactRequest(artifact, repositories, null);
      final File file = repositorySystemSession
          .getLocalRepositoryManager()
          .find(repositorySystemSession, localArtifactRequest)
          .getFile();
      Objects.requireNonNull(file);
      final var gav = new GroupArtifactVersion(
          artifactResult.getArtifact().getGroupId(),
          artifactResult.getArtifact().getArtifactId(),
          artifactResult.getArtifact().getVersion()
      );
      result.put(gav, file.toPath().toAbsolutePath());
    }

    return result;
  }

  @Override
  public Path fetchToLocal(final GroupArtifactVersion gav, @Nullable final String classifier) {
    Objects.requireNonNull(gav);
    final var artifact = new DefaultArtifact(
        gav.groupId(),
        gav.artifactId(),
        classifier,
        "jar",
        gav.version()
    );
    final var artifactRequest = new ArtifactRequest(artifact, repositories, null);

    // Cannot set root dependency - this way Aether will incorrectly
    // resolve optional dependencies (see OptionalDependencySelector).
    // So we set root artifact (not root dependency) which works
    // pretty much as a pseudo-dependency for entering resolved dependency
    // graph. Without root artifact we cannot start iterating over results.
    // Yet we don't have even a root artifact, so we're emulating its value.
    final ArtifactResult resolveResult;
    try {
      resolveResult = repositorySystem.resolveArtifact(
          repositorySystemSession,
          artifactRequest
      );
    } catch (ArtifactResolutionException e) {
      throw new IllegalStateException(e);
    }

    final Artifact a = resolveResult.getArtifact();
    final var localArtifactRequest = new LocalArtifactRequest(a, repositories, null);
    return repositorySystemSession
        .getLocalRepositoryManager()
        .find(repositorySystemSession, localArtifactRequest)
        .getFile()
        .toPath();
  }

  @Override
  public DependencyConstraints getConstraints(
      final GroupArtifactVersion bom,
      final GroupArtifactVersion... other
  ) {
    Objects.requireNonNull(bom);
    final var boms = new ArrayList<GroupArtifactVersion>();
    boms.add(bom);
    if (other != null) {
      for (final GroupArtifactVersion gav : other) {
        Objects.requireNonNull(gav);
        boms.add(gav);
      }
    }

    final var builder = DependencyConstraints.builder();
    for (final GroupArtifactVersion gav : boms) {
      final var artifact = new DefaultArtifact(
          gav.groupId(),
          gav.artifactId(),
          null,
          "jar",
          gav.version()
      );
      final var request = new ArtifactDescriptorRequest(artifact, repositories, null);
      final ArtifactDescriptorResult result;
      try {
        result = repositorySystem.readArtifactDescriptor(repositorySystemSession, request);
      } catch (final ArtifactDescriptorException e) {
        throw new IllegalStateException(e);
      }

      for (final Dependency managedDependency : result.getManagedDependencies()) {
        final var managedGav = new GroupArtifactVersion(
            managedDependency.getArtifact().getGroupId(),
            managedDependency.getArtifact().getArtifactId(),
            managedDependency.getArtifact().getVersion()
        );
        builder.withExactVersion(managedGav);
      }
    }

    return builder.build();
  }
}
