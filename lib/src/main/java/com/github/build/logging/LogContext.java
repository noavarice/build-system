package com.github.build.logging;

import com.github.build.Project;
import com.github.build.SourceSet;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

/**
 * Puts build context values ({@code project}, {@code ss}, {@code pid}) into the SLF4J
 * {@link MDC} for the duration of a scope and restores the previous values on close.
 *
 * <p>The values are rendered by the logging pattern, so log messages never hardcode project or
 * source set identifiers. Scopes can be nested and used with try-with-resources:
 *
 * <pre>{@code
 * try (final var ignored = LogContext.sourceSet(project, sourceSet.id())) {
 *   log.info("Compilation started");
 * }
 * }</pre>
 *
 * @author noavarice
 * @since 1.0.0
 */
public final class LogContext implements AutoCloseable {

  /**
   * Project artifact ID MDC key.
   */
  public static final String PROJECT = "project";

  /**
   * Source set ID MDC key.
   */
  public static final String SOURCE_SET = "ss";

  /**
   * Process ID MDC key.
   */
  public static final String PROCESS_ID = "pid";

  private final Map<String, @Nullable String> previousValues;

  private LogContext(final Map<String, String> values) {
    this.previousValues = new LinkedHashMap<>(values.size());
    values.forEach((key, value) -> {
      previousValues.put(key, MDC.get(key));
      MDC.put(key, value);
    });
  }

  public static LogContext project(final Project project) {
    return new LogContext(Map.of(PROJECT, project.artifactId()));
  }

  public static LogContext sourceSet(final Project project, final SourceSet.Id sourceSetId) {
    return new LogContext(Map.of(
        PROJECT, project.artifactId(),
        SOURCE_SET, sourceSetId.value()
    ));
  }

  public static LogContext pid(final long pid) {
    return new LogContext(Map.of(PROCESS_ID, Long.toString(pid)));
  }

  @Override
  public void close() {
    previousValues.forEach((key, value) -> {
      if (value == null) {
        MDC.remove(key);
      } else {
        MDC.put(key, value);
      }
    });
  }
}
