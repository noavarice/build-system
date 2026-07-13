package com.github.build.test;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * @author noavarice
 */
public record TestFailure(
    String testId,
    @Nullable String displayName,
    @Nullable String exceptionType,
    @Nullable String message,
    @Nullable List<StackTraceElement> stackTrace
) {}
