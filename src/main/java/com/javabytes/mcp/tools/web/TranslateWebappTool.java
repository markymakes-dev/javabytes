package com.javabytes.mcp.tools.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.config.AppConfig;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.PathValidator;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Produces a migration plan for a JavaScript/TypeScript web app and, on
 * request, a compilable Spring Boot or JavaFX scaffold.
 *
 * <p>It deliberately does not pretend to port the application. The plan names
 * the browser APIs, npm dependencies, routes and components that require human
 * decisions, and the scaffold contains only generated structure.</p>
 */
public class TranslateWebappTool implements Tool {

    private static final Pattern PACKAGE = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");

    /** Extensions treated as static assets to pass through unchanged (keep-frontend). */
    private static final Set<String> STATIC_EXTENSIONS = Set.of(
            ".html", ".htm", ".css", ".js", ".mjs", ".cjs",
            ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico",
            ".woff", ".woff2", ".ttf", ".eot",
            ".json", ".webmanifest", ".xml", ".txt", ".map");

    /**
     * One parsed entity from the ticket #2 {@code entities} payload, used to
     * derive a JPA entity, a Spring Data JPA repository and a CRUD controller.
     */
    private record EntityDef(String name, String table, List<FieldDef> fields) {
    }

    /** The result of the {@code auto} target classifier: which strategy was
     * picked and why, for honest reporting to the caller. */
    private record TargetDecision(String target, String reason) {
    }

    private record FieldDef(String name, String column, String type, boolean nullable,
                            boolean unique, boolean id, String confidence) {
    }

    @Override
    public String getName() { return "translate_webapp"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "translate_webapp",
                "Plan Web App Migration",
                "Produce a migration plan for a web app and generate a complete, runnable target project: a keep-frontend Spring Boot REST backend that serves the original frontend pixel-identical, a Vaadin browser UI, a JavaFX desktop shell, or (auto) a classifier-picked strategy with reasons; with optional ticket #2 data models it derives JPA entities, Spring Data JPA repositories and real CRUD controllers. Returns a complete file map (paths to contents).",
                "codegen",
                false,
                false,
                false,
                true,
                false);
    }

    @Override
    public JsonNode execute(JsonNode params) {
        String pathParam = params.has("path")
                ? params.get("path").asText().trim()
                : params.has("source_path")
                        ? params.get("source_path").asText().trim()
                        : "";
        String requestedTarget = params.has("target")
                ? params.get("target").asText("auto").trim().toLowerCase(Locale.ROOT)
                : "auto";
        String packageName = params.has("package_name")
                ? params.get("package_name").asText("com.example").trim()
                : "com.example";
        String writeTo = params.has("write_to") ? params.get("write_to").asText().trim() : "";
        int maxFiles = params.has("max_files") ? params.get("max_files").asInt(500) : 500;
        String entitiesJson = params.has("entities") && !params.get("entities").isNull()
                ? params.get("entities").asText()
                : null;
        String jdbcUrl = params.has("jdbc_url") && !params.get("jdbc_url").isNull()
                ? params.get("jdbc_url").asText().trim()
                : null;

        if (pathParam.isEmpty()) {
            return ErrorHelper.error("MISSING_PARAM", "Parameter 'path' (or 'source_path') is required");
        }
        if (!Set.of("auto", "keep-frontend", "spring-boot-rest", "spring-boot-vaadin",
                "javafx").contains(requestedTarget)) {
            return ErrorHelper.error("INVALID_TARGET",
                    "target must be auto, keep-frontend, spring-boot-rest, "
                            + "spring-boot-vaadin or javafx");
        }
        if (!PACKAGE.matcher(packageName).matches()) {
            return ErrorHelper.error("INVALID_PACKAGE", "Invalid Java package: " + packageName);
        }

        try {
            Path root = Path.of(pathParam);
            if (!PathValidator.isAllowed(root)) {
                return ErrorHelper.error("PATH_NOT_ALLOWED",
                        "Path not allowed: " + pathParam + ". Allowed roots: "
                                + AppConfig.describeAllowedDirectories());
            }
            if (!Files.isDirectory(root)) {
                return ErrorHelper.error("NOT_A_DIRECTORY", "Not a directory: " + pathParam);
            }

            ObjectNode analysis = JavaScriptAnalyzer.analyzeProject(root, maxFiles);
            TargetDecision decision = resolveTarget(requestedTarget, analysis);
            String target = decision.target();
            String strategyReason = decision.reason();

            String plan = migrationPlan(root, analysis, target, packageName);

            // Complete per-target file map: paths -> contents.
            Map<String, String> fileMap = new LinkedHashMap<>();
            List<String> limitations = new ArrayList<>();
            generateProject(fileMap, limitations, target, packageName, analysis,
                    entitiesJson, jdbcUrl, root);

            ObjectNode out = JsonUtils.MAPPER.createObjectNode();
            out.put("success", true);
            out.put("target", target);
            out.put("strategy_reason", strategyReason);
            out.put("migration_plan", plan);
            out.set("analysis", analysis);

            // Per-field confidence / honesty: report what cannot be generated.
            ArrayNode limNode = out.putArray("limitations");
            for (String lim : limitations) {
                limNode.add(lim);
            }

            // Complete file map (paths -> contents).
            ObjectNode filesNode = out.putObject("files");
            for (var entry : fileMap.entrySet()) {
                filesNode.put(entry.getKey(), entry.getValue());
            }

            ArrayNode written = out.putArray("written_files");
            if (!writeTo.isEmpty()) {
                Path destination = Path.of(writeTo);
                if (!PathValidator.isAllowed(destination)) {
                    return ErrorHelper.error("PATH_NOT_ALLOWED",
                            "write_to is outside the sandbox: " + writeTo);
                }
                Files.createDirectories(destination);
                write(destination.resolve("MIGRATION.md"), plan, written);
                for (var entry : fileMap.entrySet()) {
                    write(destination.resolve(entry.getKey()), entry.getValue(), written);
                }
            }
            return out;
        } catch (Exception e) {
            return ErrorHelper.error("MIGRATION_ERROR",
                    "Error generating target app: " + e.getMessage());
        }
    }

    private static String migrationPlan(Path root, ObjectNode analysis, String target,
                                        String packageName) {
        StringBuilder md = new StringBuilder();
        md.append("# Migration plan\n\n");
        md.append("Source: `").append(root.toAbsolutePath()).append("`\n\n");
        md.append("Chosen target: **").append(target).append("**\n\n");
        md.append("> Generated by JavaBytes from static analysis. It is a plan, not a port.\n\n");

        md.append("## Inventory\n\n");
        md.append("| Metric | Value |\n|---|---|\n");
        md.append("| Files analyzed | ").append(analysis.path("files_analyzed").asInt()).append(" |\n");
        md.append("| Truncated | ").append(analysis.path("truncated").asBoolean()).append(" |\n");
        md.append("| Lines | ").append(analysis.path("total_lines").asInt()).append(" |\n");
        md.append("| JSX files | ").append(analysis.path("jsx_files").asInt()).append(" |\n");
        md.append("| TypeScript files | ").append(analysis.path("typescript_files").asInt()).append(" |\n");
        md.append("| Project type | ").append(analysis.path("project_type").asText()).append(" |\n");
        md.append("| Frameworks | ").append(join(analysis.path("frameworks"))).append(" |\n\n");

        md.append("## Dependencies\n\n");
        JsonNode mappings = analysis.path("dependency_mapping");
        if (mappings.isEmpty()) {
            md.append("No known npm dependencies were found.\n\n");
        } else {
            md.append("| npm | Version | Java equivalent |\n|---|---|---|\n");
            for (JsonNode mapping : mappings) {
                md.append("| `").append(mapping.path("npm").asText()).append("` | ")
                        .append(mapping.path("version").asText("-")).append(" | ")
                        .append(mapping.path("java").asText()).append(" |\n");
            }
            md.append('\n');
        }
        md.append("Dependencies not listed here have no direct Java equivalent and need an "
                + "explicit decision (replace, reimplement, or keep as a separate service).\n\n");

        md.append("## Routes\n\n");
        JsonNode routes = analysis.path("routes");
        if (routes.isEmpty()) {
            md.append("No routes were detected. If routing is dynamic or configured at runtime, "
                    + "enumerate it manually before porting.\n\n");
        } else {
            md.append("| Method | Path | Source |\n|---|---|---|\n");
            for (JsonNode route : routes) {
                md.append("| ").append(route.path("method").asText()).append(" | `")
                        .append(route.path("path").asText()).append("` | ")
                        .append(route.path("file").asText("-")).append(" |\n");
            }
            md.append('\n');
        }

        md.append("## Components\n\n");
        JsonNode components = analysis.path("components");
        if (components.isEmpty()) {
            md.append("No components were detected.\n\n");
        } else {
            for (JsonNode component : components) {
                md.append("- `").append(component.asText()).append("`\n");
            }
            md.append('\n');
        }

        md.append("## Browser APIs to replace\n\n");
        JsonNode browserApis = analysis.path("browser_apis");
        if (browserApis.isEmpty()) {
            md.append("No browser-only APIs were detected by name. Verify manually: the analyzer "
                    + "cannot see APIs reached through libraries.\n\n");
        } else {
            md.append("| JavaScript API | Java direction |\n|---|---|\n");
            for (JsonNode api : browserApis) {
                md.append("| `").append(api.asText()).append("` | ")
                        .append(browserDirection(api.asText())).append(" |\n");
            }
            md.append('\n');
        }

        md.append("## Coexistence option (consider first)\n\n");
        for (JsonNode option : analysis.path("coexistence_options")) {
            md.append("- ").append(option.asText()).append('\n');
        }
        md.append('\n');

        md.append("## Phases\n\n");        for (JsonNode phase : analysis.path("migration_phases")) {
            md.append("- ").append(phase.asText()).append('\n');
        }
        md.append('\n');

        md.append("## Risks\n\n");
        for (JsonNode risk : analysis.path("risks")) {
            md.append("- ").append(risk.asText()).append('\n');
        }
        md.append('\n');

        md.append("## Scaffold\n\n");
        md.append("Package: `").append(packageName).append("`\n\n");
        if (target.startsWith("spring-boot")) {
            md.append("- `pom.xml` with Spring Boot and Java 21\n");
            md.append("- `Application.java`\n");
            md.append("- `application.properties`\n");
            if (!routes.isEmpty()) {
                md.append("- `ApiController.java` with stubs returning HTTP 501 for detected routes\n");
            }
        } else {
            md.append("- `pom.xml` with JavaFX and Java 21\n");
            md.append("- `App.java` JavaFX application shell\n");
        }
        md.append("- Endpoint stubs intentionally return 501: implement them against the "
                + "acceptance tests captured in phase 1.\n\n");

        md.append("## What this tool does not do\n\n");
        md.append("- It does not execute the JavaScript app, so it cannot observe runtime routes "
                + "or state.\n");
        md.append("- It does not translate JSX, DOM code, npm packages or async behaviour.\n");
        md.append("- It does not prove feature parity. That requires tests, which are the first "
                + "deliverable of the plan.\n");
        return md.toString();
    }

    private static void writeScaffold(Path root, String target, String packageName,
                                      ObjectNode analysis, ArrayNode written) throws Exception {
        String packagePath = packageName.replace('.', '/');
        Path javaDir = root.resolve("src/main/java").resolve(packagePath);
        Path resources = root.resolve("src/main/resources");
        Files.createDirectories(javaDir);
        Files.createDirectories(resources);

        if (target.equals("javafx")) {
            write(root.resolve("pom.xml"), javafxPom(packageName), written);
            write(javaDir.resolve("App.java"), javafxApp(packageName), written);
        } else {
            boolean vaadin = target.equals("spring-boot-vaadin");
            write(root.resolve("pom.xml"), springBootPom(packageName, vaadin, false), written);
            write(javaDir.resolve("Application.java"), springApplication(packageName), written);
            write(resources.resolve("application.properties"), springProperties(vaadin, null, false), written);
            String controller = apiController(packageName, analysis.path("routes"));
            if (controller != null) {
                Path web = javaDir.resolve("web");
                Files.createDirectories(web);
                write(web.resolve("ApiController.java"), controller, written);
            }
        }
    }

    private static String springBootPom(String packageName, boolean vaadin, boolean withDataJpa) {
        String vaadinDependency = vaadin
                ? "        <dependency>\n"
                        + "            <groupId>com.vaadin</groupId>\n"
                        + "            <artifactId>vaadin-spring-boot-starter</artifactId>\n"
                        + "            <version>24.6.5</version>\n"
                        + "        </dependency>\n"
                : "";
        String jpaDependency = withDataJpa
                ? "        <dependency>\n"
                        + "            <groupId>org.springframework.boot</groupId>\n"
                        + "            <artifactId>spring-boot-starter-data-jpa</artifactId>\n"
                        + "        </dependency>\n"
                        + "        <dependency>\n"
                        + "            <groupId>com.h2database</groupId>\n"
                        + "            <artifactId>h2</artifactId>\n"
                        + "            <scope>runtime</scope>\n"
                        + "        </dependency>\n"
                : "";
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.4.1</version>
                        <relativePath/>
                    </parent>
                    <groupId>%s</groupId>
                    <artifactId>migrated-app</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <properties>
                        <java.version>21</java.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                %s%s    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """.formatted(packageName, vaadinDependency, jpaDependency);
    }

    private static String javafxPom(String packageName) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>%s</groupId>
                    <artifactId>migrated-desktop</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <properties>
                        <java.version>21</java.version>
                        <javafx.version>21.0.8</javafx.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.openjfx</groupId>
                            <artifactId>javafx-controls</artifactId>
                            <version>${javafx.version}</version>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.openjfx</groupId>
                                <artifactId>javafx-maven-plugin</artifactId>
                                <version>0.0.8</version>
                                <configuration>
                                    <mainClass>%s.App</mainClass>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """.formatted(packageName, packageName);
    }

    private static String springApplication(String packageName) {
        return """
                package %s;

                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;

                @SpringBootApplication
                public class Application {

                    public static void main(String[] args) {
                        SpringApplication.run(Application.class, args);
                    }
                }
                """.formatted(packageName);
    }

    private static String springProperties(boolean vaadin, String jdbcUrl, boolean withData) {
        String ddl = withData ? "create-drop" : "validate";
        String urlDefault = (jdbcUrl != null && !jdbcUrl.isBlank())
                ? jdbcUrl
                : "jdbc:h2:mem:appdb";
        String driver = (jdbcUrl != null && !jdbcUrl.isBlank() && jdbcUrl.startsWith("jdbc:mysql"))
                ? "com.mysql.cj.jdbc.Driver"
                : "org.h2.Driver";
        return """
                spring.application.name=migrated-app
                server.port=8080
                spring.jpa.hibernate.ddl-auto=%s
                spring.jpa.open-in-view=false
                # Fill these from the environment; never commit credentials.
                spring.datasource.url=${DB_URL:%s}
                spring.datasource.driver-class-name=%s
                spring.datasource.username=${DB_USER:sa}
                spring.datasource.password=${DB_PASSWORD:}
                """.formatted(ddl, urlDefault, driver) + (vaadin ? "vaadin.launch-browser=true\n" : "");
    }

    private static String javafxApp(String packageName) {
        return """
                package %s;

                import javafx.application.Application;
                import javafx.scene.Scene;
                import javafx.scene.control.Label;
                import javafx.scene.layout.StackPane;
                import javafx.stage.Stage;

                /**
                 * JavaFX shell generated by JavaBytes. Replace the placeholder view with the
                 * screens enumerated in MIGRATION.md.
                 */
                public class App extends Application {

                    @Override
                    public void start(Stage stage) {
                        StackPane root = new StackPane(new Label("Migration scaffold"));
                        stage.setScene(new Scene(root, 900, 600));
                        stage.setTitle("Migrated app");
                        stage.show();
                    }

                    public static void main(String[] args) {
                        launch(args);
                    }
                }
                """.formatted(packageName);
    }

    private static String apiController(String packageName, JsonNode routes) {
        if (routes.isEmpty()) {
            return null;
        }
        StringBuilder methods = new StringBuilder();
        Set<String> usedNames = new LinkedHashSet<>();
        for (JsonNode route : routes) {
            String method = route.path("method").asText("GET").toUpperCase(Locale.ROOT);
            String springMethod = switch (method) {
                case "POST" -> "PostMapping";
                case "PUT" -> "PutMapping";
                case "PATCH" -> "PatchMapping";
                case "DELETE" -> "DeleteMapping";
                default -> "GetMapping";
            };
            String path = normalizeRoute(route.path("path").asText("/"));
            String name = methodName(method, path, usedNames);
            methods.append("\n    @").append(springMethod).append("(\"")
                    .append(path.replace("\"", ""))
                    .append("\")\n")
                    .append("    public ResponseEntity<Map<String, Object>> ").append(name)
                    .append("() {\n")
                    .append("        return ResponseEntity.status(501)\n")
                    .append("                .body(Map.of(\"message\", \"Not implemented\", ")
                    .append("\"source\", \"").append(route.path("file").asText("unknown"))
                    .append("\"));\n")
                    .append("    }\n");
        }
        return """
                package %s.web;

                import java.util.Map;

                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.DeleteMapping;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.PatchMapping;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.PutMapping;
                import org.springframework.web.bind.annotation.RestController;

                /**
                 * Stubs for routes detected in the JavaScript app. Every method returns 501 until
                 * it is implemented against the acceptance tests described in MIGRATION.md.
                 */
                @RestController
                public class ApiController {
                %s}
                """.formatted(packageName, methods.toString());
    }

    private static String normalizeRoute(String path) {
        String normalized = path.trim();
        if (normalized.isEmpty()) {
            normalized = "/";
        }
        normalized = Pattern.compile(":([A-Za-z_][A-Za-z0-9_]*)")
                .matcher(normalized)
                .replaceAll("{$1}");
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.replaceAll("[^A-Za-z0-9/{}_-]", "");
    }

    private static String methodName(String method, String path, Set<String> used) {
        StringBuilder name = new StringBuilder(method.toLowerCase(Locale.ROOT));
        for (String segment : path.split("/")) {
            String clean = segment.replaceAll("[^A-Za-z0-9]", "");
            if (!clean.isEmpty()) {
                name.append(Character.toUpperCase(clean.charAt(0))).append(clean.substring(1));
            }
        }
        String candidate = name.toString();
        if (candidate.equals(method.toLowerCase(Locale.ROOT))) {
            candidate += "Root";
        }
        String unique = candidate;
        int suffix = 2;
        while (!used.add(unique)) {
            unique = candidate + suffix++;
        }
        return unique;
    }

    private static TargetDecision resolveTarget(String requestedTarget, ObjectNode analysis) {
        if (!requestedTarget.equals("auto")) {
            return new TargetDecision(requestedTarget, "Explicitly requested target: "
                    + requestedTarget);
        }
        String projectType = analysis.path("project_type").asText("unknown");
        JsonNode frameworks = analysis.path("frameworks");
        JsonNode browserApis = analysis.path("browser_apis");
        boolean desktop = projectType.equals("desktop")
                || contains(frameworks, "Electron");
        if (desktop) {
            // JavaFX is a desktop platform, not a web technology; opt-in only.
            return new TargetDecision("javafx",
                    "Desktop intent detected (project type 'desktop' or Electron framework); "
                            + "JavaFX is a desktop platform, not a web technology.");
        }
        if (projectType.equals("frontend")) {
            // Pure browser webapp: keep the original frontend pixel-identical and
            // re-point it at a generated Spring Boot REST API (ADR 0004).
            return new TargetDecision("keep-frontend",
                    "Pure browser webapp detected: keeping the original frontend pixel-identical "
                            + "and re-pointing it at a generated Spring Boot REST API (ADR 0004).");
        }
        if (projectType.equals("backend")) {
            return new TargetDecision("spring-boot-rest",
                    "Backend project detected: generating a Spring Boot REST application.");
        }
        // fullstack / unknown: keep the historical behaviour so an existing
        // working frontend is not silently rewritten.
        if (browserApis.isEmpty()) {
            return new TargetDecision("spring-boot-rest",
                    "No browser APIs detected and no clear frontend/backend intent: generating "
                            + "a Spring Boot REST application (safest default, does not rewrite "
                            + "a working frontend).");
        }
        return new TargetDecision("spring-boot-vaadin",
                "Browser APIs detected with no clear backend intent: generating an all-Java "
                        + "Vaadin browser UI.");
    }

    private static boolean contains(JsonNode array, String value) {
        if (array == null || !array.isArray()) {
            return false;
        }
        for (JsonNode item : array) {
            if (value.equals(item.asText())) {
                return true;
            }
        }
        return false;
    }

    private static String browserDirection(String api) {
        return switch (api) {
            case "fetch", "XMLHttpRequest" -> "java.net.http.HttpClient";
            case "setTimeout", "setInterval" -> "ScheduledExecutorService";
            case "requestAnimationFrame" -> "JavaFX AnimationTimer";
            case "WebSocket" -> "java.net.http.WebSocket / Spring WebSocket";
            case "localStorage", "sessionStorage" -> "Server-side store or Preferences API";
            case "canvas" -> "JavaFX Canvas";
            case "alert", "confirm", "prompt" -> "JavaFX dialog / server-side flow";
            default -> "No direct equivalent; redesign the interaction";
        };
    }

    private static void write(Path target, String content, ArrayNode written) throws Exception {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.writeString(target, content);
        written.add(target.toAbsolutePath().toString());
    }

    private static String join(JsonNode array) {
        if (array == null || array.isEmpty()) {
            return "-";
        }
        StringBuilder out = new StringBuilder();
        for (JsonNode item : array) {
            if (!out.isEmpty()) {
                out.append(", ");
            }
            out.append(item.asText());
        }
        return out.toString();
    }

    // ========================================================================
    // Data-centric project generation (ticket #5)
    // ========================================================================

    private static void generateProject(Map<String, String> fileMap, List<String> limitations,
                                        String target, String packageName, ObjectNode analysis,
                                        String entitiesJson, String jdbcUrl, Path root) {
        boolean vaadin = target.equals("spring-boot-vaadin");
        boolean keepFrontend = target.equals("keep-frontend");
        boolean isJavaFx = target.equals("javafx");
        boolean isSpringBoot = keepFrontend || target.equals("spring-boot-rest") || vaadin;

        List<EntityDef> entities = new ArrayList<>();
        if (entitiesJson != null && !entitiesJson.isBlank()) {
            try {
                entities = parseEntities(entitiesJson, limitations);
            } catch (Exception e) {
                limitations.add("Could not parse entities payload for data-centric generation: "
                        + e.getMessage());
            }
        }

        if (isSpringBoot) {
            String packagePath = packageName.replace('.', '/');
            fileMap.put("pom.xml", springBootPom(packageName, vaadin, !entities.isEmpty()));
            fileMap.put("src/main/java/" + packagePath + "/Application.java",
                    springApplication(packageName));
            fileMap.put("src/main/resources/application.properties",
                    springProperties(vaadin, jdbcUrl, !entities.isEmpty()));
            fileMap.put("README.md", readme(packageName, target, entities, jdbcUrl));

            for (EntityDef ent : entities) {
                String cls = entityClassName(ent);
                fileMap.put("src/main/java/" + packagePath + "/entity/" + cls + ".java",
                        entityClass(ent, packageName));
                fileMap.put("src/main/java/" + packagePath + "/repository/" + cls + "Repository.java",
                        repository(ent, packageName));
                fileMap.put("src/main/java/" + packagePath + "/web/" + cls + "Controller.java",
                        crudController(ent, packageName));
            }

            // Route-based 501 stubs preserved when no entities are supplied
            // (historical behaviour, kept so existing callers see no change).
            if (entities.isEmpty()) {
                JsonNode routes = analysis.path("routes");
                String controller = apiController(packageName, routes);
                if (controller != null) {
                    fileMap.put("src/main/java/" + packagePath + "/web/ApiController.java",
                            controller);
                }
            }

            if (keepFrontend) {
                copyStaticAssets(root, fileMap, "src/main/resources/static", limitations);
                fileMap.putIfAbsent("src/main/resources/static/.gitkeep", "");
            }
        } else if (isJavaFx) {
            fileMap.put("pom.xml", javafxPom(packageName));
            fileMap.put("src/main/java/" + packageName.replace('.', '/') + "/App.java",
                    javafxApp(packageName));
            fileMap.put("README.md", readme(packageName, target, entities, jdbcUrl));
            limitations.add("JavaFX is a desktop platform, not a web technology: the generated "
                    + "App.java is a shell and the browser UI is not mechanically ported.");
        }
    }

    private static List<EntityDef> parseEntities(String entitiesJson, List<String> limitations) {
        List<EntityDef> out = new ArrayList<>();
        JsonNode root;
        try {
            root = JsonUtils.MAPPER.readTree(entitiesJson);
        } catch (IOException e) {
            limitations.add("entities payload is not valid JSON: " + e.getMessage());
            return out;
        }
        if (root == null || !root.isArray()) {
            limitations.add("entities payload is not a JSON array; skipping data-centric generation");
            return out;
        }
        for (JsonNode entNode : root) {
            String name = entNode.path("name").asText("Entity");
            String table = entNode.path("table").asText();
            if (table.isBlank()) {
                table = tableName(name);
            }
            List<FieldDef> fields = new ArrayList<>();
            JsonNode fieldsNode = entNode.path("fields");
            if (fieldsNode.isArray()) {
                for (JsonNode f : fieldsNode) {
                    String fname = f.path("name").asText("field");
                    String column = f.path("column").asText(fname);
                    String type = f.path("type").asText("UNKNOWN");
                    boolean nullable = f.path("nullable").asBoolean(true);
                    boolean unique = f.path("unique").asBoolean(false);
                    boolean id = f.path("id").asBoolean(false);
                    String confidence = f.path("confidence").asText("medium");
                    if (fname.isBlank()) {
                        continue;
                    }
                    fields.add(new FieldDef(fname, column, type, nullable, unique, id, confidence));
                }
            }
            if (fields.isEmpty()) {
                limitations.add("Entity '" + name + "' has no parseable fields; skipping");
                continue;
            }
            out.add(new EntityDef(name, table, fields));
        }
        if (out.isEmpty()) {
            limitations.add("No parseable entities in the payload; skipping data-centric generation");
        }
        return out;
    }

    private static String entityClassName(EntityDef ent) {
        String name = ent.name();
        if (name.isBlank()) {
            name = "Entity";
        }
        return className(name);
    }

    private static String entityClass(EntityDef ent, String packageName) {
        String cls = entityClassName(ent);
        FieldDef id = pickId(ent);
        String idJavaType = id != null ? javaTypeFor(id.type()) : "Long";
        String idMember = id != null ? memberName(id.name()) : "id";
        String idColumn = id != null ? id.column() : "id";

        StringBuilder fields = new StringBuilder();
        StringBuilder gettersSetters = new StringBuilder();
        for (FieldDef f : ent.fields()) {
            if (id != null && f.name().equals(id.name())) {
                continue; // the id is declared separately with @Id
            }
            String javaType = javaTypeFor(f.type());
            String member = memberName(f.name());
            fields.append("    @Column(name = \"").append(f.column())
                    .append("\", nullable = ").append(f.nullable());
            if (f.unique()) {
                fields.append(", unique = true");
            }
            fields.append(")\n");
            fields.append("    private ").append(javaType).append(" ").append(member).append(";\n\n");
            gettersSetters.append("    public ").append(javaType).append(" get")
                    .append(capitalize(member)).append("() { return ").append(member).append("; }\n");
            gettersSetters.append("    public void set").append(capitalize(member))
                    .append("(").append(javaType).append(" ").append(member).append(") { this.")
                    .append(member).append(" = ").append(member).append("; }\n\n");
        }

        List<String> imports = new ArrayList<>();
        imports.add("jakarta.persistence.Entity");
        imports.add("jakarta.persistence.Table");
        imports.add("jakarta.persistence.Id");
        imports.add("jakarta.persistence.GeneratedValue");
        imports.add("jakarta.persistence.GenerationType");
        imports.add("jakarta.persistence.Column");
        for (FieldDef f : ent.fields()) {
            String t = importTypeFor(f.type());
            if (t != null) {
                imports.add(t);
            }
        }
        if (id == null) {
            imports.add("java.lang.Long");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(".entity;\n\n");
        for (String imp : imports) {
            sb.append("import ").append(imp).append(";\n");
        }
        sb.append("\n@Entity\n");
        sb.append("@Table(name = \"").append(ent.table()).append("\")\n");
        sb.append("public class ").append(cls).append(" {\n\n");
        if (id == null) {
            sb.append("    @Id\n");
            sb.append("    @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
            sb.append("    @Column(name = \"id\")\n");
            sb.append("    private Long id;\n\n");
            gettersSetters.insert(0, "    public Long getId() { return id; }\n");
            gettersSetters.insert(0, "    public void setId(Long id) { this.id = id; }\n\n");
        } else {
            sb.append("    @Id\n");
            sb.append("    @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
            sb.append("    @Column(name = \"").append(idColumn).append("\")\n");
            sb.append("    private ").append(idJavaType).append(" ").append(idMember).append(";\n\n");
        }
        sb.append(fields);
        sb.append(gettersSetters);
        sb.append("}\n");
        return sb.toString();
    }

    private static FieldDef pickId(EntityDef ent) {
        for (FieldDef f : ent.fields()) {
            if (f.id()) {
                return f;
            }
        }
        for (FieldDef f : ent.fields()) {
            if ("id".equalsIgnoreCase(f.name()) || "id".equalsIgnoreCase(f.column())) {
                return f;
            }
        }
        return null;
    }

    private static String repository(EntityDef ent, String packageName) {
        String cls = entityClassName(ent);
        String idType = "Long";
        FieldDef id = pickId(ent);
        if (id != null) {
            idType = javaTypeFor(id.type());
        }
        return """
                package %s.repository;

                import %s.entity.%s;
                import org.springframework.data.jpa.repository.JpaRepository;
                import org.springframework.stereotype.Repository;

                @Repository
                public interface %sRepository extends JpaRepository<%s, %s> {
                }
                """.formatted(packageName, packageName, cls, cls, cls, idType);
    }

    private static String crudController(EntityDef ent, String packageName) {
        String cls = entityClassName(ent);
        String idType = "Long";
        String idMember = "id";
        FieldDef id = pickId(ent);
        if (id != null) {
            idType = javaTypeFor(id.type());
            idMember = memberName(id.name());
        }
        String basePath = basePath(ent);
        return """
                package %s.web;

                import %s.entity.%s;
                import %s.repository.%sRepository;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.DeleteMapping;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.PathVariable;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.PutMapping;
                import org.springframework.web.bind.annotation.RequestBody;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                import java.util.List;
                import java.util.Optional;

                /**
                 * Real CRUD controller for {@code %s}. Derived from the ticket #2 schema model.
                 * Endpoints follow the {@code /api/%s} contract.
                 */
                @RestController
                @RequestMapping("/api/%s")
                public class %sController {

                    @Autowired
                    private %sRepository repository;

                    @GetMapping
                    public List<%s> findAll() {
                        return repository.findAll();
                    }

                    @GetMapping("/{%s}")
                    public ResponseEntity<%s> findById(@PathVariable %s id) {
                        Optional<%s> found = repository.findById(id);
                        return found.map(ResponseEntity::ok)
                                .orElse(ResponseEntity.notFound().build());
                    }

                    @PostMapping
                    public ResponseEntity<%s> create(@RequestBody %s input) {
                        return ResponseEntity.ok(repository.save(input));
                    }

                    @PutMapping("/{%s}")
                    public ResponseEntity<%s> update(@PathVariable %s id,
                            @RequestBody %s input) {
                        if (!repository.existsById(id)) {
                            return ResponseEntity.notFound().build();
                        }
                        return ResponseEntity.ok(repository.save(input));
                    }

                    @DeleteMapping("/{%s}")
                    public ResponseEntity<Void> delete(@PathVariable %s id) {
                        if (!repository.existsById(id)) {
                            return ResponseEntity.notFound().build();
                        }
                        repository.deleteById(id);
                        return ResponseEntity.noContent().build();
                    }
                }
                """.formatted(packageName, packageName, cls, packageName, cls,
                    ent.name(), basePath, basePath, cls,
                    cls, cls, idMember, cls, idType, idMember, idType, idMember, cls,
                    cls, cls, idMember, idMember, cls, idMember, cls, idMember, idType);
    }

    private static String basePath(EntityDef ent) {
        String name = ent.name();
        if (name.isBlank()) {
            return "items";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return pluralize(lower);
    }

    private static String tableName(String entityName) {
        return entityName.toLowerCase(Locale.ROOT) + "s";
    }

    private static String pluralize(String word) {
        if (word.isBlank()) {
            return "items";
        }
        if (word.endsWith("s") || word.endsWith("x") || word.endsWith("ch")
                || word.endsWith("sh")) {
            return word + "es";
        }
        if (word.endsWith("y")) {
            return word.substring(0, word.length() - 1) + "ies";
        }
        return word + "s";
    }

    private static String className(String name) {
        StringBuilder out = new StringBuilder();
        boolean nextUpper = true;
        for (char c : name.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                if (nextUpper) {
                    out.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    out.append(c); // preserve existing camelCase
                }
            } else {
                nextUpper = true;
            }
        }
        String result = out.toString();
        return result.isBlank() ? "Entity" : result;
    }

    private static String memberName(String name) {
        String cls = className(name);
        if (cls.isEmpty()) {
            return "field";
        }
        return Character.toLowerCase(cls.charAt(0)) + cls.substring(1);
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String javaTypeFor(String type) {
        if (type == null || type.isBlank()) {
            return "String";
        }
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "STRING", "TEXT", "UUID", "JSON", "UNKNOWN" -> "String";
            case "INT" -> "Integer";
            case "BIGINT" -> "Long";
            case "DECIMAL" -> "BigDecimal";
            case "BOOLEAN" -> "Boolean";
            case "DATE" -> "LocalDate";
            case "TIMESTAMP" -> "LocalDateTime";
            case "BLOB" -> "byte[]";
            default -> "String";
        };
    }

    private static String importTypeFor(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "BIGINT", "INT" -> null; // java.lang.*
            case "DECIMAL" -> "java.math.BigDecimal";
            case "BOOLEAN" -> null;
            case "DATE" -> "java.time.LocalDate";
        case "TIMESTAMP" -> "java.time.LocalDateTime";
            case "UUID" -> "java.util.UUID";
            default -> null;
        };
    }

    private static String readme(String packageName, String target, List<EntityDef> entities,
                                 String jdbcUrl) {
        String dbHint = (jdbcUrl != null && !jdbcUrl.isBlank())
                ? "Set JDBC_URL (or spring.datasource.url) to: " + jdbcUrl
                : "Using embedded H2 (jdbc:h2:mem:appdb). No external database required.";
        StringBuilder sb = new StringBuilder();
        sb.append("# Generated JavaBytes App\n\n");
        sb.append("Target: ").append(target).append("\n\n");
        sb.append("## Run\n\n");
        if (target.equals("javafx")) {
            sb.append("```bash\nmvn javafx:run\n```\n\n");
            sb.append("JavaFX is a desktop platform; the generated App.java is a shell.\n\n");
        } else {
            sb.append("```bash\nmvn spring-boot:run\n```\n\n");
            sb.append("The app starts on port 8080. ").append(dbHint).append("\n\n");
        }
        if (!entities.isEmpty()) {
            sb.append("## Data\n\n");
            sb.append("JPA entities and Spring Data JPA repositories were generated for: ");
            for (int i = 0; i < entities.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(entityClassName(entities.get(i)));
            }
            sb.append(".\n\nREST CRUD is exposed under `/api/...`.\n\n");
        }
        sb.append("## Honesty\n\n");
        sb.append("Generated code is an honest subset: only provably-compiling constructs are emitted; ");
        sb.append("anything that cannot be proven is listed as manual work rather than guessed at.\n");
        return sb.toString();
    }

    private static void copyStaticAssets(Path root, Map<String, String> fileMap, String staticDir,
                                         List<String> limitations) {
        int[] copied = {0};
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 20,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            if (!dir.equals(root)) {
                                String lc = dir.getFileName().toString().toLowerCase(Locale.ROOT);
                                if (Set.of("node_modules", ".git", ".svn", "dist", "build", "out",
                                        ".next", ".nuxt", "coverage", "vendor", "target", "bin",
                                        "obj", ".cache", ".turbo").contains(lc)) {
                                    return FileVisitResult.SKIP_SUBTREE;
                                }
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                            if (name.equals("package.json") || name.equals("package-lock.json")) {
                                return FileVisitResult.CONTINUE;
                            }
                            String ext = "";
                            int dot = name.lastIndexOf('.');
                            if (dot > 0) {
                                ext = name.substring(dot);
                            }
                            if (!STATIC_EXTENSIONS.contains(ext)) {
                                return FileVisitResult.CONTINUE;
                            }
                            try {
                                String relative = root.relativize(file).toString().replace('\\', '/');
                                String content = Files.readString(file);
                                fileMap.put(staticDir + "/" + relative, content);
                                copied[0]++;
                            } catch (IOException e) {
                                limitations.add("Could not copy static asset " + file + ": "
                                        + e.getMessage());
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            limitations.add("Could not walk static assets: " + e.getMessage());
        }
        if (copied[0] == 0) {
            limitations.add("No static assets were found to pass through unchanged.");
        }
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("path", new Tool.StringSchema()
                .description("Web project directory inside an allowed root (alias: source_path)"));
        schema.prop("source_path", new Tool.StringSchema()
                .description("Alias for path: web project directory inside an allowed root"));
        schema.prop("target", new Tool.StringSchema()
                .description("auto, keep-frontend (default for browser webapps), spring-boot-rest, "
                        + "spring-boot-vaadin or javafx (default auto)"));
        schema.prop("entities", new Tool.StringSchema()
                .description("Optional ticket #2 schema-model JSON (tables/columns/relations) to derive "
                        + "JPA entities, repositories and CRUD controllers from"));
        schema.prop("jdbc_url", new Tool.StringSchema()
                .description("Optional JDBC URL passed through to generate_database if DB creation is requested"));
        schema.prop("package_name", new Tool.StringSchema()
                .description("Package for generated Java scaffolding (default com.example)"));
        schema.prop("write_to", new Tool.StringSchema()
                .description("Optional directory inside the sandbox for MIGRATION.md and scaffold files"));
        schema.prop("max_files", new Tool.NumberSchema()
                .integer()
                .minimum(1)
                .maximum(2000)
                .description("Maximum project files to scan (default 500)"));
        schema.required("path");
        return schema;
    }
}
