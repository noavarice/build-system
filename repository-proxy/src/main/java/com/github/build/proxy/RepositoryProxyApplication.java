package com.github.build.proxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Maven repository proxy application.
 *
 * @author noavarice
 * @since 1.0.0
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class RepositoryProxyApplication {

    public static void main(final String[] args) {
        SpringApplication.run(RepositoryProxyApplication.class, args);
    }
}
