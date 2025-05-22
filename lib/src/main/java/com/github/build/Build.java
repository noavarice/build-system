package com.github.build;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author noavarice
 * @since 1.0.0
 */
public final class Build {

  private static final Logger log = LoggerFactory.getLogger(Build.class);

  private Build() {
  }

  /**
   * Compiles specified project>
   *
   * @param workdir Working directory
   * @param project Project to build
   */
  public static void compile(final Path workdir, final Project project) {
    Objects.requireNonNull(workdir);
    if (!workdir.isAbsolute()) {
      throw new IllegalArgumentException("Working directory must be an absolute path");
    }

    if (!Files.isDirectory(workdir)) {
      throw new IllegalArgumentException("Working directory must be an existing directory");
    }

    Objects.requireNonNull(project);

    log.info("[project={}] Compiling main source set", project.id());
    final SourceSet prodSourceSet = project.mainSourceSet();
    final Path sourcesDirectory = workdir
        .resolve(project.path())
        .resolve("src")
        .resolve(prodSourceSet.name())
        .resolve("java")
        .normalize()
        .toAbsolutePath();

    final var sources = new ArrayList<Path>();
    try {
      // for some reason, newDirectoryStream with glob does not work as expected
      // TODO: consider using newDirectoryStream with glob
      Files.walkFileTree(sourcesDirectory, new CollectingFileVisitor(sources));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    log.debug("[project={}] Compiling {} files: {}", project.id(), sources.size(), sources);

    final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    final DiagnosticListener<JavaFileObject> diagnosticListener = diagnostic -> log.info(
        "[project={}] {}", project.id(), diagnostic
    );
    final var fileManager = compiler.getStandardFileManager(
        diagnosticListener,
        Locale.US,
        StandardCharsets.UTF_8
    );
    final var compUnits = fileManager.getJavaFileObjectsFromPaths(sources);

    final Path buildDirectory = workdir
        .resolve(project.path())
        .resolve(project.artifactLayout().rootDir());
    final Path classesDirectory = buildDirectory.resolve(project.artifactLayout().classesDir());
    final var task = compiler.getTask(
        null,
        fileManager,
        diagnosticListener,
        List.of("-d", classesDirectory.resolve(prodSourceSet.name()).toString()),
        null,
        compUnits
    );
    final boolean result = task.call();
    if (result) {
      log.info("[project={}] Compilation succeeded", project.id());
    } else {
      log.error("[project={}] Compilation failed", project.id());
    }
  }

  /**
   * {@link FileVisitor} for collecting source paths into list.
   *
   * @param sources Sources list to collect into
   * @see Files#walkFileTree(Path, FileVisitor)
   */
  private record CollectingFileVisitor(List<Path> sources) implements FileVisitor<Path> {

    @Override
    public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
      System.out.println(file);
      if (file.getFileName().toString().endsWith(".java")) {
        sources.add(file);
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
