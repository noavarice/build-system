package com.github.build;

import java.util.Locale;

/**
 * Source languages the build system is able to compile.
 *
 * @author noavarice
 * @since 1.0.0
 */
public enum Language {

  JAVA,
  KOTLIN,
  ASPECTJ;

  /**
   * @return Name of the directory dedicated to this language in a source set's classes output
   */
  public String directory() {
    return name().toLowerCase(Locale.ROOT);
  }
}
