package com.github.build;

/**
 * Thrown when compilation of a source set fails during a build.
 *
 * @author noavarice
 * @since 1.0.0
 */
public final class CompilationException extends BuildException {

    public CompilationException(final String message) {
        super(message);
    }

    public CompilationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
