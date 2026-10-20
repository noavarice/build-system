/**
 * @author noavarice
 * @since 1.0.0
 */
module com.github.build {
  requires org.jspecify;
  requires org.slf4j;
  requires java.compiler;
  requires maven.artifact;
  requires jakarta.xml.bind;
  requires java.net.http;
  requires maven.model;
  requires plexus.utils;
  requires org.apache.maven.resolver;
  requires org.apache.maven.resolver.util;
  requires org.aspectj.tools;
  requires kotlin.compiler.embeddable;

  // Logback is optional: the library only provides a default logging configuration when Logback
  // happens to be on the classpath.
  requires static ch.qos.logback.classic;
  requires static ch.qos.logback.core;

  provides ch.qos.logback.classic.spi.Configurator
      with com.github.build.logging.DefaultLoggingConfigurator;
}
