package com.github.build;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.reverseOrder;

import com.github.build.util.PathUtils;
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
import java.util.Comparator;
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
  public static boolean compile(Path workdir, final Project project) {
    Objects.requireNonNull(workdir);
    PathUtils.checkAbsolute(workdir);
    PathUtils.checkDirectory(workdir);
    workdir = workdir.normalize();

    Objects.requireNonNull(project);

    log.info("[project={}] Compiling main source set", project.id());
    final SourceSet prodSourceSet = project.mainSourceSet();
    final Path projectPath = workdir.resolve(project.path());
    PathUtils.checkDirectory(projectPath);

    final List<Path> sourceDirectories = prodSourceSet.sourceDirectories()
        .stream()
        .map(projectPath::resolve)
        .peek(PathUtils::checkDirectory)
        .toList();
    final List<Path> sources = collectSources(project.id(), sourceDirectories);

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
        .resolve(prodSourceSet.id().value())
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
   * Collects files ending with ".java" in the specified directories.
   * <p>
   * TODO: remove project ID parameter - it's only for logging. Consider using MDC
   *
   * @param id                Project ID for logging
   * @param sourceDirectories Directories to search for source files in
   * @return List of source files, never null but can be empty
   */
  private static List<Path> collectSources(
      final Project.Id id,
      final List<Path> sourceDirectories
  ) {
    final var sources = new ArrayList<Path>();
    // for some reason, newDirectoryStream with glob does not work as expected
    // TODO: consider using newDirectoryStream with glob
    for (final Path path : sourceDirectories) {
      final var sourcesPart = new ArrayList<Path>();
      final var fileVisitor = new CollectingFileVisitor(
          file -> file.getFileName().toString().endsWith(".java"),
          sourcesPart::add
      );

      try {
        Files.walkFileTree(path, fileVisitor);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }

      if (sourcesPart.isEmpty()) {
        log.debug("[project={}] {} contains no source files", id, path);
      } else {
        sources.addAll(sourcesPart);
      }
    }

    return List.copyOf(sources);
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

  public static void createJar(Path workdir, final Project project) {
    Objects.requireNonNull(workdir);
    PathUtils.checkAbsolute(workdir);
    PathUtils.checkDirectory(workdir);
    workdir = workdir.normalize();

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
        .resolve(prodSourceSet.id().value())
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

  /**
   * Copies contents of the source directory recursively to a target directory.
   *
   * @param sourceDir Path to directory to be copied
   * @param targetDir Path to directory to copy to
   */
  public static void copyDirectory(final Path sourceDir, final Path targetDir) {
    if (!Files.isDirectory(sourceDir)) {
      throw new IllegalArgumentException("Source path must be a directory");
    }

    if (Files.isRegularFile(targetDir)) {
      throw new IllegalArgumentException("Target path must not be a file");
    }

    if (Files.isDirectory(targetDir)) {
      log.debug("Target directory {} exists, removing content", targetDir);
      removeDirectoryRecursively(targetDir);
    }

    copyToNonExistentDirectory(sourceDir, targetDir);
  }

  private static void removeDirectoryRecursively(final Path directory) {
    final List<Path> paths = listAll(directory, reverseOrder());
    log.debug("Files and directories to remove, in order: {}", paths);
    for (final Path path : paths) {
      log.trace("Removing {}", path);
      try {
        Files.delete(path);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  private static void copyToNonExistentDirectory(final Path sourceDir, final Path targetDir) {
    final List<Path> content = listAll(sourceDir, naturalOrder());
    for (final Path path : content) {
      final Path relativePath = sourceDir.relativize(path);
      final Path pathInsideTarget = targetDir.resolve(relativePath);
      if (Files.isDirectory(path)) {
        try {
          Files.createDirectory(pathInsideTarget);
        } catch (final IOException e) {
          throw new UncheckedIOException(e);
        }
      } else {
        try {
          Files.copy(path, pathInsideTarget);
        } catch (final IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }
  }

  private static List<Path> listAll(final Path directory, final Comparator<Path> comparator) {
    try (final var stream = Files.walk(directory)) {
      return stream
          .sorted(comparator)
          .toList();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
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
