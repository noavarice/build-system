# Repository proxy service.
#
# Stage 1 builds the executable jar and extracts the Spring Boot layers (see the "Dockerfiles"
# section of the Spring Boot reference) so the runtime image carries the layers instead of a fat
# jar. Stage 2 runs as a non-root user; the default Maven local repository lives in that user's
# home so a mounted volume inherits its ownership.

FROM docker.io/library/maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN mvn -q -pl repository-proxy -am -DskipTests package

WORKDIR /builder
RUN cp /src/repository-proxy/target/repository-proxy-0.1.0-exec.jar application.jar \
  && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM docker.io/library/eclipse-temurin:21-jre

RUN groupadd --system --gid 1001 repositoryproxy \
  && useradd --system --uid 1001 --gid repositoryproxy \
       --home-dir /home/repositoryproxy --create-home repositoryproxy

WORKDIR /app

# Each layer is copied separately so rebuilds only invalidate the layers that changed.
COPY --from=build --chown=repositoryproxy:repositoryproxy /builder/extracted/dependencies/ ./
COPY --from=build --chown=repositoryproxy:repositoryproxy /builder/extracted/spring-boot-loader/ ./
COPY --from=build --chown=repositoryproxy:repositoryproxy /builder/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=repositoryproxy:repositoryproxy /builder/extracted/application/ ./

# Pre-create the default local repository so a mounted volume inherits the non-root ownership.
RUN mkdir -p /home/repositoryproxy/.m2/repository \
  && chown -R repositoryproxy:repositoryproxy /home/repositoryproxy/.m2

# Short, dependency-free healthcheck shared by the image and by compose. The port comes from
# SERVER_PORT (Spring Boot binds it to server.port) and defaults to 8080.
RUN printf '%s\n' \
      '#!/bin/sh' \
      'exec java -cp /app/application.jar com.github.build.proxy.HealthCheck "http://localhost:${SERVER_PORT:-8080}/health"' \
      > /usr/local/bin/healthcheck \
  && chmod +x /usr/local/bin/healthcheck

# The app starts in about a second, so the probe uses a short start period and interval. Note that
# `podman build` keeps the OCI image format by default, which ignores HEALTHCHECK; compose therefore
# declares the same check explicitly (see compose-local.yaml) and this is used by `docker run` and
# docker-format builds.
HEALTHCHECK --interval=5s --timeout=3s --start-period=5s --retries=3 CMD /usr/local/bin/healthcheck

USER repositoryproxy

ENTRYPOINT ["java", "-jar", "application.jar"]
