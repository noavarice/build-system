package com.github.build;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * @author noavarice
 */
public final class BuildEventBus implements BuildEventPublisher {

  private final ExecutorService executorService;

  private final List<Consumer<BuildEvent>> handlers = new ArrayList<>();

  public BuildEventBus(final ExecutorService executorService) {
    this.executorService = Objects.requireNonNull(executorService);
  }

  @Override
  public void publish(final BuildEvent event) {
    Objects.requireNonNull(event);
    handlers.forEach(handler -> executorService.submit(() -> handler.accept(event)));
  }

  public void subscribe(final Consumer<BuildEvent> handler) {
    Objects.requireNonNull(handler);
    handlers.addLast(handler);
  }
}
