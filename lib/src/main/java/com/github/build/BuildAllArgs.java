package com.github.build;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author noavarice
 */
public record BuildAllArgs(
    ExecutorService executorService,
    Duration timeout
) {

  public static Builder builder() {
    return new Builder();
  }

  public BuildAllArgs {
    Objects.requireNonNull(executorService);
    checkTimeout(timeout);
  }

  public static final class Builder {

    private Builder() {
    }

    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    private Duration timeout = Duration.ofMinutes(60);

    public Builder executorService(final ExecutorService executorService) {
      this.executorService = Objects.requireNonNull(executorService);
      return this;
    }

    public Builder timeout(final Duration timeout) {
      checkTimeout(timeout);
      this.timeout = timeout;
      return this;
    }

    public BuildAllArgs build() {
      return new BuildAllArgs(executorService, timeout);
    }
  }

  private static void checkTimeout(final Duration timeout) {
    Objects.requireNonNull(timeout);
    if (!timeout.isPositive()) {
      throw new IllegalArgumentException("Build timeout must be positive");
    }
  }
}
