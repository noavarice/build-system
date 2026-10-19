FROM docker.io/library/eclipse-temurin:21

RUN mkdir /opt/build-system

COPY ./lib/target/lib-0.1.0.jar /opt/build-system/
COPY ./lib/target/dependencies/ /opt/build-system/

COPY ./build-itself/target/build-itself-0.1.0.jar /opt/build-system/
COPY ./build-itself/target/dependencies/ /opt/build-system/

WORKDIR /app

# `maven.repo.local` is the self-hosted aether local repository; the compose service mounts a
# shared volume there so resolved artifacts persist across runs and container rebuilds
# (see compose-local.yaml).
ENTRYPOINT ["java", "-Dmaven.repo.local=/m2/repository", "-classpath", "/opt/build-system/*", \
  "com.github.build.BuildItself", "/app"]
