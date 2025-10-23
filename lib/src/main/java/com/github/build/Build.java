package com.github.build;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.toUnmodifiableSet;

import com.github.build.Project.ArtifactLayout;
import com.github.build.deps.Dependency;
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
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.jspecify.annotations.Nullable;
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

  public static boolean compileMain(final Path workdir, final Project project) {
    Objects.requireNonNull(workdir);
    Objects.requireNonNull(project);
    PathUtils.checkAbsolute(workdir);

    @Nullable
    final SourceSet mainSourceSet = project.sourceSets().get(SourceSet.Id.MAIN);
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
    return compile(compileArgs);
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

  /**
   * Performs compilation with specified arguments.
   *
   * @param args Compilation arguments (sources, classes directory, etc.)
   * @return True if compilation succeeds, false otherwise
   */
  public static boolean compile(final CompileArgs args) {
    Objects.requireNonNull(args);
    log.debug("Compiling {} files: {}", args.sources().size(), args.sources());

    final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    final DiagnosticListener<JavaFileObject> diagnosticListener = diagnostic -> {
      final Level level = switch (diagnostic.getKind()) {
        case ERROR -> Level.ERROR;
        case WARNING, MANDATORY_WARNING -> Level.WARN;
        case NOTE -> Level.INFO;
        case OTHER -> Level.DEBUG;
      };
      log.atLevel(level).log("{}", diagnostic);
    };

    final var fileManager = compiler.getStandardFileManager(
        diagnosticListener,
        Locale.US,
        StandardCharsets.UTF_8
    );

    final var classPathFiles = args.classpath()
        .stream()
        .map(Path::toFile)
        .toList();
    try {
      fileManager.setLocation(StandardLocation.CLASS_PATH, classPathFiles);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    try {
      Files.createDirectories(args.classesDir());
      fileManager.setLocation(
          StandardLocation.CLASS_OUTPUT,
          List.of(args.classesDir().toFile())
      );
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    final var compUnits = fileManager.getJavaFileObjectsFromPaths(args.sources());
    final var task = compiler.getTask(
        new LogWriter(),
        fileManager,
        diagnosticListener,
        null,
        null,
        compUnits
    );
    task.setLocale(Locale.US);
    final boolean result = task.call();
    if (result) {
      log.info("Compilation succeeded");
    } else {
      log.error("Compilation failed");
    }

    return result;
  }

  /**
   * Writes compiler output to {@link Logger}.
   */
  private static final class LogWriter extends Writer {

    @Override
    public void write(final char[] cbuf, final int off, final int len) {
      final var compilerMessage = new String(cbuf);
      log.debug("[compiler] {}", compilerMessage);
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

  public static void createJar(final JarArgs config) {
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
      final JarArgs.Content content
  ) {
    final byte[] bytes = switch (content) {
      case JarArgs.Content.Bytes b -> b.value();
      case JarArgs.Content.File f -> {
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
   * Copies resources from project's main source set to a {@link ArtifactLayout#resourcesDir()}.
   *
   * @param workdir Working directory
   * @param project Project info
   */
  public static void copyResources(Path workdir, final Project project) {
    Objects.requireNonNull(workdir);
    PathUtils.checkAbsolute(workdir);
    PathUtils.checkDirectory(workdir);
    workdir = workdir.normalize();

    Objects.requireNonNull(project);

    log.info("[project={}] Copying resources from main source set", project.id());
    final SourceSet mainSourceSet = project.mainSourceSet();
    final Path targetDir = workdir
        .resolve(project.path())
        .resolve(project.artifactLayout().rootDir())
        .resolve(project.artifactLayout().resourcesDir())
        .resolve(mainSourceSet.id().value());

    if (Files.isDirectory(targetDir)) {
      deleteDirectory(targetDir);
    }

    try {
      // always creating directory, even if there's nothing to copy
      Files.createDirectories(targetDir);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    for (final Path dir : mainSourceSet.resourceDirectories()) {
      final var absolutePath = workdir
          .resolve(project.path())
          .resolve(mainSourceSet.path())
          .resolve(dir);
      copyDirectory(absolutePath, targetDir);
    }
  }

  /**
   * Copies contents of the source directory recursively to a target directory.
   *
   * @param sourceDir Path to directory to be copied
   * @param targetDir Path to directory to copy to
   */
  public static void copyDirectory(final Path sourceDir, final Path targetDir) {
    if (Files.isRegularFile(sourceDir)) {
      throw new IllegalArgumentException("Source path must not be a file");
    }

    if (Files.notExists(sourceDir)) {
      log.debug("{} does not exist, do nothing", sourceDir);
      return;
    }

    if (Files.isRegularFile(targetDir)) {
      throw new IllegalArgumentException("Target path must not be a file");
    }

    copyToNonExistentDirectory(sourceDir, targetDir);
  }

  /**
   * Deletes specified directory and all of its content recursively.
   *
   * @param directory Path to directory
   */
  public static void deleteDirectory(final Path directory) {
    log.info("Removing directory {}", directory);
    Objects.requireNonNull(directory);
    if (!Files.isDirectory(directory)) {
      log.info("{} is not a directory (it's a file or it does not exist)", directory);
      return;
    }

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
      if (pathInsideTarget.equals(targetDir)) {
        if (Files.notExists(targetDir)) {
          try {
            Files.createDirectory(targetDir);
          } catch (final IOException e) {
            throw new UncheckedIOException(e);
          }
        }
        continue;
      }

      if (Files.isDirectory(path)) {
        if (Files.notExists(pathInsideTarget)) {
          try {
            Files.createDirectory(pathInsideTarget);
          } catch (final IOException e) {
            throw new UncheckedIOException(e);
          }
        }
      } else {
        try {
          Files.copy(path, pathInsideTarget, StandardCopyOption.REPLACE_EXISTING);
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
}
