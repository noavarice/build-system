package com.github.build.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.classic.spi.ConfiguratorRank;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.spi.ContextAwareBase;
import ch.qos.logback.core.util.Loader;

/**
 * Default Logback configuration used by the build library when the consuming application does not
 * provide its own configuration.
 *
 * <p>Logback discovers this class through the {@link java.util.ServiceLoader} (see
 * {@code META-INF/services/ch.qos.logback.classic.spi.Configurator} and the module descriptor) and
 * invokes it before its own configurators. The configuration is deliberately simple: an
 * {@code INFO} console appender that renders the level (highlighted), the MDC context (project /
 * source set, when present) and the message, without date, thread or logger name.
 *
 * <p>The default is easy to override. If any of the following is present, this configurator steps
 * aside ({@link ExecutionStatus#INVOKE_NEXT_IF_ANY}) and lets Logback use it instead:
 * <ul>
 *   <li>the {@code logback.configurationFile} system property;</li>
 *   <li>a {@code logback-test.xml} on the classpath;</li>
 *   <li>a {@code logback.xml} on the classpath.</li>
 * </ul>
 * A consumer can also register its own {@link Configurator} with a higher
 * {@link ConfiguratorRank}, or simply call {@code setLevel} / {@code addAppender} on the logger
 * context after startup.
 *
 * @author noavarice
 * @since 1.0.0
 */
@ConfiguratorRank(ConfiguratorRank.CUSTOM_LOW_PRIORITY)
public final class DefaultLoggingConfigurator extends ContextAwareBase implements Configurator {

  private static final String PATTERN =
      "%highlight(%-5level) %X{project:-} %X{ss:-} - %msg%n";

  @Override
  public ExecutionStatus configure(final LoggerContext context) {
    if (isOverridden()) {
      return ExecutionStatus.INVOKE_NEXT_IF_ANY;
    }

    final var encoder = new PatternLayoutEncoder();
    encoder.setContext(context);
    encoder.setPattern(PATTERN);
    encoder.start();

    final var appender = new ConsoleAppender<ILoggingEvent>();
    appender.setContext(context);
    appender.setEncoder(encoder);
    appender.start();

    final Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
    root.detachAndStopAllAppenders();
    root.setLevel(Level.INFO);
    root.addAppender(appender);

    return ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY;
  }

  private static boolean isOverridden() {
    final String configFile = System.getProperty("logback.configurationFile");
    if (configFile != null && !configFile.isBlank()) {
      return true;
    }

    final ClassLoader classLoader = classLoader();
    return Loader.getResource("logback-test.xml", classLoader) != null
        || Loader.getResource("logback.xml", classLoader) != null;
  }

  private static ClassLoader classLoader() {
    final ClassLoader threadContextClassLoader =
        Thread.currentThread().getContextClassLoader();
    return threadContextClassLoader != null
        ? threadContextClassLoader
        : DefaultLoggingConfigurator.class.getClassLoader();
  }
}
