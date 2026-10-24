package com.github.build;

import static java.util.stream.Collectors.toUnmodifiableSet;

import com.github.build.deps.DependencyConstraints;
import com.github.build.util.PathUtils;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Arguments for declaring test source set in a project.
 *
 * @author noavarice
 * @since 1.0.0
 */
public record TestSourceSetArgs(
        String id,
        SourceRoots javaSourceRoots,
        SourceRoots kotlinSourceRoots,
        SourceRoots aspectjSourceRoots,
        Set<Path> resourceDirectories,
        List<TestSourceSetDependency> compileDependencies,
        List<TestSourceSetDependency> runtimeDependencies,
        DependencyConstraints dependencyConstraints) {

    public TestSourceSetArgs(
            final String id,
            final SourceRoots javaSourceRoots,
            final Set<Path> resourceDirectories,
            final List<TestSourceSetDependency> compileDependencies,
            final List<TestSourceSetDependency> runtimeDependencies,
            final DependencyConstraints dependencyConstraints) {
        this(
                id,
                javaSourceRoots,
                SourceRoots.EMPTY,
                SourceRoots.EMPTY,
                resourceDirectories,
                compileDependencies,
                runtimeDependencies,
                dependencyConstraints);
    }

    public static TestSourceSetArgs withTestDefaults() {
        return new TestSourceSetArgs(
                SourceSet.Id.TEST.toString(),
                SourceRoots.javaSources(Set.of(Path.of("src", "test", "java"))),
                Set.of(Path.of("src", "test", "resources")),
                List.of(),
                List.of(),
                DependencyConstraints.EMPTY);
    }

    public TestSourceSetArgs {
        Objects.requireNonNull(id);
        Objects.requireNonNull(javaSourceRoots);
        Objects.requireNonNull(kotlinSourceRoots);
        Objects.requireNonNull(aspectjSourceRoots);
        if (javaSourceRoots.isEmpty() && kotlinSourceRoots.isEmpty() && aspectjSourceRoots.isEmpty()) {
            throw new IllegalArgumentException("Source set must have at least one source root directory");
        }

        resourceDirectories = resourceDirectories.stream()
                .peek(Objects::requireNonNull)
                .peek(PathUtils::checkRelative)
                .map(Path::normalize)
                .collect(toUnmodifiableSet());

        compileDependencies = List.copyOf(compileDependencies);
        runtimeDependencies = List.copyOf(runtimeDependencies);
        Objects.requireNonNull(dependencyConstraints);
    }
}
