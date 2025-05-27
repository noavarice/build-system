package com.github.build;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

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
  public static boolean compile(final Path workdir, final Project project) {
    checkWorkdir(workdir);
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
    final var fileVisitor = new CollectingFileVisitor(
        file -> file.getFileName().toString().endsWith(".java"),
        sources::add
    );
    try {
      // for some reason, newDirectoryStream with glob does not work as expected
      // TODO: consider using newDirectoryStream with glob
      Files.walkFileTree(sourcesDirectory, fileVisitor);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    log.debug("[project={}] Compiling {} files: {}", project.id(), sources.size(), sources);

    final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    final DiagnosticListener<JavaFileObject> diagnosticListener = diagnostic -> {
      final Level level = switch (diagnostic.getKind()) {
        case ERROR -> Level.ERROR;
        case WARNING, MANDATORY_WARNING -> Level.WARN;
        case NOTE -> Level.INFO;
        case OTHER -> Level.DEBUG;
      };
      log.atLevel(level).log("[project={}] {}", project.id(), diagnostic);
    };
    final var fileManager = compiler.getStandardFileManager(
        diagnosticListener,
        Locale.US,
        StandardCharsets.UTF_8
    );

    final List<Path> classPath = prodSourceSet.dependencies()
        .stream()
        .map(d -> switch (d) {
          case Dependency.File file -> file.path();
        })
        .toList();
    try {
      final var classPathFiles = classPath
          .stream()
          .map(Path::toFile)
          .toList();
      fileManager.setLocation(StandardLocation.CLASS_PATH, classPathFiles);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    final var compUnits = fileManager.getJavaFileObjectsFromPaths(sources);

    final Path buildDirectory = workdir
        .resolve(project.path())
        .resolve(project.artifactLayout().rootDir());
    final Path classesDir = buildDirectory.resolve(project.artifactLayout().classesDir());
    final Path prodSourceSetClassesDir = classesDir
        .resolve(prodSourceSet.name())
        .normalize()
        .toAbsolutePath();
    try {
      Files.createDirectories(prodSourceSetClassesDir);
      fileManager.setLocation(
          StandardLocation.CLASS_OUTPUT,
          List.of(prodSourceSetClassesDir.toFile())
      );
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    final var task = compiler.getTask(
        new LogWriter(project.id()),
        fileManager,
        diagnosticListener,
        null,
        null,
        compUnits
    );
    task.setLocale(Locale.US);
    final boolean result = task.call();
    if (result) {
      log.info("[project={}] Compilation succeeded", project.id());
    } else {
      log.error("[project={}] Compilation failed", project.id());
    }

    return result;
  }

  /**
   * Writes compiler output to {@link Logger}.
   */
  private static final class LogWriter extends Writer {

    private final Project.Id projectId;

    private LogWriter(final Project.Id projectId) {
      this.projectId = projectId;
    }

    @Override
    public void write(final char[] cbuf, final int off, final int len) {
      final var compilerMessage = new String(cbuf);
      log.debug("[project={}] [compiler] {}", projectId, compilerMessage);
    }

    @Override
    public void flush() {
      // do nothing
    }

    @Override
    public void close() {
      // do nothing
    }
  }

  public static void createJar(final Path workdir, final Project project) {
    checkWorkdir(workdir);
    Objects.requireNonNull(project);
    final var jarPath = workdir
        .resolve(project.artifactLayout().rootDir())
        .resolve(project.id() + ".jar")
        .normalize();
    log.info("[project={}] Create jar at {}", project.id(), jarPath);
    final var prodSourceSet = project.mainSourceSet();
    final Path buildDirectory = workdir
        .resolve(project.path())
        .resolve(project.artifactLayout().rootDir());
    final Path classesDir = buildDirectory.resolve(project.artifactLayout().classesDir());
    final Path prodSourceSetClassesDir = classesDir
        .resolve(prodSourceSet.name())
        .normalize()
        .toAbsolutePath();
    // not creating directories because they should be created during project compilation

    final var jarContents = new HashMap<Path, JarConfig.Content>();
    final var classFileVisitor = new CollectingFileVisitor(
        file -> file.getFileName().toString().endsWith(".class"),
        file -> {
          final var relativePath = prodSourceSetClassesDir.relativize(file);
          jarContents.put(relativePath, new JarConfig.Content.File(file));
        }
    );

    try {
      Files.walkFileTree(prodSourceSetClassesDir, classFileVisitor);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    log.debug("[project={}] Creating jar out of {} files: {}",
        project.id(),
        jarContents.size(),
        jarContents.keySet()
    );

    final var config = new JarConfig(jarPath, jarContents);
    createJar(config);
  }

  public static void createJar(final JarConfig config) {
    Objects.requireNonNull(config);
    log.debug("Creating JAR file at {}", config.path());
    try (final OutputStream os = Files.newOutputStream(config.path())) {
      final var jos = new JarOutputStream(os);
      config.contents().forEach((path, content) -> {
        log.trace("Writing {} to JAR {}", path, config.path());
        writeJarEntry(jos, path, content);
      });
      jos.finish();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void writeJarEntry(
      final JarOutputStream jos,
      final Path path,
      final JarConfig.Content content
  ) {
    final byte[] bytes = switch (content) {
      case JarConfig.Content.Bytes b -> b.value();
      case JarConfig.Content.File f -> {
        try {
          yield Files.readAllBytes(f.path());
        } catch (final IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    };

    final var entry = new ZipEntry(path.normalize().toString());
    try {
      jos.putNextEntry(entry);
      jos.write(bytes);
      jos.closeEntry();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void checkWorkdir(final Path workdir) {
    Objects.requireNonNull(workdir);
    if (!workdir.isAbsolute()) {
      throw new IllegalArgumentException("Working directory must be an absolute path");
    }

    if (!Files.isDirectory(workdir)) {
      throw new IllegalArgumentException("Working directory must be an existing directory");
    }
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
