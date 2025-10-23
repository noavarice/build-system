package com.github.build.deps;

import com.github.build.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for working with dependencies.
 *
 * @author noavarice
 * @since 1.0.0
 */
public final class Dependencies {

  private static final Logger log = LoggerFactory.getLogger(Dependencies.class);

  private Dependencies() {
  }

  public static final RemoteRepository MAVEN_CENTRAL = new RemoteRepository
      .Builder("nexus-local", "default", "http://localhost:8081/repository/maven-central")
      .build();

  public static final List<RemoteRepository> DEFAULT_REPOS = List.of(
      MAVEN_CENTRAL
  );

  /**
   * Resolves project remote dependencies, downloads them and puts into cache.
   *
   * @param project         Project which dependencies to be resolved
   * @param repositories    Remote repositories for resolving dependencies against
   * @param localRepository Local repository to save downloaded artifacts to
   * @param cache           Dependency cache
   */
  public static void resolve(
      final Project project,
      final List<RemoteRepository> repositories,
      final LocalRepository localRepository,
      final Map<Dependency.Remote.Lax, ResolvedRemoteDependency> cache
  ) {
    resolve(project, repositories, localRepository, cache, null);
  }


  /**
   * Resolves project remote dependencies, downloads them and puts into cache.
   *
   * @param project         Project which dependencies to be resolved
   * @param repositories    Remote repositories for resolving dependencies against
   * @param localRepository Local repository to save downloaded artifacts to
   * @param cache           Dependency cache
   */
  public static void resolve(
      final Project project,
      final List<RemoteRepository> repositories,
      final LocalRepository localRepository,
      final Map<Dependency.Remote.Lax, ResolvedRemoteDependency> cache,
      @Nullable final DependencyFilter filter
  ) {
    Objects.requireNonNull(project);
    if (repositories != DEFAULT_REPOS) {
      Objects.requireNonNull(repositories).forEach(Objects::requireNonNull);
    }
    Objects.requireNonNull(localRepository);
    Objects.requireNonNull(cache);

    log.info("[project={}] Resolving dependencies", project.id());

    final RepositorySystem repoSystem = new RepositorySystemSupplier().get();
    final RepositorySystemSession repoSession = repoSession(repoSystem, localRepository);

    final List<Dependency.Remote.Lax> remoteDependencies = project.mainSourceSet().dependencies()
        .stream()
        .filter(dependency -> dependency instanceof Dependency.Remote.Lax)
        .map(dependency -> (Dependency.Remote.Lax) dependency)
        .toList();

    for (final var dependency : remoteDependencies) {
      final var aetherDependency = Dependencies.toAetherDependency(dependency);
      final var request = new CollectRequest(aetherDependency, repositories);
      final var dependencyRequest = new DependencyRequest(request, filter);

      final DependencyResult result;
      try {
        result = repoSystem.resolveDependencies(repoSession, dependencyRequest);
      } catch (final DependencyResolutionException e) {
        throw new IllegalStateException(e);
      }

      for (final ArtifactResult artifactResult : result.getArtifactResults()) {
        log.info("[project={}] Resolved {}", project.id(), artifactResult);
        final Path path = artifactResult.getArtifact().getFile().toPath().toAbsolutePath();
        final var dep = fromAetherDependency(artifactResult.getArtifact(), dependency.scope());
        cache.put(dep, new ResolvedRemoteDependency(path));
      }
    }
  }

  private static org.eclipse.aether.graph.Dependency toAetherDependency(
      final Dependency.Remote.Lax dependency
  ) {
    final var artifact = new DefaultArtifact(
        dependency.groupId(),
        dependency.artifactId(),
        null,
        "jar",
        dependency.version()
    );
    return new org.eclipse.aether.graph.Dependency(artifact, null);
  }

  private static Dependency.Remote.Lax fromAetherDependency(
      final Artifact artifact,
      final Dependency.Scope scope
  ) {
    return new Dependency.Remote.Lax(
        artifact.getGroupId(),
        artifact.getArtifactId(),
        artifact.getVersion(),
        scope
    );
  }

  private static RepositorySystemSession repoSession(
      final RepositorySystem system,
      final LocalRepository localRepo
  ) {
    final DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
    final var manager = system.newLocalRepositoryManager(session, localRepo);
    session.setLocalRepositoryManager(manager);
    return session;
  }
}
