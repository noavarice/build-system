package com.github.build;

import com.github.build.compile.CompileArgs;
import com.github.build.compile.CompileService;
import com.github.build.deps.Dependency;
import com.github.build.deps.DependencyConstraints;
import com.github.build.deps.DependencyService;
import com.github.build.deps.GroupArtifactVersion;
import com.github.build.util.PathUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author noavarice
 */
public final class BuildService {

  private static final Logger log = LoggerFactory.getLogger(BuildService.class);

  private final CompileService compileService;

  private final DependencyService dependencyService;

  public BuildService(
      final CompileService compileService,
      final DependencyService dependencyService
  ) {
    this.compileService = Objects.requireNonNull(compileService);
    this.dependencyService = Objects.requireNonNull(dependencyService);
  }

  public boolean compileMain(final Path workdir, final Project project) {
    Objects.requireNonNull(workdir);
    Objects.requireNonNull(project);
    PathUtils.checkAbsolute(workdir);

    final Set<Path> sources = collectSources(workdir, project, SourceSet.Id.MAIN);
    final Path classesDir = workdir
        .resolve(project.path())
        .resolve(project.artifactLayout().rootDir())
        .resolve(project.artifactLayout().classesDir())
        .resolve(SourceSet.Id.MAIN.toString());

    try {
      Files.createDirectories(classesDir);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    final SourceSet mainSourceSet = project.sourceSet(SourceSet.Id.MAIN);
    final Set<Path> classpath = new HashSet<>();
    for (final Dependency dependency : mainSourceSet.compileClasspath()) {
      switch (dependency) {
        case Dependency.Jar file -> classpath.add(file.path());
        case Dependency.Remote.WithVersion withVersion -> {
          final GroupArtifactVersion gav = withVersion.gav();
          final Set<GroupArtifactVersion> artifacts = dependencyService.resolveTransitive(gav);
          final Map<GroupArtifactVersion, Path> localArtifacts = dependencyService.fetchToLocal(
              artifacts
          );
          classpath.addAll(localArtifacts.values());
        }
        case Dependency.Remote.WithoutVersion withoutVersion -> {
          final DependencyConstraints constraints = mainSourceSet.dependencyConstraints();
          @Nullable
          final String version = constraints.getConstraint(withoutVersion.ga());
          if (version == null) {
            log.error("Dependency has no version and no associated source set-wise constraint");
            return false;
          }

          final GroupArtifactVersion gav = withoutVersion.ga().withVersion(version);
          final Set<GroupArtifactVersion> artifacts = dependencyService.resolveTransitive(gav);
          final Map<GroupArtifactVersion, Path> localArtifacts = dependencyService.fetchToLocal(
              artifacts
          );
          classpath.addAll(localArtifacts.values());
        }
      }
    }

    final var compileArgs = new CompileArgs(sources, classesDir, classpath);
    return compileService.compile(compileArgs);
  }

  /**
   * Collects files ending with ".java" in the specified source set.
   */
  private static Set<Path> collectSources(
      final Path workdir,
      final Project project,
      final SourceSet.Id sourceSetId
  ) {
    final var sources = new HashSet<Path>();
    // for some reason, newDirectoryStream with glob does not work as expected
    // TODO: consider using newDirectoryStream with glob
    final SourceSet sourceSet = project.sourceSet(sourceSetId);
    for (final Path relativeDir : sourceSet.sourceDirectories()) {
      final Path sourceDir = workdir
          .resolve(project.path())
          .resolve(relativeDir);
      if (Files.notExists(sourceDir)) {
        log.debug("[project={}][sourceSet={}] Source directory {} does not exist",
            project.id(),
            sourceSetId,
            sourceDir
        );
        continue;
      }

      if (!Files.isDirectory(sourceDir)) {
        log.warn(
            "[project={}][sourceSet={}] {} assumed to be source directory but it's not a directory",
            project.id(),
            sourceSetId,
            sourceDir
        );
        continue;
      }

      final var sourcesPart = new ArrayList<Path>();
      final var fileVisitor = new CollectingFileVisitor(
          file -> file.getFileName().toString().endsWith(".java"),
          sourcesPart::add
      );

      try {
        Files.walkFileTree(sourceDir, fileVisitor);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }

      if (sourcesPart.isEmpty()) {
        log.debug("[project={}][sourceSet={}] No source files found in directory {}",
            project.id(),
            sourceSetId,
            sourceDir
        );
      } else {
        sources.addAll(sourcesPart);
      }
    }

    return sources;
  }

  /**
   * {@link FileVisitor} for collecting source paths into list.
   *
   * @param filter   File filter
   * @param consumer File consumer
   * @see Files#walkFileTree(Path, FileVisitor)
   */
  private record CollectingFileVisitor(
      Predicate<Path> filter,
      Consumer<Path> consumer
  ) implements FileVisitor<Path> {

    @Override
    public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
      if (filter.test(file)) {
        consumer.accept(file);
      }

      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(final Path file, final IOException exc) {
      return FileVisitResult.TERMINATE;
    }

    @Override
    public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) {
      return FileVisitResult.CONTINUE;
    }
  }
}
