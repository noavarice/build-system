package com.github.build.test.junit;

import static java.util.stream.Collectors.toUnmodifiableSet;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/**
 * @author noavarice
 * @since 1.0.0
 */
public record JUnitTestTaskArgs(Set<Path> testClassesDirs) {

    public JUnitTestTaskArgs {
        testClassesDirs = Objects.requireNonNull(testClassesDirs).stream()
                .map(Objects::requireNonNull)
                .map(Path::normalize)
                .map(Path::toAbsolutePath)
                .collect(toUnmodifiableSet());
    }
}
