# BuildSpringSecurity self-hosted build runtime.
#
# Requires a full JDK 21: BuildSpringSecurity reuses the current JVM for the test
# processes it spawns, and their Mockito inline mock maker / JaCoCo agents only work on
# JDK 21 (see BLOCKED_MODULES.md for the JDK 25 failure mode).
#
# The artifacts come from the module `target/` dirs, which the `maven` compose service
# produces (Maven layout, NOT the self-hosted `{module}/build/` layout). The
# `copy-dependencies` executions in the poms fill `target/dependencies/`.
FROM docker.io/library/eclipse-temurin:21

# The `web` module's `buildJsModule` runs `npm install` / `npm run assemble` in
# spring-security/javascript, which requires Node >= 20 (see its package.json engines).
RUN apt-get update \
  && apt-get install -y --no-install-recommends curl ca-certificates gnupg \
  && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
  && apt-get install -y --no-install-recommends nodejs \
  && rm -rf /var/lib/apt/lists/* \
  && node --version && npm --version

RUN mkdir /opt/build-system

COPY ./build-spring-security/target/build-spring-security-0.1.0.jar /opt/build-system/
COPY ./build-spring-security/target/dependencies/ /opt/build-system/

COPY ./lib/target/lib-0.1.0.jar /opt/build-system/

COPY ./junit-integration/target/junit-integration-0.1.0.jar /opt/build-system/

WORKDIR /app

# junit-integration + lib are handed to in-process tests via -DbuildRuntimePath, matching
# the `build Spring Security` run configuration. `maven.repo.local` is the self-hosted aether
# local repository; the compose service mounts a shared volume there so resolved artifacts
# persist across runs (see compose-local.yaml).
ENTRYPOINT ["java", \
  "-DbuildRuntimePath=/opt/build-system/junit-integration-0.1.0.jar,/opt/build-system/lib-0.1.0.jar", \
  "-Dmaven.repo.local=/m2/repository", \
  "-classpath", "/opt/build-system/*", \
  "com.github.build.BuildSpringSecurity", "/app/spring-security"]