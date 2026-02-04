package com.github.build;

/**
 * @author noavarice
 */
public interface BuildEventPublisher {

  void publish(BuildEvent event);
}
