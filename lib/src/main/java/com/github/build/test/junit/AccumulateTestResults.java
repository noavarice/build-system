package com.github.build.test.junit;

import com.github.build.Project;
import com.github.build.SourceSet;
import com.github.build.logging.LogContext;
import com.github.build.test.TestFailure;
import com.github.build.test.TestResults;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author noavarice
 * @since 1.0.0
 */
public final class AccumulateTestResults implements Consumer<JUnitEvent> {

    private static final Logger log = LoggerFactory.getLogger(AccumulateTestResults.class);

    private final Project project;

    private final SourceSet.Id sourceSetId;

    private final AtomicInteger succeeded = new AtomicInteger(0);

    private final AtomicInteger skipped = new AtomicInteger(0);

    private final AtomicInteger failed = new AtomicInteger(0);

    private final List<TestFailure> failures = Collections.synchronizedList(new ArrayList<>());

    public AccumulateTestResults(final Project project, final SourceSet.Id sourceSetId) {
        this.project = Objects.requireNonNull(project);
        this.sourceSetId = Objects.requireNonNull(sourceSetId);
    }

    @Override
    public void accept(final JUnitEvent event) {
        // events are processed on the socket listener thread, so the context must be set explicitly
        try (final var ignored = LogContext.sourceSet(project, sourceSetId)) {
            switch (event) {
                case JUnitEvent.ContainerStarted containerStarted ->
                    log.debug("JUnit test container started: {}", containerStarted.containerId());
                case JUnitEvent.ContainerSkipped containerSkipped -> {
                    log.debug("JUnit test container skipped: {}", containerSkipped.containerId());
                    skipped.incrementAndGet();
                }
                case JUnitEvent.ContainerFinished containerFinished ->
                    log.debug("JUnit test container finished: {}", containerFinished.containerId());
                case JUnitEvent.TestFinished testFinished -> {
                    switch (testFinished.status()) {
                        case SUCCESSFUL -> {
                            log.trace("JUnit test succeeded: {}", testFinished.testId());
                            succeeded.incrementAndGet();
                        }
                        case ABORTED -> log.warn("JUnit test aborted: {}", testFinished.testId());
                        case FAILED -> {
                            log.debug("JUnit test failed: {}", testFinished.testId());
                            failed.incrementAndGet();
                        }
                    }
                }
                case JUnitEvent.TestSkipped testSkipped -> {
                    log.debug("JUnit test skipped: {}", testSkipped.testId());
                    skipped.incrementAndGet();
                }
                case JUnitEvent.TestFailed tf -> {
                    failed.incrementAndGet();
                    failures.add(new TestFailure(
                            tf.testId(), tf.displayName(), tf.exceptionType(), tf.message(), tf.stackTrace()));
                }
            }
        }
    }

    public TestResults toTestResults() {
        return new TestResults(succeeded.get(), failed.get(), skipped.get(), List.copyOf(failures));
    }
}
