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
    requires org.aspectj.tools;
    requires kotlin.compiler.embeddable;

    // Logback is optional: the library only provides a default logging configuration when Logback
    // happens to be on the classpath.
    requires static ch.qos.logback.classic;
    requires static ch.qos.logback.core;

    exports com.github.build;
    exports com.github.build.deps;
    exports com.github.build.util;
    exports org.apache.maven.metadata._1_1;

    provides ch.qos.logback.classic.spi.Configurator with
            com.github.build.logging.DefaultLoggingConfigurator;
}
