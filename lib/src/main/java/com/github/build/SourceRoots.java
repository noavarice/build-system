package com.github.build;

import static java.util.stream.Collectors.toUnmodifiableSet;

import com.github.build.util.PathUtils;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Objects;
import java.util.Set;

/**
 * A set of source root directories together with a native Java glob selecting the source files
 * inside them.
 *
 * <p>{@code directories} are relative to the project directory. {@code glob} is a glob understood
 * by {@link java.nio.file.FileSystem#getPathMatcher(String)} and is matched against a candidate
 * file path relative to its source root directory.
 *
 * @param directories Source root directories, relative to the project directory
 * @param glob        Native Java glob selecting source files within the directories
 * @author noavarice
 * @since 1.0.0
 */
public record SourceRoots(Set<Path> directories, String glob) {

    public static final SourceRoots EMPTY = new SourceRoots(Set.of(), "**/*");

    private static final String ANY_DIRECTORY_PREFIX = "**/";

    public SourceRoots {
        directories = directories.stream()
                .peek(Objects::requireNonNull)
                .peek(PathUtils::checkRelative)
                .map(Path::normalize)
                .collect(toUnmodifiableSet());

        glob = Objects.requireNonNull(glob).strip();
        if (glob.isBlank()) {
            throw new IllegalArgumentException("Source roots glob must not be blank");
        }
    }

    public static SourceRoots javaSources(final Set<Path> directories) {
        return new SourceRoots(directories, "**/*.java");
    }

    public static SourceRoots kotlinSources(final Set<Path> directories) {
        return new SourceRoots(directories, "**/*.kt");
    }

    public static SourceRoots aspectjSources(final Set<Path> directories) {
        return new SourceRoots(directories, "**/*.aj");
    }

    public boolean isEmpty() {
        return directories.isEmpty();
    }

    /**
     * @return Matcher for paths relative to a source root directory. The leading {@code **} is
     *     optional, so both {@code **}/{@code *.java} and {@code *.java} match a file located
     *     directly in a source root.
     */
    public PathMatcher matcher() {
        final var fileSystem = FileSystems.getDefault();
        final PathMatcher matcher = fileSystem.getPathMatcher("glob:" + glob);
        if (!glob.startsWith(ANY_DIRECTORY_PREFIX)) {
            return matcher;
        }

        final PathMatcher rootMatcher =
                fileSystem.getPathMatcher("glob:" + glob.substring(ANY_DIRECTORY_PREFIX.length()));
        return path -> matcher.matches(path) || rootMatcher.matches(path);
    }
}
