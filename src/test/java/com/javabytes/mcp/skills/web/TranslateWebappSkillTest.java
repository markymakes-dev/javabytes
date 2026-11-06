package com.javabytes.mcp.skills.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslateWebappSkillTest {

    @Test
    void plansMigrationAndScaffoldsSpringBootWithHonestStubs(@TempDir Path root) throws Exception {
        writeProject(root);
        ObjectNode params = params(root);
        params.put("write_to", root.resolve("out").toString());

        JsonNode result = new TranslateWebappSkill().execute(params);

        assertTrue(result.path("success").asBoolean(), result.toString());
        assertEquals("spring-boot-vaadin", result.path("target").asText());

        String plan = result.path("migration_plan").asText();
        assertTrue(plan.contains("# Migration plan"));
        assertTrue(plan.contains("not a port"));
        assertTrue(plan.contains("Coexistence option"));
        assertTrue(plan.contains("GraalJS"));
        assertTrue(plan.contains("/users/:id"));
        assertTrue(plan.contains("`document`"));

        Path out = root.resolve("out");
        assertTrue(Files.isRegularFile(out.resolve("MIGRATION.md")));
        assertTrue(Files.isRegularFile(out.resolve("pom.xml")));
        assertTrue(Files.isRegularFile(out.resolve("src/main/resources/application.properties")));

        String pom = Files.readString(out.resolve("pom.xml"));
        assertTrue(pom.contains("vaadin-spring-boot-starter"));
        assertTrue(pom.contains("java.version>21<"));

        String controller = Files.readString(
                out.resolve("src/main/java/com/example/web/ApiController.java"));
        assertTrue(controller.contains("@GetMapping(\"/users/{id}\")"));
        assertTrue(controller.contains("@PostMapping(\"/users\")"));
        assertTrue(controller.contains("status(501)"));
        assertFalse(controller.contains("/api\""));

        String properties = Files.readString(
                out.resolve("src/main/resources/application.properties"));
        assertTrue(properties.contains("${DB_PASSWORD:}"));
    }

    @Test
    void scaffoldsJavaFxWhenRequested(@TempDir Path root) throws Exception {
        writeProject(root);
        ObjectNode params = params(root);
        params.put("target", "javafx");
        params.put("write_to", root.resolve("desktop").toString());

        JsonNode result = new TranslateWebappSkill().execute(params);

        assertTrue(result.path("success").asBoolean(), result.toString());
        assertEquals("javafx", result.path("target").asText());
        Path desktop = root.resolve("desktop");
        assertTrue(Files.isRegularFile(desktop.resolve("src/main/java/com/example/App.java")));
        String app = Files.readString(desktop.resolve("src/main/java/com/example/App.java"));
        assertTrue(app.contains("extends Application"));
        String pom = Files.readString(desktop.resolve("pom.xml"));
        assertTrue(pom.contains("javafx-controls"));
    }

    @Test
    void rejectsInvalidTargetsAndMissingPaths(@TempDir Path root) {
        ObjectNode invalid = params(root);
        invalid.put("target", "cobol");
        JsonNode result = new TranslateWebappSkill().execute(invalid);
        assertFalse(result.path("success").asBoolean(true));
        assertEquals("INVALID_TARGET", result.path("error").asText());

        ObjectNode missing = JsonUtils.MAPPER.createObjectNode();
        JsonNode missingResult = new TranslateWebappSkill().execute(missing);
        assertFalse(missingResult.path("success").asBoolean(true));
        assertEquals("MISSING_PARAM", missingResult.path("error").asText());
    }

    private static ObjectNode params(Path root) {
        ObjectNode params = JsonUtils.MAPPER.createObjectNode();
        params.put("path", root.toString());
        params.put("target", "auto");
        params.put("package_name", "com.example");
        params.put("max_files", 50);
        return params;
    }

    private static void writeProject(Path root) throws Exception {
        Files.writeString(root.resolve("package.json"), """
                {
                  "dependencies": {
                    "express": "^4.19.0",
                    "react": "^18.3.0",
                    "mysql2": "^3.11.0"
                  }
                }
                """);
        Files.writeString(root.resolve("server.js"), """
                const express = require('express');
                const app = express();
                app.get('/users/:id', (req, res) => res.json({}));
                app.post('/users', (req, res) => res.json({}));
                """);
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/App.jsx"), """
                import React from 'react';

                export function App() {
                  return <main>{document.title}</main>;
                }
                """);
    }
}
