package com.github.build;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
   * Builds specified project.
   *
   * @param workdir Working directory
   * @param project Project to build
   */
  public static void build(final Path workdir, final Project project) {
    Objects.requireNonNull(workdir);
    if (!workdir.isAbsolute()) {
      throw new IllegalArgumentException("Working directory must be an absolute path");
    }

    if (!Files.isDirectory(workdir)) {
      throw new IllegalArgumentException("Working directory must be an existing directory");
    }

    Objects.requireNonNull(project);
    compile(workdir, project);
  }

  private static void compile(final Path workdir, final Project project) {
    log.info("[project={}] Compiling main source set", project.id());
    final SourceSet prodSourceSet = project.mainSourceSet();
    final Path sourcesDirectory = workdir
        .resolve(project.path())
        .resolve("src")
        .resolve(prodSourceSet.name())
        .resolve("java");

    final var sources = new ArrayList<Path>();
    try (final var stream = Files.newDirectoryStream(sourcesDirectory, "**/*.java")) {
      stream.forEach(sources::add);
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
    final var task = compiler.getTask(null, fileManager, diagnosticListener, null, null, compUnits);
    final boolean result = task.call();
    if (result) {
      log.info("[project={}] Compilation succeeded", project.id());
    } else {
      log.error("[project={}] Compilation failed", project.id());
    }
  }
}
