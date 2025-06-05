package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * "Hello, world!" that depends on SLF4J API.
 */
public class Slf4jExample {

  private static final Logger log = LoggerFactory.getLogger(Slf4jExample.class);

  public static void main(final String[] args) {
    log.info("Hello, world");
  }
}
