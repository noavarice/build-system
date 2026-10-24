package com.github.build.util;

import java.time.Duration;

/**
 * Formats durations for human-readable log messages and build summaries.
 *
 * @author noavarice
 * @since 1.0.0
 */
public final class DurationUtils {

    private DurationUtils() {}

    /**
     * @param duration Duration to format
     * @return Human-readable representation, e.g. {@code 812 ms}, {@code 3.4 s}, {@code 1 m 3 s}
     */
    public static String format(final Duration duration) {
        final long millis = duration.toMillis();
        if (millis < 1000) {
            return millis + " ms";
        }

        final long totalSeconds = millis / 1000;
        if (totalSeconds < 60) {
            return "%.1f s".formatted(millis / 1000.0);
        }

        final long minutes = totalSeconds / 60;
        final long seconds = totalSeconds % 60;
        if (minutes < 60) {
            return "%d m %d s".formatted(minutes, seconds);
        }

        return "%d h %d m".formatted(minutes / 60, minutes % 60);
    }
}
