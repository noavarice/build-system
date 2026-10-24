package com.github.build.compile;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * @author noavarice
 */
public final class JavaCompilerOptions {

    public static final JavaCompilerOptions EMPTY = new JavaCompilerOptions(null, false);

    public static Builder builder() {
        return new Builder();
    }

    @Nullable
    private final String release;

    private final boolean parameters;

    private JavaCompilerOptions(@Nullable final String release, final boolean parameters) {
        this.release = release;
        this.parameters = parameters;
    }

    public List<String> toList() {
        final var result = new ArrayList<String>();

        if (release != null) {
            result.add("--release");
            result.add(release);
        }

        if (parameters) {
            result.add("-parameters");
        }

        return result;
    }

    public static final class Builder {

        @Nullable
        private String release = null;

        private boolean parameters = false;

        private Builder() {}

        public Builder release(final String release) {
            if (release == null) {
                this.release = null;
            } else {
                if (release.isBlank()) {
                    throw new IllegalArgumentException();
                }

                // TODO: add validation
                this.release = release.strip();
            }

            return this;
        }

        public Builder parameters(final boolean parameters) {
            this.parameters = parameters;
            return this;
        }

        public JavaCompilerOptions build() {
            return new JavaCompilerOptions(release, parameters);
        }
    }
}
