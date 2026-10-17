package com.github.build;

import com.github.build.compile.CompileArgs;
import com.github.build.compile.CompileService;
import com.github.build.compile.CompilerOptions;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.jetbrains.kotlin.cli.common.ExitCode;
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compiles source sets that mix Kotlin and Java.
 *
 * <p>Kotlin is compiled first with the Java sources exposed as source roots so Kotlin can resolve
 * Java symbols. The emitted classes are then put on javac's classpath, which makes Java able to
 * resolve the Kotlin classes it references. Both compilers write to the same classes directory.
 *
 * @author noavarice
 * @since 1.0.0
 */
public final class KotlinCompileService {

  private static final Logger log = LoggerFactory.getLogger(KotlinCompileService.class);

  private static final String KOTLIN_JVM_TARGET = "17";

  private static final String KOTLIN_LANGUAGE_VERSION = "2.2";

  private final CompileService compileService;

  public KotlinCompileService(final CompileService compileService) {
    this.compileService = Objects.requireNonNull(compileService);
  }

  /**
   * @return True if the source set contains at least one Kotlin source file
   */
  public static boolean hasKotlinSources(final Path workdir, final SourceSet sourceSet) {
    Objects.requireNonNull(workdir);
    Objects.requireNonNull(sourceSet);

    final Project project = sourceSet.project();
    for (final Path relativeDir : sourceSet.sourceDirectories()) {
      final Path sourceDir = workdir.resolve(project.path()).resolve(relativeDir);
      if (!Files.isDirectory(sourceDir)) {
        continue;
      }

      try (final Stream<Path> files = Files.walk(sourceDir)) {
        if (files.anyMatch(file -> file.getFileName().toString().endsWith(KOTLIN_EXTENSION))) {
          return true;
        }
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    return false;
  }

  /**
   * Compiles Kotlin and Java sources of a source set.
   *
   * @param workdir         Build working directory (absolute)
   * @param sourceSet       Source set to compile
   * @param classesDir      Directory to put output classes into
   * @param classpath       Compilation classpath
   * @param compilerOptions Java compiler options
   * @return True if both Kotlin and Java compilation succeed, false otherwise
   */
  public boolean compile(
      final Path workdir,
      final SourceSet sourceSet,
      final Path classesDir,
      final Set<Path> classpath,
      final CompilerOptions compilerOptions
  ) {
    Objects.requireNonNull(workdir);
    Objects.requireNonNull(sourceSet);
    Objects.requireNonNull(classesDir);
    Objects.requireNonNull(classpath);
    Objects.requireNonNull(compilerOptions);

    final Project project = sourceSet.project();
    final SourceSet.Id sourceSetId = sourceSet.id();

    final List<Path> kotlinSources = new ArrayList<>();
    final List<Path> javaSources = new ArrayList<>();
    final List<Path> javaSourceRoots = new ArrayList<>();
    for (final Path relativeDir : sourceSet.sourceDirectories()) {
      final Path sourceDir = workdir.resolve(project.path()).resolve(relativeDir);
      if (!Files.isDirectory(sourceDir)) {
        continue;
      }

      javaSourceRoots.add(sourceDir);
      collectSources(sourceDir, KOTLIN_EXTENSION, kotlinSources);
      collectSources(sourceDir, JAVA_EXTENSION, javaSources);
    }

    if (kotlinSources.isEmpty() && javaSources.isEmpty()) {
      log.info("[project={}] [ss={}] No sources found, do nothing",
          project.artifactId(), sourceSetId);
      return true;
    }

    try {
      Files.createDirectories(classesDir);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    if (!kotlinSources.isEmpty()) {
      log.info("[project={}] [ss={}] Compiling Kotlin ({} files)",
          project.artifactId(), sourceSetId, kotlinSources.size());
      if (!compileKotlin(classesDir, classpath, javaSourceRoots, kotlinSources)) {
        return false;
      }
    }

    if (javaSources.isEmpty()) {
      return true;
    }

    log.info("[project={}] [ss={}] Compiling Java ({} files)",
        project.artifactId(), sourceSetId, javaSources.size());
    final var javaClasspath = new LinkedHashSet<>(classpath);
    javaClasspath.add(classesDir);
    return compileService.compile(new CompileArgs(
        new LinkedHashSet<>(javaSources),
        classesDir,
        javaClasspath,
        compilerOptions
    ));
  }

  private boolean compileKotlin(
      final Path classesDir,
      final Set<Path> classpath,
      final List<Path> javaSourceRoots,
      final List<Path> kotlinSources
  ) {
    final var args = new ArrayList<String>();
    args.add("-classpath");
    args.add(join(classpath, File.pathSeparatorChar));
    args.add("-d");
    args.add(classesDir.toString());
    args.add("-jvm-target");
    args.add(KOTLIN_JVM_TARGET);
    args.add("-language-version");
    args.add(KOTLIN_LANGUAGE_VERSION);
    args.add("-api-version");
    args.add(KOTLIN_LANGUAGE_VERSION);
    args.add("-Xjsr305=strict");
    args.add("-Xsuppress-version-warnings");
    // the stdlib comes from the resolved classpath, not the compiler's own distribution
    args.add("-no-stdlib");
    // the Kotlin CLI expects a comma-separated list here, not the platform path separator
    args.add("-Xjava-source-roots=" + join(javaSourceRoots, ','));
    for (final Path source : kotlinSources) {
      args.add(source.toString());
    }

    log.debug("Compiling Kotlin with args: {}", args);
    final ExitCode exitCode = new K2JVMCompiler().exec(System.err, args.toArray(String[]::new));
    if (exitCode != ExitCode.OK) {
      log.error("Kotlin compilation failed: {}", exitCode);
      return false;
    }

    return true;
  }

  private static String join(final Iterable<Path> paths, final char separator) {
    final var result = new StringBuilder();
    for (final Path path : paths) {
      if (!result.isEmpty()) {
        result.append(separator);
      }
      result.append(path);
    }
    return result.toString();
  }

  private static void collectSources(
      final Path sourceDir,
      final String extension,
      final List<Path> sink
  ) {
    try (final Stream<Path> files = Files.walk(sourceDir)) {
      files
          .filter(Files::isRegularFile)
          .filter(file -> file.getFileName().toString().endsWith(extension))
          .forEach(sink::add);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static final String KOTLIN_EXTENSION = ".kt";

  private static final String JAVA_EXTENSION = ".java";
}
