package com.javabytes.mcp.tools.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

/**
 * Generates Docker assets for a Spring Boot service: a multi-stage Dockerfile
 * and a compose file that keeps credentials in environment variables.
 */
public class GenerateSpringBootDockerTool implements Tool {

    @Override
    public String getName() { return "springboot_generate_docker"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "springboot_generate_docker",
                "Generate Dockerfile",
                "Generate a multi-stage Dockerfile and docker-compose file for a Spring Boot service.",
                "springboot",
                false,
                true,
                false,
                true,
                false);
    }

    @Override
    public JsonNode execute(JsonNode params) {
        String jarName = params.has("jarName") ? params.get("jarName").asText() : "app.jar";
        String javaVersion = params.has("javaVersion") ? params.get("javaVersion").asText() : "25";
        String databaseName = params.has("databaseName") ? params.get("databaseName").asText() : "appdb";
        String appPort = params.has("appPort") ? params.get("appPort").asText() : "8080";

        try {
            String dockerfile = generateDockerfile(jarName, javaVersion, appPort);
            String compose = generateDockerCompose(databaseName, appPort);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("jarName", jarName);
            result.put("javaVersion", javaVersion);
            result.put("databaseName", databaseName);
            result.put("dockerfile", dockerfile);
            result.put("dockerCompose", compose);
            result.put("message", "Generated Docker configuration; set MYSQL_PASSWORD and MYSQL_ROOT_PASSWORD before starting.");
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_DOCKER_ERROR", "Error generating Docker config: " + e.getMessage());
        }
    }

    private String generateDockerfile(String jarName, String javaVersion, String appPort) {
        String safeJar = jarName == null || jarName.isBlank() ? "app.jar" : jarName.trim();
        String safeVersion = javaVersion == null || javaVersion.isBlank() ? "25" : javaVersion.trim();
        String safePort = appPort == null || appPort.isBlank() ? "8080" : appPort.trim();
        return """
                # Multi-stage Dockerfile for a Spring Boot application
                FROM maven:3.9-eclipse-temurin-%1$s AS build
                WORKDIR /workspace

                # Cache dependencies before copying sources
                COPY pom.xml .
                RUN mvn -q -B -DskipTests dependency:go-offline

                COPY src ./src
                RUN mvn -q -B -DskipTests package

                FROM eclipse-temurin:%1$s-jre
                WORKDIR /app

                # Run as a non-root user
                RUN useradd --system --uid 1001 appuser
                COPY --from=build /workspace/target/%2$s app.jar
                USER appuser

                EXPOSE %3$s
                ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
                """.formatted(safeVersion, safeJar, safePort);
    }

    private String generateDockerCompose(String databaseName, String appPort) {
        String db = databaseName == null || databaseName.isBlank() ? "appdb" : databaseName.trim();
        String port = appPort == null || appPort.isBlank() ? "8080" : appPort.trim();
        return """
                # docker-compose.yml - no credentials are stored in this file.
                # Create a .env file with MYSQL_PASSWORD and MYSQL_ROOT_PASSWORD.
                services:
                  app:
                    build: .
                    ports:
                      - "%2$s:%2$s"
                    environment:
                      SPRING_PROFILES_ACTIVE: prod
                      DB_URL: jdbc:mysql://db:3306/${MYSQL_DATABASE:-%1$s}
                      DB_USER: ${MYSQL_USER:-app}
                      DB_PASSWORD: ${MYSQL_PASSWORD:?set MYSQL_PASSWORD}
                    depends_on:
                      db:
                        condition: service_healthy
                    restart: unless-stopped

                  db:
                    image: mysql:8.4
                    environment:
                      MYSQL_DATABASE: ${MYSQL_DATABASE:-%1$s}
                      MYSQL_USER: ${MYSQL_USER:-app}
                      MYSQL_PASSWORD: ${MYSQL_PASSWORD:?set MYSQL_PASSWORD}
                      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:?set MYSQL_ROOT_PASSWORD}
                    volumes:
                      - mysql_data:/var/lib/mysql
                    healthcheck:
                      test: ["CMD-SHELL", "mysqladmin ping -h localhost -u root -p$${MYSQL_ROOT_PASSWORD}"]
                      interval: 10s
                      timeout: 5s
                      retries: 10
                      start_period: 30s
                    restart: unless-stopped

                volumes:
                  mysql_data:
                """.formatted(db, port);
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("jarName", new Tool.StringSchema().description("JAR file name produced in target/ (default: app.jar)"));
        schema.prop("javaVersion", new Tool.StringSchema().description("JDK major version for both stages (default: 25)"));
        schema.prop("databaseName", new Tool.StringSchema().description("Default MySQL database name (default: appdb)"));
        schema.prop("appPort", new Tool.StringSchema().description("Application port (default: 8080)"));
        return schema;
    }
}
