package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * "Hello, world!" that depends on SLF4J as plain JAR file.
 */
public class Main {

  private static final Logger log = LoggerFactory.getLogger(Main.class);

  public static void main(final String[] args) {
    log.info("Hello, world");
  }
}
