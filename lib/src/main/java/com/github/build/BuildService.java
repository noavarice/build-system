package com.github.build;

import static java.util.stream.Collectors.toUnmodifiableSet;

import com.github.build.compile.CompileArgs;
import com.github.build.compile.CompileService;
import com.github.build.deps.Dependency;
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

  public BuildService(final CompileService compileService) {
    this.compileService = Objects.requireNonNull(compileService);
  }

  public boolean compileMain(final Path workdir, final Project project) {
    Objects.requireNonNull(workdir);
    Objects.requireNonNull(project);
    PathUtils.checkAbsolute(workdir);

    @Nullable
    final SourceSet mainSourceSet = project.mainSourceSet();
    Objects.requireNonNull(mainSourceSet);

    final Set<Path> sources = collectSources(workdir, mainSourceSet);
    final Path classesDir = workdir
        .resolve(project.path())
        .resolve(project.artifactLayout().rootDir())
        .resolve(project.artifactLayout().classesDir())
        .resolve(mainSourceSet.id().toString());

    try {
      Files.createDirectories(classesDir);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    final Set<Path> classpath = mainSourceSet.dependencies()
        .stream()
        .map(dependency -> switch (dependency) {
          case Dependency.File file -> file.path();
          // TODO: implement
          case Dependency.Remote ignored -> throw new UnsupportedOperationException();
        })
        .collect(toUnmodifiableSet());
    final var compileArgs = new CompileArgs(sources, classesDir, classpath);
    return compileService.compile(compileArgs);
  }

  /**
   * Collects files ending with ".java" in the specified directories.
   *
   * @param workdir   Working directory
   * @param sourceSet Source set
   * @return Paths to each individual source file, never null but can be empty
   */
  private static Set<Path> collectSources(final Path workdir, final SourceSet sourceSet) {
    final var sources = new HashSet<Path>();
    // for some reason, newDirectoryStream with glob does not work as expected
    // TODO: consider using newDirectoryStream with glob
    for (final Path relativeDir : sourceSet.sourceDirectories()) {
      final Path sourceDir = workdir
          .resolve(sourceSet.project().path())
          .resolve(sourceSet.path())
          .resolve(relativeDir);
      if (Files.notExists(sourceDir)) {
        log.debug("[project={}][sourceSet={}] Source directory {} does not exist",
            sourceSet.project().id(),
            sourceSet.id(),
            sourceDir
        );
        continue;
      }

      if (!Files.isDirectory(sourceDir)) {
        log.warn(
            "[project={}][sourceSet={}] {} assumed to be source directory but it's not a directory",
            sourceSet.project().id(),
            sourceSet.id(),
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
            sourceSet.project().id(),
            sourceSet.id(),
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
