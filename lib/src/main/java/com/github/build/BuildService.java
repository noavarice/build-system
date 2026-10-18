package com.github.build;

import com.github.build.compile.AspectJCompileArgs;
import com.github.build.compile.AspectJCompileService;
import com.github.build.compile.CompilationResult;
import com.github.build.compile.JavaCompileArgs;
import com.github.build.compile.JavaCompileService;
import com.github.build.compile.JavaCompilerOptions;
import com.github.build.compile.KotlinCompileService;
import com.github.build.deps.Dependency;
import com.github.build.deps.DependencyConstraints;
import com.github.build.deps.DependencyService;
import com.github.build.deps.GroupArtifactVersion;
import com.github.build.jar.JarArgs;
import com.github.build.jar.JarManifest;
import com.github.build.jar.JarService;
import com.github.build.util.FileUtils;
import com.github.build.util.PathUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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

  private final JavaCompileService javaCompileService;

  private final KotlinCompileService kotlinCompileService;

  private final AspectJCompileService aspectJCompileService;

  private final DependencyService dependencyService;

  private final JarService jarService;

  public BuildService(
      final JavaCompileService javaCompileService,
      final DependencyService dependencyService,
      final JarService jarService
  ) {
    this.javaCompileService = Objects.requireNonNull(javaCompileService);
    this.kotlinCompileService = new KotlinCompileService();
    this.aspectJCompileService = new AspectJCompileService();
    this.dependencyService = Objects.requireNonNull(dependencyService);
    this.jarService = jarService;
  }

  /**
   * Resolves the compile classpath for the specified source set. Includes locally built
   * artifacts (JARs, projects) and remotely resolved dependencies.
   *
   * @param workdir   Build working directory
   * @param sourceSet Source set to resolve compile classpath for
   * @return Compile classpath
   */
  public Set<Path> compileClasspath(final Path workdir, final SourceSet sourceSet) {
    Objects.requireNonNull(workdir);
    Objects.requireNonNull(sourceSet);
    PathUtils.checkAbsolute(workdir);

    final Set<Path> classpath = new HashSet<>();
    addSourceSetCompileClasspath(sourceSet, classpath);
    return classpath;
  }

  /**
   * Compiles Java sources of a source set with javac into a dedicated Java output directory.
   *
   * @param workdir         Build working directory
   * @param sourceSet       Source set to compile
   * @param compilerOptions Java compiler options
   * @return Compilation result with the Java class output directory
   */
  public CompilationResult compileJava(
      final Path workdir,
      final SourceSet sourceSet,
      final JavaCompilerOptions compilerOptions
  ) {
    Objects.requireNonNull(workdir);
    Objects.requireNonNull(sourceSet);
    Objects.requireNonNull(compilerOptions);

    PathUtils.checkAbsolute(workdir);

    final Project project = sourceSet.project();
    final SourceSet.Id sourceSetId = sourceSet.id();
    log.info("[project={}] [ss={}] Compiling Java", project.artifactId(), sourceSetId);

    final Path outputDirectory = classOutputDirectory(
        workdir, project, sourceSetId, Language.JAVA
    );
    createDirectories(outputDirectory);

    final Set<Path> sources = collectJavaSources(workdir, project, sourceSetId);
    if (sources.isEmpty()) {
      log.info("[project={}] [ss={}] No Java sources found, do nothing", project.artifactId(),
          sourceSetId);
      return CompilationResult.success(outputDirectory);
    }

    final Set<Path> classpath = new HashSet<>();
    try {
      addSourceSetCompileClasspath(sourceSet, classpath);
    } catch (final IllegalStateException e) {
      log.error("[project={}] [ss={}] Failed to gather compilation classpath",
          project.artifactId(),
          sourceSetId,
          e
      );
      return CompilationResult.failure(outputDirectory);
    }

    // Kotlin is compiled before Java and its classes may be referenced from Java sources
    for (final Language sibling : Language.values()) {
      if (sibling == Language.JAVA) {
        continue;
      }

      final Path siblingDirectory = classOutputDirectory(workdir, project, sourceSetId, sibling);
      if (Files.isDirectory(siblingDirectory)) {
        classpath.add(siblingDirectory);
      }
    }

    final boolean successful = javaCompileService.compile(
        new JavaCompileArgs(sources, outputDirectory, classpath, compilerOptions)
    );
    return successful
        ? CompilationResult.success(outputDirectory)
        : CompilationResult.failure(outputDirectory);
  }

  /**
   * Compiles sources described by pre-built {@link JavaCompileArgs}. Useful for sources that are
   * not described by a {@link SourceSet}.
   *
   * @param args Compilation arguments
   * @return Compilation result with the directory from {@link JavaCompileArgs#classesDir()}
   */
  public CompilationResult compileJava(final JavaCompileArgs args) {
    Objects.requireNonNull(args);
    final boolean successful = javaCompileService.compile(args);
    return successful
        ? CompilationResult.success(args.classesDir())
        : CompilationResult.failure(args.classesDir());
  }

  /**
   * Compiles Kotlin sources of a source set into a dedicated Kotlin output directory. Java sources
   * are passed to the Kotlin compiler as source roots for resolution and must be compiled
   * separately with {@link #compileJava(Path, SourceSet, JavaCompilerOptions)}.
   *
   * @param workdir   Build working directory
   * @param sourceSet Source set to compile
   * @return Compilation result with the Kotlin class output directory
   */
  public CompilationResult compileKotlin(final Path workdir, final SourceSet sourceSet) {
    Objects.requireNonNull(workdir);
    Objects.requireNonNull(sourceSet);

    PathUtils.checkAbsolute(workdir);

    final Project project = sourceSet.project();
    final Path outputDirectory = classOutputDirectory(
        workdir, project, sourceSet.id(), Language.KOTLIN
    );
    final Set<Path> classpath = compileClasspath(workdir, sourceSet);
    final boolean successful = kotlinCompileService.compile(
        workdir, sourceSet, outputDirectory, classpath
    );
    return successful
        ? CompilationResult.success(outputDirectory)
        : CompilationResult.failure(outputDirectory);
  }

  /**
   * Compiles AspectJ and Java sources of a source set with the AspectJ compiler into a dedicated
   * AspectJ output directory.
   *
   * <p>When compiling a test source set, the main source set AspectJ output is added to the aspect
   * path so test classes are woven against the main aspects at compile time.
   *
   * @param workdir   Build working directory
   * @param sourceSet Source set to compile
   * @return Compilation result with the AspectJ class output directory
   */
  public CompilationResult compileAspectJ(final Path workdir, final SourceSet sourceSet) {
    Objects.requireNonNull(workdir);
    Objects.requireNonNull(sourceSet);

    PathUtils.checkAbsolute(workdir);

    final Project project = sourceSet.project();
    final SourceSet.Id sourceSetId = sourceSet.id();
    final Path outputDirectory = classOutputDirectory(
        workdir, project, sourceSetId, Language.ASPECTJ
    );
    final Set<Path> classpath = compileClasspath(workdir, sourceSet);
    final Set<Path> sourceRoots = resolveDirectories(
        workdir, project, sourceSet.aspectjSourceRoots()
    );

    final Set<Path> aspectPath = new HashSet<>();
    final Path outXmlFile;
    if (sourceSetId.equals(SourceSet.Id.TEST)) {
      // main aspects weave test classes at compile time
      final Path mainOutputDirectory = classOutputDirectory(
          workdir, project, SourceSet.Id.MAIN, Language.ASPECTJ
      );
      aspectPath.add(mainOutputDirectory);
      classpath.add(mainOutputDirectory);
      outXmlFile = null;
    } else if (sourceSetId.equals(SourceSet.Id.MAIN)) {
      outXmlFile = Path.of("META-INF", "aop.xml");
    } else {
      outXmlFile = null;
    }

    final boolean successful = aspectJCompileService.compile(
        new AspectJCompileArgs(sourceRoots, outputDirectory, classpath, aspectPath, outXmlFile)
    );
    return successful
        ? CompilationResult.success(outputDirectory)
        : CompilationResult.failure(outputDirectory);
  }

  private static Path classOutputDirectory(
      final Path workdir,
      final Project project,
      final SourceSet.Id sourceSetId,
      final Language language
  ) {
    return workdir
        .resolve(project.path())
        .resolve(project.artifactLayout().rootDir())
        .resolve(project.artifactLayout().classesDir())
        .resolve(sourceSetId.toString())
        .resolve(language.directory());
  }

  private static Set<Path> resolveDirectories(
      final Path workdir,
      final Project project,
      final SourceRoots sourceRoots
  ) {
    final Set<Path> directories = new HashSet<>();
    for (final Path relativeDir : sourceRoots.directories()) {
      final Path directory = workdir.resolve(project.path()).resolve(relativeDir);
      if (Files.isDirectory(directory)) {
        directories.add(directory);
      }
    }
    return directories;
  }

  private static void createDirectories(final Path directory) {
    try {
      Files.createDirectories(directory);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void addSourceSetCompileClasspath(
      final SourceSet sourceSet,
      final Collection<Path> classpath
  ) {
    for (final Dependency dependency : sourceSet.compileDependencies()) {
      switch (dependency) {
        case Dependency.OnProject ignored -> {
          // will be resolved as "remote" dependency
        }
        case Dependency.OnSourceSet ignored -> {
          // will be resolved as "remote" dependency
        }
        case Dependency.Jar file -> classpath.add(file.path());
        case Dependency.Remote.WithVersion ignored -> {
          // will be resolved later
        }
        case Dependency.Remote.WithoutVersion withoutVersion -> {
          final DependencyConstraints constraints = sourceSet.dependencyConstraints();
          @Nullable
          final String version = constraints.getConstraint(withoutVersion.ga());
          if (version == null) {
            log.error("Dependency has no version and no associated source set-wise constraint");
            throw new IllegalStateException();
          }

          // will be resolved later
        }
      }
    }

    final Map<GroupArtifactVersion, Path> localArtifacts = dependencyService.resolveCompileClasspath(
        sourceSet
    );
    classpath.addAll(localArtifacts.values());
  }

  /**
   * Collects Java source files matching the source set's Java source roots glob.
   */
  private static Set<Path> collectJavaSources(
      final Path workdir,
      final Project project,
      final SourceSet.Id sourceSetId
  ) {
    final var sources = new HashSet<Path>();
    // for some reason, newDirectoryStream with glob does not work as expected
    // TODO: consider using newDirectoryStream with glob
    final SourceSet sourceSet = project.sourceSet(sourceSetId);
    final SourceRoots javaSourceRoots = sourceSet.javaSourceRoots();
    final PathMatcher matcher = javaSourceRoots.matcher();
    for (final Path relativeDir : javaSourceRoots.directories()) {
      final Path sourceDir = workdir
          .resolve(project.path())
          .resolve(relativeDir);
      if (Files.notExists(sourceDir)) {
        log.debug("[project={}][ss={}] Source directory {} does not exist",
            project.artifactId(),
            sourceSetId,
            sourceDir
        );
        continue;
      }

      if (!Files.isDirectory(sourceDir)) {
        log.warn(
            "[project={}][ss={}] {} assumed to be source directory but it's not a directory",
            project.artifactId(),
            sourceSetId,
            sourceDir
        );
        continue;
      }

      final var sourcesPart = new ArrayList<Path>();
      final var fileVisitor = new CollectingFileVisitor(
          file -> matcher.matches(sourceDir.relativize(file)),
          sourcesPart::add
      );

      try {
        Files.walkFileTree(sourceDir, fileVisitor);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }

      if (sourcesPart.isEmpty()) {
        log.debug("[project={}][ss={}] No source files found in directory {}",
            project.artifactId(),
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

  /**
   * Copies resources from project's main source set to a project's resource directory.
   *
   * @param workdir     Working directory
   * @param project     Project
   * @param sourceSetId Source set ID
   * @see Project.ArtifactLayout#resourcesDir()
   */
  public void copyResources(
      final Path workdir,
      final Project project,
      final SourceSet.Id sourceSetId
  ) {
    Objects.requireNonNull(workdir);
    Objects.requireNonNull(project);
    Objects.requireNonNull(sourceSetId);

    copyResources(workdir, project.sourceSet(sourceSetId));
  }

  public void copyResources(final Path workdir, final SourceSet sourceSet) {
    Objects.requireNonNull(workdir);
    Objects.requireNonNull(sourceSet);

    PathUtils.checkAbsolute(workdir);
    PathUtils.checkDirectory(workdir);

    final Project project = sourceSet.project();
    final SourceSet.Id sourceSetId = sourceSet.id();
    log.info("[project={}][ss={}] Copying resources", project.artifactId(), sourceSetId);

    final Path targetDir = workdir
        .resolve(project.path())
        .resolve(project.artifactLayout().rootDir())
        .resolve(project.artifactLayout().resourcesDir())
        .resolve(sourceSetId.value());

    if (Files.isDirectory(targetDir)) {
      FileUtils.delete(targetDir);
    }

    try {
      // always creating directory, even if there's nothing to copy
      Files.createDirectories(targetDir);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    for (final Path dir : sourceSet.resourceDirectories()) {
      final var absolutePath = workdir
          .resolve(project.path())
          .resolve(dir);
      log.info("[project={}][ss={}] Copying resources from {}",
          project.artifactId(),
          sourceSetId,
          absolutePath
      );
      FileUtils.copyDirectory(absolutePath, targetDir);
    }
  }

  /**
   * @deprecated Use generic
   *     {@link #createJar(Path, SourceSet, Collection, Map, JarManifest)}
   */
  @Deprecated(forRemoval = true)
  public void createJar(
      final Path workdir,
      final Project project,
      final Collection<Path> classDirectories,
      final Map<Path, JarArgs.Content> additionalEntries,
      @Nullable final JarManifest manifest
  ) {
    createJar(workdir, project.mainSourceSet(), classDirectories, additionalEntries, manifest);
  }

  /**
   * Creates a JAR from the given compiled class directories and the source set resources.
   *
   * @param workdir           Working directory
   * @param sourceSet         Source set the JAR is created for
   * @param classDirectories  Directories containing compiled classes to put in the JAR
   * @param additionalEntries Additional JAR entries
   * @param manifest          JAR manifest
   */
  public void createJar(
      final Path workdir,
      final SourceSet sourceSet,
      final Collection<Path> classDirectories,
      final Map<Path, JarArgs.Content> additionalEntries,
      @Nullable final JarManifest manifest
  ) {
    Objects.requireNonNull(workdir);
    Objects.requireNonNull(sourceSet);
    Objects.requireNonNull(classDirectories);
    Objects.requireNonNull(additionalEntries);

    PathUtils.checkAbsolute(workdir);
    PathUtils.checkDirectory(workdir);

    final Project project = sourceSet.project();
    final SourceSet.Id sourceSetId = sourceSet.id();

    log.info("[project={}][ss={}] Creating JAR", project.artifactId(), sourceSetId);
    final String jarName = sourceSetId.equals(SourceSet.Id.MAIN)
        ? project.artifactId() + ".jar"
        : project.artifactId() + "-" + sourceSetId + ".jar";
    final Path jarPath = workdir
        .resolve(project.path())
        .resolve(project.artifactLayout().rootDir())
        // TODO: customize JAR path/filename
        .resolve(jarName);
    final var content = new HashMap<Path, JarArgs.Content>();

    // collect compiled classes
    for (final Path classDirectory : classDirectories) {
      if (!Files.isDirectory(classDirectory)) {
        log.debug("[project={}][ss={}] Class output directory {} does not exist",
            project.artifactId(), sourceSetId, classDirectory);
        continue;
      }

      try {
        Files.walkFileTree(classDirectory, new CollectJarContent(classDirectory, content));
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    // collect resources
    final Path resourcesDir = workdir
        .resolve(project.path()).resolve(project.artifactLayout().rootDir())
        .resolve(project.artifactLayout().resourcesDir())
        .resolve(sourceSetId.value());
    try {
      Files.walkFileTree(resourcesDir, new CollectJarContent(resourcesDir, content));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    content.putAll(additionalEntries);

    final var args = new JarArgs(jarPath, content, manifest);
    jarService.create(args);
  }

  /**
   * Collects files from specified directory to be put in the JAR relative to the same directory.
   *
   * @param rootDir Directory to collect files from
   * @param content Future JAR content
   */
  private record CollectJarContent(
      Path rootDir,
      Map<Path, JarArgs.Content> content
  ) implements FileVisitor<Path> {

    private CollectJarContent {
      Objects.requireNonNull(rootDir);
      Objects.requireNonNull(content);
    }

    @Override
    public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
      final Path pathInJar = rootDir.relativize(file);
      final var fileContent = new JarArgs.Content.File(file);
      content.put(pathInJar, fileContent);
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
   * Cleans build output for the specified project.
   *
   * @param workdir Build working directory
   * @param project Project to clean
   */
  public void clean(final Path workdir, final Project project) {
    Objects.requireNonNull(workdir);
    Objects.requireNonNull(project);
    PathUtils.checkAbsolute(workdir);

    log.info("[project={}] Cleaning build output directory", project.artifactId());
    final Path buildOutputDir = workdir
        .resolve(project.path())
        .resolve(project.artifactLayout().rootDir());
    FileUtils.delete(buildOutputDir);
  }
}
