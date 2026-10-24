package com.github.build;

import com.github.build.test.TestResults;
import com.github.build.util.DurationUtils;
import com.github.build.util.PathUtils;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-build context that ties build statistics and per-project outcomes to a concrete build and its
 * {@linkplain #workdir() working directory}.
 *
 * <p>A fresh instance is created for every build (in a build main), so its scope is a single build,
 * not a {@link BuildService}/{@link com.github.build.test.TestService} instance. Services are created
 * once and are shared across builds, while the build-specific state accumulates here. In addition to
 * per-source-set compilation/test timings it tracks what happened to each reactor project ({@link
 * ProjectStatus}), which lets the summary tell apart projects that were built successfully, projects
 * that failed and projects that were never built because one of their dependencies failed.
 *
 * <pre>{@code
 * final var context = new BuildContext(workdir);
 * final var buildService = new BuildService(new JavaCompileService(), dependencyService, new JarService());
 * final var testService = new TestService(dependencyService);
 * context.expectProjects(projects);
 * // ... build, recording buildService.projectBuilt/Failed and compiling/testing through the context ...
 * context.logSummary();
 * }</pre>
 *
 * @author noavarice
 * @since 1.0.0
 */
public final class BuildContext {

    private static final Logger log = LoggerFactory.getLogger(BuildContext.class);

    private final Path workdir;

    private final Instant startedAt = Instant.now();

    private final Map<String, SourceSetStats> stats = new LinkedHashMap<>();

    private final Map<Project, ProjectOutcome> outcomes = new LinkedHashMap<>();

    public BuildContext(final Path workdir) {
        this.workdir = Objects.requireNonNull(workdir);
        PathUtils.checkAbsolute(workdir);
    }

    /**
     * @return The working directory this build context accumulates statistics for
     */
    public Path workdir() {
        return workdir;
    }

    /**
     * Registers the reactor projects of the build so the summary can report any of them that never
     * get built (e.g. because a dependency failed) as {@link ProjectStatus#NOT_BUILT}.
     *
     * @param projects All projects participating in the build
     */
    public synchronized void expectProjects(final Collection<Project> projects) {
        for (final Project project : projects) {
            outcomes.putIfAbsent(project, new ProjectOutcome(ProjectStatus.NOT_BUILT, null));
        }
    }

    /**
     * Records that the given project was built successfully.
     *
     * @param project Built project
     */
    public synchronized void projectBuilt(final Project project) {
        outcomes.put(project, new ProjectOutcome(ProjectStatus.BUILT, null));
    }

    /**
     * Records that the given project failed to build.
     *
     * @param project Failed project
     */
    public synchronized void projectFailed(final Project project) {
        projectFailed(project, null);
    }

    /**
     * Records that the given project failed to build with a specific reason.
     *
     * @param project Failed project
     * @param reason   Failure reason, may be {@code null}
     */
    public synchronized void projectFailed(final Project project, final @Nullable String reason) {
        outcomes.put(project, new ProjectOutcome(ProjectStatus.FAILED, reason));
    }

    /**
     * Records that the given project was not built because one of its dependencies failed.
     *
     * @param project Not-built project
     * @param reason   Specific skip reason, may be {@code null}
     */
    public synchronized void projectNotBuilt(final Project project, final @Nullable String reason) {
        outcomes.put(project, new ProjectOutcome(ProjectStatus.NOT_BUILT, reason));
    }

    /**
     * Records a finished compilation of a source set.
     *
     * @param project     Compiled project
     * @param sourceSetId Compiled source set
     * @param language    Compiled language
     * @param duration    Compilation duration
     */
    public synchronized void compilationFinished(
            final Project project, final SourceSet.Id sourceSetId, final Language language, final Duration duration) {
        final SourceSetStats sourceSetStats = statsFor(project, sourceSetId);
        sourceSetStats.compilation = sourceSetStats.compilation.plus(duration);
    }

    /**
     * Records a failed compilation of a source set.
     *
     * @param project     Compiled project
     * @param sourceSetId Compiled source set
     */
    public synchronized void compilationFailed(final Project project, final SourceSet.Id sourceSetId) {
        final SourceSetStats sourceSetStats = statsFor(project, sourceSetId);
        sourceSetStats.compilationFailed = true;
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
     * Logs the build summary: overall status, per-project outcome (built / failed / not built) and
     * per-source-set timings and test statistics.
     */
    public synchronized void logSummary() {
        final Duration total = Duration.between(startedAt, Instant.now());
        final boolean success = outcomes.values().stream().noneMatch(outcome -> outcome.status == ProjectStatus.FAILED);
        final var summary = new StringBuilder()
                .append("Build summary: ")
                .append(success ? "SUCCESS" : "FAILED")
                .append(" (total ")
                .append(DurationUtils.format(total))
                .append(')');

        if (!outcomes.isEmpty()) {
            appendProjectOutcomes(summary, ProjectStatus.BUILT, "Built");
            appendProjectOutcomes(summary, ProjectStatus.FAILED, "Failed");
            appendProjectOutcomes(summary, ProjectStatus.NOT_BUILT, "Not built");
        }

        for (final Map.Entry<String, SourceSetStats> entry : stats.entrySet()) {
            final SourceSetStats sourceSetStats = entry.getValue();
            summary.append(System.lineSeparator())
                    .append("  ")
                    .append(entry.getKey())
                    .append(": compilation ")
                    .append(
                            sourceSetStats.compilationFailed
                                    ? "FAILED"
                                    : DurationUtils.format(sourceSetStats.compilation));

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

    private void appendProjectOutcomes(final StringBuilder summary, final ProjectStatus status, final String label) {
        final List<String> projects = new ArrayList<>();
        outcomes.forEach((project, outcome) -> {
            if (outcome.status == status) {
                String description = project.artifactId();
                if (status == ProjectStatus.NOT_BUILT) {
                    description = outcome.reason == null
                            ? description + " (not built because a dependency failed)"
                            : description + " (" + outcome.reason + ')';
                } else if (status == ProjectStatus.FAILED && outcome.reason != null) {
                    description = description + " (" + outcome.reason + ')';
                }
                projects.add(description);
            }
        });

        if (projects.isEmpty()) {
            return;
        }

        summary.append(System.lineSeparator())
                .append("  ")
                .append(label)
                .append(" (")
                .append(projects.size())
                .append("):");
        for (final String project : projects) {
            summary.append(System.lineSeparator()).append("    - ").append(project);
        }
    }

    private SourceSetStats statsFor(final Project project, final SourceSet.Id sourceSetId) {
        final String key = project.artifactId() + ':' + sourceSetId.value();
        return stats.computeIfAbsent(key, ignored -> new SourceSetStats());
    }

    /** Outcome of a single project within a build. */
    public enum ProjectStatus {
        BUILT,
        FAILED,
        NOT_BUILT
    }

    private static final class ProjectOutcome {

        private final ProjectStatus status;

        private final @Nullable String reason;

        private ProjectOutcome(final ProjectStatus status, final @Nullable String reason) {
            this.status = status;
            this.reason = reason;
        }
    }

    private static final class SourceSetStats {

        private Duration compilation = Duration.ZERO;

        private boolean compilationFailed;

        private @Nullable Duration tests;

        private @Nullable TestResults testResults;
    }
}
