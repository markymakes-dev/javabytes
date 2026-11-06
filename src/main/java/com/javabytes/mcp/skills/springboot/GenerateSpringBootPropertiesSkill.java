package com.javabytes.mcp.skills.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

/**
 * Generates a Spring Boot {@code application.properties} template.
 * Credentials default to environment-variable placeholders rather than literals.
 */
public class GenerateSpringBootPropertiesSkill implements Skill {

    @Override
    public String getName() { return "springboot_generate_properties"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String dbUrl = params.has("dbUrl") ? params.get("dbUrl").asText() : "jdbc:mysql://localhost:3306/appdb";
        String dbUser = params.has("dbUser") ? params.get("dbUser").asText() : "app";
        String dbPassword = params.has("dbPassword") ? params.get("dbPassword").asText() : "";
        String serverPort = params.has("serverPort") ? params.get("serverPort").asText() : "8080";
        String ddlAuto = params.has("ddlAuto") ? params.get("ddlAuto").asText() : "validate";

        try {
            String properties = generateProperties(dbUrl, dbUser, dbPassword, serverPort, ddlAuto);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("dbUrl", dbUrl);
            result.put("serverPort", serverPort);
            result.put("properties", properties);
            result.put("message", "Generated Spring Boot application.properties");
            if (dbPassword != null && !dbPassword.isEmpty()) {
                result.put("warning", "A literal password was embedded at your request; prefer the DB_PASSWORD environment variable.");
            }
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_PROPERTIES_ERROR", "Error generating properties: " + e.getMessage());
        }
    }

    private String generateProperties(String dbUrl, String dbUser, String dbPassword,
                                      String serverPort, String ddlAuto) {
        String passwordValue = (dbPassword == null || dbPassword.isEmpty())
                ? "${DB_PASSWORD:}"
                : dbPassword;
        return "# Spring Boot application properties\n"
                + "\n"
                + "# Server\n"
                + "server.port=" + safe(serverPort) + "\n"
                + "\n"
                + "# Database (MySQL) - credentials come from the environment\n"
                + "spring.datasource.url=${DB_URL:" + safe(dbUrl) + "}\n"
                + "spring.datasource.username=${DB_USER:" + safe(dbUser) + "}\n"
                + "spring.datasource.password=" + safe(passwordValue) + "\n"
                + "\n"
                + "# JPA / Hibernate\n"
                + "# 'validate' is safe by default; use Flyway or Liquibase for schema changes.\n"
                + "spring.jpa.hibernate.ddl-auto=" + safe(ddlAuto) + "\n"
                + "spring.jpa.open-in-view=false\n"
                + "spring.jpa.properties.hibernate.jdbc.time_zone=UTC\n"
                + "\n"
                + "# Jackson\n"
                + "spring.jackson.serialization.write-dates-as-timestamps=false\n"
                + "spring.jackson.default-property-inclusion=non_null\n";
    }

    /** Prevent generated properties files from being broken by stray newlines. */
    private String safe(String value) {
        return value == null ? "" : value.replace("\r", "").replace("\n", "");
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.prop("dbUrl", new Skill.StringSchema().description("MySQL JDBC URL (default: jdbc:mysql://localhost:3306/appdb)"));
        schema.prop("dbUser", new Skill.StringSchema().description("MySQL username (default: app)"));
        schema.prop("dbPassword", new Skill.StringSchema().description("Optional literal password; omitted means ${DB_PASSWORD:}"));
        schema.prop("serverPort", new Skill.StringSchema().description("Server port (default: 8080)"));
        schema.prop("ddlAuto", new Skill.StringSchema().description("Hibernate ddl-auto mode (default: validate)"));
        return schema;
    }
}
