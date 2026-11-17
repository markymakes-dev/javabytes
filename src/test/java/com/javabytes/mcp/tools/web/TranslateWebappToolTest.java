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
    void crudControllerTemplatePlaceholderAlignment(@TempDir Path root) throws Exception {
        // Regression: the crudController format args were misaligned, producing
        // Optional<id>, @RequestBody id input, /{User} routes and
        // @PathVariable User id — none of which compile.
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

        // Entity type in generic positions (was Optional<id> / @RequestBody id).
        assertTrue(controller.contains("Optional<User> found"), controller);
        assertTrue(controller.contains(
                "public ResponseEntity<User> create(@RequestBody User input)"), controller);
        assertTrue(controller.contains(
                "@RequestBody User input)"), controller);

        // Id member in route placeholders (was /{User}).
        assertTrue(controller.contains("@PutMapping(\"/{id}\")"), controller);
        assertTrue(controller.contains("@DeleteMapping(\"/{id}\")"), controller);

        // Id TYPE in @PathVariable (was @PathVariable User id).
        assertTrue(controller.contains("findById(@PathVariable Integer id)"), controller);
        assertTrue(controller.contains("delete(@PathVariable Integer id)"), controller);
        assertFalse(controller.contains("@PathVariable User id"), controller);
        assertFalse(controller.contains("Optional<id>"), controller);
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

    @Test
    void scanOnlyReturnsAnalysisAndPlanWithoutScaffold(@TempDir Path root) throws Exception {
        writeProject(root);
        ObjectNode params = params(root);
        params.put("scan_only", true);
        params.put("report_path", root.resolve("SCAN-REPORT.md").toString());

        JsonNode result = new TranslateWebappTool().execute(params);

        assertTrue(result.path("success").asBoolean(), result.toString());
        assertTrue(result.path("scan_only").asBoolean());
        assertTrue(result.path("analysis").isObject());
        assertTrue(result.path("migration_plan").isTextual());
        assertTrue(result.path("report_written").asText().endsWith("SCAN-REPORT.md"));
        // No scaffold written
        assertTrue(result.path("files").isMissingNode());
    }

    @Test
    void reportPathWritesDetailedTranslationReport(@TempDir Path root) throws Exception {
        writeProject(root);
        ObjectNode params = params(root);
        params.put("write_to", root.resolve("out").toString());
        params.put("report_path", root.resolve("TRANSLATION-REPORT.md").toString());

        JsonNode result = new TranslateWebappTool().execute(params);

        assertTrue(result.path("success").asBoolean(), result.toString());
        String reportWritten = result.path("report_written").asText();
        Path report = root.resolve("TRANSLATION-REPORT.md");
        assertTrue(reportWritten.contains("TRANSLATION-REPORT.md"),
                "report_written was: " + reportWritten);
        assertTrue(Files.isRegularFile(report));
        String reportContent = Files.readString(report);
        assertTrue(reportContent.contains("# Translation Report"));
        assertTrue(reportContent.contains("## Summary"));
        assertTrue(reportContent.contains("## Scaffold Files Generated"));
        // "## Limitations" only appears when there are limitations (summary shows "Limitations | 0")
        assertTrue(reportContent.contains("## Browser-Bound Files"));
        assertTrue(reportContent.contains("## Data-Centric Generation"));
        assertTrue(reportContent.contains("## Written Files"));
        // Summary table should show counts correctly
        assertTrue(reportContent.contains("Limitations | 0"));
    }

    @Test
    void generatedScaffoldIncludesMavenWrapper(@TempDir Path root) throws Exception {
        writeProject(root);
        ObjectNode params = params(root);
        params.put("target", "keep-frontend");
        params.put("write_to", root.resolve("out").toString());

        JsonNode result = new TranslateWebappTool().execute(params);
        assertTrue(result.path("success").asBoolean(), result.toString());

        Path out = root.resolve("out");
        // Maven wrapper files
        assertTrue(Files.isRegularFile(out.resolve("mvnw")), "mvnw should exist");
        assertTrue(Files.isRegularFile(out.resolve("mvnw.cmd")), "mvnw.cmd should exist");
        assertTrue(Files.isRegularFile(out.resolve(".mvn/wrapper/maven-wrapper.properties")),
                "maven-wrapper.properties should exist");
        String props = Files.readString(out.resolve(".mvn/wrapper/maven-wrapper.properties"));
        assertTrue(props.contains("wrapperVersion=3.3.2"), "properties should have version 3.3.2");
        // mvnw should have exec bit on POSIX (no-op test on Windows; content check)
        String mvnw = Files.readString(out.resolve("mvnw"));
        assertTrue(mvnw.contains("Apache Maven Wrapper startup batch script, version 3.3.2"));
        // mvnw.cmd should have CRLF
        String mvnwCmd = Files.readString(out.resolve("mvnw.cmd"));
        assertTrue(mvnwCmd.contains("\r\n"), "mvnw.cmd should use CRLF line endings");
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

    @Test
    void javafxWithEntitiesGeneratesCrudViewsAndHttpClient(@TempDir Path root) throws Exception {
        writeProject(root);
        ObjectNode params = params(root);
        params.put("target", "javafx");
        params.put("write_to", root.resolve("desktop").toString());
        params.put("entities", entitiesJson());

        JsonNode result = new TranslateWebappTool().execute(params);

        assertTrue(result.path("success").asBoolean(), result.toString());
        assertEquals("javafx", result.path("target").asText());

        Path desktop = root.resolve("desktop");

        // Core files
        assertTrue(Files.isRegularFile(desktop.resolve("src/main/java/com/example/App.java")));
        assertTrue(Files.isRegularFile(desktop.resolve("src/main/java/com/example/client/ApiClient.java")));
        assertTrue(Files.isRegularFile(desktop.resolve("pom.xml")));

        // Entity files
        assertTrue(Files.isRegularFile(desktop.resolve("src/main/java/com/example/entity/User.java")));

        // CRUD views
        assertTrue(Files.isRegularFile(desktop.resolve("src/main/java/com/example/ui/UserListView.java")),
                "UserListView should be generated");
        assertTrue(Files.isRegularFile(desktop.resolve("src/main/java/com/example/ui/UserFormView.java")),
                "UserFormView should be generated");

        // Verify content of ApiClient
        String apiClient = Files.readString(desktop.resolve("src/main/java/com/example/client/ApiClient.java"));
        assertTrue(apiClient.contains("class ApiClient"));
        assertTrue(apiClient.contains("HttpClient"));
        assertTrue(apiClient.contains("ObjectMapper"));

        // Verify UserListView has TableView and CRUD buttons
        String listView = Files.readString(desktop.resolve("src/main/java/com/example/ui/UserListView.java"));
        assertTrue(listView.contains("class UserListView"));
        assertTrue(listView.contains("TableView<User>"));
        assertTrue(listView.contains("Button refreshBtn"));
        assertTrue(listView.contains("Button addBtn"));
        assertTrue(listView.contains("Button editBtn"));
        assertTrue(listView.contains("Button deleteBtn"));

        // Template alignment (regression: format args were misaligned by one
        // from the constructor onward, producing `public User(ApiClient ...)`
        // inside UserListView, `TableColumn<User, User>` generics and stray
        // getType()/getString() calls that never compile).
        assertTrue(listView.contains("public class UserListView"), listView);
        assertTrue(listView.contains("public UserListView(ApiClient apiClient)"), listView);
        assertTrue(listView.contains("TableColumn<User, Integer> idCol"), listView);
        assertTrue(listView.contains("getId()"), listView);
        assertTrue(listView.contains("ObjectMapper objectMapper"), listView);
        assertTrue(listView.contains("convertValue(node, User.class)"), listView);
        assertTrue(listView.contains("new UserFormView(apiClient"), listView);

        // Verify UserFormView has form fields
        String formView = Files.readString(desktop.resolve("src/main/java/com/example/ui/UserFormView.java"));
        assertTrue(formView.contains("class UserFormView"));
        assertTrue(formView.contains("TextField emailField"));
        assertTrue(formView.contains("DatePicker createdAtField"));

        // Verify pom.xml has javafx-fxml dependency
        String pom = Files.readString(desktop.resolve("pom.xml"));
        assertTrue(pom.contains("javafx-fxml"));

        // Verify README mentions CRUD views
        String readme = Files.readString(desktop.resolve("README.md"));
        assertTrue(readme.contains("CRUD views"));
        assertTrue(readme.contains("ApiClient"));
    }
}
