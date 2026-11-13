# syntax=docker/dockerfile:1

# Build stage: shaded jar from source. Tests run in CI, not in the image build.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# Runtime stage: JRE only, no build tooling.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --create-home --uid 10001 javabytes
COPY --from=build /src/target/javabytes.jar /app/javabytes.jar

# Stdio transport: the JSON-RPC stream is stdin/stdout, so logs go to stderr.
USER javabytes
ENTRYPOINT ["java", "-jar", "/app/javabytes.jar"]
