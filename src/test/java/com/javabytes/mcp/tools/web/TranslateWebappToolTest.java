package com.javabytes.mcp.tools.web;

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

class TranslateWebappToolTest {

    @Test
    void plansMigrationAndScaffoldsSpringBootWithHonestStubs(@TempDir Path root) throws Exception {
        writeProject(root);
        ObjectNode params = params(root);
        params.put("write_to", root.resolve("out").toString());

        JsonNode result = new TranslateWebappTool().execute(params);

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

        JsonNode result = new TranslateWebappTool().execute(params);

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
        JsonNode result = new TranslateWebappTool().execute(invalid);
        assertFalse(result.path("success").asBoolean(true));
        assertEquals("INVALID_TARGET", result.path("error").asText());

        ObjectNode missing = JsonUtils.MAPPER.createObjectNode();
        JsonNode missingResult = new TranslateWebappTool().execute(missing);
        assertFalse(missingResult.path("success").asBoolean(true));
        assertEquals("MISSING_PARAM", missingResult.path("error").asText());
    }

    @Test
    void keepFrontendGeneratesSpringBootAppAndServesStaticAssets(@TempDir Path root)
            throws Exception {
        writeStaticProject(root);
        ObjectNode params = params(root);
        params.put("target", "keep-frontend");
        params.put("write_to", root.resolve("out").toString());
        params.put("max_files", 50);

        JsonNode result = new TranslateWebappTool().execute(params);

        assertTrue(result.path("success").asBoolean(), result.toString());
        assertEquals("keep-frontend", result.path("target").asText());

        Path out = root.resolve("out");
        String pom = Files.readString(out.resolve("pom.xml"));
        assertTrue(pom.contains("spring-boot-starter-web"), pom);
        assertTrue(pom.contains("java.version>21<"), pom);

        // Static assets passed through unchanged (pixel-identical).
        assertTrue(Files.isRegularFile(out.resolve("src/main/resources/static/index.html")));
        assertTrue(Files.isRegularFile(out.resolve("src/main/resources/static/style.css")));
        String html = Files.readString(out.resolve("src/main/resources/static/index.html"));
        assertEquals("<h1>Hello, world</h1>\n", html);

        String props = Files.readString(out.resolve("src/main/resources/application.properties"));
        assertTrue(props.contains("${DB_PASSWORD:}"));
        assertTrue(props.contains("jdbc:h2:mem:appdb"));

        assertTrue(Files.isRegularFile(out.resolve("README.md")));
        String readme = Files.readString(out.resolve("README.md"));
        assertTrue(readme.contains("mvn spring-boot:run"));

        // Complete file map is returned, not just disk writes.
        assertFalse(result.path("files").isMissingNode());
        assertTrue(result.path("files").has("pom.xml"));
    }

    @Test
    void autoPicksKeepFrontendForFrontendOnlyProject(@TempDir Path root) throws Exception {
        // Pure browser frontend: React, no backend framework.
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("package.json"), """
                {
                  "dependencies": {
                    "react": "^18.3.0"
                  }
                }
                """);
        Files.writeString(root.resolve("src/App.jsx"), """
                import React from 'react';
                export function App() {
                  return <main>Hi</main>;
                }
                """);

        ObjectNode params = params(root);
        params.put("target", "auto");
        params.put("max_files", 50);

        JsonNode result = new TranslateWebappTool().execute(params);

        assertTrue(result.path("success").asBoolean(), result.toString());
        assertEquals("keep-frontend", result.path("target").asText());
        String plan = result.path("migration_plan").asText();
        assertTrue(plan.contains("keep-frontend"));
        // Complete file map is returned, not just instructions.
        assertFalse(result.path("files").isMissingNode());
    }

    @Test
    void dataCentricGeneratesJpaEntityRepositoryAndCrudController(@TempDir Path root)
            throws Exception {
        writeProject(root);
        ObjectNode params = params(root);
        params.put("target", "keep-frontend");
        params.put("write_to", root.resolve("out").toString());
        params.put("entities", entitiesJson());
        params.put("max_files", 50);

        JsonNode result = new TranslateWebappTool().execute(params);
        assertTrue(result.path("success").asBoolean(), result.toString());

        Path out = root.resolve("out");

        String entity = Files.readString(out.resolve("src/main/java/com/example/entity/User.java"));
        assertTrue(entity.contains("@Entity"));
        assertTrue(entity.contains("@Table(name = \"users\")"));
        assertTrue(entity.contains("@Id"));
        assertTrue(entity.contains("private Integer id"));
        assertTrue(entity.contains("private String email"));
        assertTrue(entity.contains("private LocalDateTime createdAt"));

        String repo = Files.readString(
                out.resolve("src/main/java/com/example/repository/UserRepository.java"));
        assertTrue(repo.contains("extends JpaRepository<User, Integer>"));

        String controller = Files.readString(
                out.resolve("src/main/java/com/example/web/UserController.java"));
        assertTrue(controller.contains("@RestController"));
        assertTrue(controller.contains("@RequestMapping(\"/api/users\")"));
        assertTrue(controller.contains("@GetMapping"));
        assertTrue(controller.contains("@PostMapping"));
        assertTrue(controller.contains("@PutMapping"));
        assertTrue(controller.contains("@DeleteMapping"));

        // No 501 stubs when real entities are provided.
        assertFalse(Files.isRegularFile(out.resolve("src/main/java/com/example/web/ApiController.java")));
    }

    @Test
    void dataCentricApplicationPropertiesAndReadme(@TempDir Path root) throws Exception {
        writeProject(root);
        ObjectNode params = params(root);
        params.put("target", "spring-boot-rest");
        params.put("write_to", root.resolve("out").toString());
        params.put("entities", entitiesJson());
        params.put("jdbc_url", "jdbc:mysql://example:3306/appdb");
        params.put("max_files", 50);

        JsonNode result = new TranslateWebappTool().execute(params);
        assertTrue(result.path("success").asBoolean(), result.toString());

        Path out = root.resolve("out");
        String props = Files.readString(out.resolve("src/main/resources/application.properties"));
        assertTrue(props.contains("jdbc:mysql://example:3306/appdb"));
        assertTrue(props.contains("com.mysql.cj.jdbc.Driver"));
        assertTrue(props.contains("${DB_PASSWORD:}"));

        String readme = Files.readString(out.resolve("README.md"));
        assertTrue(readme.contains("mvn spring-boot:run"));
        assertTrue(readme.contains("User"));
    }

    @Test
    void dataCentricReportsLimitations(@TempDir Path root) throws Exception {
        writeProject(root);
        ObjectNode params = params(root);
        params.put("target", "keep-frontend");
        params.put("entities", """
                [ { "name": "Empty", "table": "empties", "fields": [] } ]
                """);
        params.put("max_files", 50);

        JsonNode result = new TranslateWebappTool().execute(params);
        assertTrue(result.path("success").asBoolean(), result.toString());
        assertTrue(result.path("limitations").isArray());
        boolean hasLimitation = false;
        for (var lim : result.path("limitations")) {
            if (lim.asText().contains("no parseable fields")) {
                hasLimitation = true;
            }
        }
        assertTrue(hasLimitation, "expected a limitations entry for the entity without fields");
    }

    @Test
    void invalidTargetStillRejected(@TempDir Path root) {
        ObjectNode invalid = params(root);
        invalid.put("target", "keep-frontend-x");
        JsonNode result = new TranslateWebappTool().execute(invalid);
        assertFalse(result.path("success").asBoolean(true));
        assertEquals("INVALID_TARGET", result.path("error").asText());
    }

    @Test
    void vaadinTargetGeneratesVaadinPomAndApp(@TempDir Path root) throws Exception {
        writeProject(root);
        ObjectNode params = params(root);
        params.put("target", "spring-boot-vaadin");
        params.put("write_to", root.resolve("out").toString());
        params.put("max_files", 50);

        JsonNode result = new TranslateWebappTool().execute(params);

        assertTrue(result.path("success").asBoolean(), result.toString());
        assertEquals("spring-boot-vaadin", result.path("target").asText());
        Path out = root.resolve("out");
        String pom = Files.readString(out.resolve("pom.xml"));
        assertTrue(pom.contains("vaadin-spring-boot-starter"), pom);
        assertTrue(Files.isRegularFile(out.resolve("src/main/java/com/example/Application.java")));
        String props = Files.readString(out.resolve("src/main/resources/application.properties"));
        assertTrue(props.contains("vaadin.launch-browser=true"));
    }

    @Test
    void dataCentricWithSpringBootRestGeneratesCrudController(@TempDir Path root)
            throws Exception {
        writeProject(root);
        ObjectNode params = params(root);
        params.put("target", "spring-boot-rest");
        params.put("write_to", root.resolve("out").toString());
        params.put("entities", entitiesJson());
        params.put("max_files", 50);

        JsonNode result = new TranslateWebappTool().execute(params);
        assertTrue(result.path("success").asBoolean(), result.toString());

        Path out = root.resolve("out");
        String controller = Files.readString(
                out.resolve("src/main/java/com/example/web/UserController.java"));
        assertTrue(controller.contains("@RequestMapping(\"/api/users\")"));
        assertTrue(controller.contains("repository.findAll()"));
        assertTrue(controller.contains("repository.save(input)"));

        String pom = Files.readString(out.resolve("pom.xml"));
        assertTrue(pom.contains("spring-boot-starter-data-jpa"), pom);
        assertTrue(pom.contains("<artifactId>h2</artifactId>"), pom);
    }

    @Test
    void keepFrontendNoStaticAssetsReportsLimitation(@TempDir Path root) throws Exception {
        // Project with only JSX source (not a static extension) and no static assets.
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("package.json"), "{ }");
        Files.writeString(root.resolve("src/App.jsx"), "export function App() { return null; }");

        ObjectNode params = params(root);
        params.put("target", "keep-frontend");
        params.put("write_to", root.resolve("out").toString());
        params.put("max_files", 50);

        JsonNode result = new TranslateWebappTool().execute(params);
        assertTrue(result.path("success").asBoolean(), result.toString());
        boolean hasLimitation = false;
        for (var lim : result.path("limitations")) {
            if (lim.asText().contains("No static assets")) {
                hasLimitation = true;
            }
        }
        assertTrue(hasLimitation, "expected a limitation when no static assets exist");
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

    private static void writeStaticProject(Path root) throws Exception {
        Files.writeString(root.resolve("package.json"), """
                {
                  "dependencies": {
                    "react": "^18.3.0"
                  }
                }
                """);
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/App.jsx"), """
                import React from 'react';
                export function App() { return <main>Hi</main>; }
                """);
        Files.writeString(root.resolve("index.html"), "<h1>Hello, world</h1>\n");
        Files.writeString(root.resolve("style.css"), "body { color: green; }\n");
    }

    private static String entitiesJson() {
        return """
                [
                  {
                    "name": "User",
                    "table": "users",
                    "fields": [
                      { "name": "id", "column": "id", "type": "INT", "nullable": false,
                        "unique": false, "id": true, "confidence": "high" },
                      { "name": "email", "column": "email", "type": "STRING", "nullable": false,
                        "unique": true, "confidence": "high" },
                      { "name": "createdAt", "column": "created_at", "type": "TIMESTAMP",
                        "nullable": true, "confidence": "medium" }
                    ]
                  }
                ]
                """;
    }
}
