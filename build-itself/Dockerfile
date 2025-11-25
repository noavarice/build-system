FROM eclipse-temurin:21

RUN mkdir /opt/build-system

COPY ./lib/target/lib-0.1.0.jar /opt/build-system/
COPY ./lib/target/dependencies/ /opt/build-system/

COPY ./build-itself/target/build-itself-0.1.0.jar /opt/build-system/
COPY ./build-itself/target/dependencies/ /opt/build-system/

WORKDIR /app

ENTRYPOINT ["java", "-classpath", "/opt/build-system/*", "com.github.build.BuildItself", "/app"]
