# Build with the repository's pinned Maven Wrapper and Java 17.
FROM eclipse-temurin:17-jdk-jammy AS build
# Keep the wrapper on its checksum-pinned ZIP distribution.
RUN apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Windows checkouts may give the wrapper CRLF line endings.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
COPY src/main/ src/main/
# Tests run separately; no test fixtures or credentials enter the build context.
RUN ./mvnw -B -ntp package -Dmaven.test.skip=true

# Follow the Quarkus fast-jar layout and container-aware Java launcher.
FROM registry.access.redhat.com/ubi9/openjdk-17-runtime:1.24
WORKDIR /deployments
COPY --from=build --chown=185:0 /workspace/target/quarkus-app/lib/ ./lib/
COPY --from=build --chown=185:0 /workspace/target/quarkus-app/*.jar ./
COPY --from=build --chown=185:0 /workspace/target/quarkus-app/app/ ./app/
COPY --from=build --chown=185:0 /workspace/target/quarkus-app/quarkus/ ./quarkus/
ENV QUARKUS_HTTP_HOST=0.0.0.0 \
    QUARKUS_HTTP_PORT=8080 \
    JAVA_APP_JAR=/deployments/quarkus-run.jar \
    JAVA_OPTS_APPEND="-Djava.util.logging.manager=org.jboss.logmanager.LogManager"
EXPOSE 8080
USER 185
ENTRYPOINT ["/opt/jboss/container/java/run/run-java.sh"]
