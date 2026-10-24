package com.github.build;

import com.github.build.test.TestResults;
import com.github.build.util.DurationUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Accumulates per-source-set build statistics and prints a summary at the end of a build.
 *
 * <p>A single report instance may be shared by {@link BuildService} and
 * {@link com.github.build.test.TestService}, so the summary covers both compilation and test
 * execution:
 *
 * <pre>{@code
 * final var report = new BuildReport();
 * final var buildService = new BuildService(report, ...);
 * final var testService = new TestService(dependencyService, report);
 * // ... build ...
 * report.logSummary(success);
 * }</pre>
 *
 * @author noavarice
 * @since 1.0.0
 */
public final class BuildReport {

    private static final Logger log = LoggerFactory.getLogger(BuildReport.class);

    private final Instant startedAt = Instant.now();

    private final Map<String, SourceSetStats> stats = new LinkedHashMap<>();

    /**
     * Records a finished compilation of a source set.
     *
     * @param project    Compiled project
     * @param sourceSetId Compiled source set
     * @param language   Compiled language
     * @param duration   Compilation duration
     */
    public synchronized void compilationFinished(
            final Project project, final SourceSet.Id sourceSetId, final Language language, final Duration duration) {
        final SourceSetStats sourceSetStats = statsFor(project, sourceSetId);
        sourceSetStats.compilation = sourceSetStats.compilation.plus(duration);
    }

    /**
     * Records finished test execution for a source set.
     *
     * @param project     Tested project
     * @param sourceSetId Tested source set
     * @param duration    Test execution duration
     * @param results     Test results
     */
    public synchronized void testsFinished(
            final Project project, final SourceSet.Id sourceSetId, final Duration duration, final TestResults results) {
        final SourceSetStats sourceSetStats = statsFor(project, sourceSetId);
        sourceSetStats.tests = duration;
        sourceSetStats.testResults = results;
    }

    /**
     * Logs the build summary (status, total, per-source-set timings and test statistics).
     *
     * @param success Whether the whole build succeeded
     */
    public synchronized void logSummary(final boolean success) {
        final Duration total = Duration.between(startedAt, Instant.now());
        final var summary = new StringBuilder()
                .append("Build summary: ")
                .append(success ? "SUCCESS" : "FAILED")
                .append(" (total ")
                .append(DurationUtils.format(total))
                .append(')');

        for (final Map.Entry<String, SourceSetStats> entry : stats.entrySet()) {
            final SourceSetStats sourceSetStats = entry.getValue();
            summary.append(System.lineSeparator())
                    .append("  ")
                    .append(entry.getKey())
                    .append(": compilation ")
                    .append(DurationUtils.format(sourceSetStats.compilation));

            final TestResults results = sourceSetStats.testResults;
            final Duration tests = sourceSetStats.tests;
            if (results != null && tests != null) {
                summary.append(", tests ")
                        .append(DurationUtils.format(tests))
                        .append(" (")
                        .append(results.testsSucceededCount())
                        .append(" passed, ")
                        .append(results.testsFailedCount())
                        .append(" failed, ")
                        .append(results.testsSkippedCount())
                        .append(" skipped)");
            }
        }

        log.info(summary.toString());
    }

    private SourceSetStats statsFor(final Project project, final SourceSet.Id sourceSetId) {
        final String key = project.artifactId() + ':' + sourceSetId.value();
        return stats.computeIfAbsent(key, ignored -> new SourceSetStats());
    }

    private static final class SourceSetStats {

        private Duration compilation = Duration.ZERO;

        private @Nullable Duration tests;

        private @Nullable TestResults testResults;
    }
}
