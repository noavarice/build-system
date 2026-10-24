package com.github.build.compile;

import com.github.build.Project;
import com.github.build.SourceRoots;
import com.github.build.SourceSet;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.jetbrains.kotlin.cli.common.ExitCode;
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compiles Kotlin sources of a source set.
 *
 * <p>Java sources are exposed to the Kotlin compiler as source roots so Kotlin code can resolve
 * Java symbols. Java sources themselves are compiled separately by {@link JavaCompileService}.
 *
 * @author noavarice
 * @since 1.0.0
 */
public final class KotlinCompileService {

    private static final Logger log = LoggerFactory.getLogger(KotlinCompileService.class);

    private static final String KOTLIN_JVM_TARGET = "17";

    private static final String KOTLIN_LANGUAGE_VERSION = "2.2";

    /**
     * Compiles Kotlin sources of a source set.
     *
     * @param workdir   Build working directory (absolute)
     * @param sourceSet Source set to compile
     * @param classesDir Directory to put output classes into
     * @param classpath Compilation classpath
     * @return True if Kotlin compilation succeeds (or there is nothing to compile), false otherwise
     */
    public boolean compile(
            final Path workdir, final SourceSet sourceSet, final Path classesDir, final Set<Path> classpath) {
        Objects.requireNonNull(workdir);
        Objects.requireNonNull(sourceSet);
        Objects.requireNonNull(classesDir);
        Objects.requireNonNull(classpath);

        final Project project = sourceSet.project();

        final List<Path> kotlinSources = new ArrayList<>();
        collectSources(workdir, project, sourceSet.kotlinSourceRoots(), kotlinSources);
        if (kotlinSources.isEmpty()) {
            log.debug("No Kotlin sources found, do nothing");
            return true;
        }

        final List<Path> javaSourceRoots = new ArrayList<>();
        for (final Path relativeDir : sourceSet.javaSourceRoots().directories()) {
            final Path sourceDir = workdir.resolve(project.path()).resolve(relativeDir);
            if (Files.isDirectory(sourceDir)) {
                javaSourceRoots.add(sourceDir);
            }
        }

        try {
            Files.createDirectories(classesDir);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        log.debug("Compiling Kotlin ({} files)", kotlinSources.size());
        return compileKotlin(classesDir, classpath, javaSourceRoots, kotlinSources);
    }

    private static boolean compileKotlin(
            final Path classesDir,
            final Set<Path> classpath,
            final List<Path> javaSourceRoots,
            final List<Path> kotlinSources) {
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
            log.debug("Kotlin compiler exited with code {}", exitCode);
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
            final Path workdir, final Project project, final SourceRoots sourceRoots, final List<Path> sink) {
        final PathMatcher matcher = sourceRoots.matcher();
        for (final Path relativeDir : sourceRoots.directories()) {
            final Path sourceDir = workdir.resolve(project.path()).resolve(relativeDir);
            if (!Files.isDirectory(sourceDir)) {
                continue;
            }

            try (final Stream<Path> files = Files.walk(sourceDir)) {
                files.filter(Files::isRegularFile)
                        .filter(file -> matcher.matches(sourceDir.relativize(file)))
                        .forEach(sink::add);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
