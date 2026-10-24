package com.github.build;

/**
 * Base class for exceptions thrown by the build library.
 *
 * @author noavarice
 * @since 1.0.0
 */
public class BuildException extends RuntimeException {

    public BuildException(final String message) {
        super(message);
    }

    public BuildException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
