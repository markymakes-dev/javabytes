package com.javabytes.mcp.tools.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.utils.JsonUtils;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static inventory of a JavaScript/TypeScript source tree.
 *
 * <p>This is intentionally an <em>analyzer</em>, not a transpiler. It reports
 * what a project uses, which parts have no mechanical Java equivalent, and a
 * realistic migration path. It never claims a browser app can be converted
 * automatically.</p>
 */
public final class JavaScriptAnalyzer {

    private static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    private static final int MAX_DEPTH = 20;

    private static final Set<String> WEB_EXTENSIONS = Set.of(
            ".js", ".mjs", ".cjs", ".jsx", ".ts", ".tsx", ".vue", ".svelte");

    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            "node_modules", ".git", ".svn", "dist", "build", "out", ".next", ".nuxt",
            "coverage", "vendor", "target", "bin", "obj", ".cache", ".turbo");

    private static final Pattern IMPORT_FROM = Pattern.compile(
            "\\bimport\\s+(?:[^'\";]+?\\s+from\\s+)?['\"]([^'\"]+)['\"]");
    private static final Pattern REQUIRE = Pattern.compile(
            "\\brequire\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
    private static final Pattern DYNAMIC_IMPORT = Pattern.compile(
            "\\bimport\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");

    private static final Pattern EXPRESS_ROUTE = Pattern.compile(
            "\\b(?:app|router|server)\\.(get|post|put|patch|delete|all)\\s*\\(\\s*['\"`]([^'\"`]+)['\"`]");
    private static final Pattern REACT_ROUTE = Pattern.compile(
            "<Route\\b[^>]*\\bpath\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern VUE_ROUTE = Pattern.compile(
            "\\bpath\\s*:\\s*[\"']([^\"']+)[\"']");

    private static final Pattern FUNCTION_COMPONENT = Pattern.compile(
            "(?:export\\s+)?(?:default\\s+)?function\\s+([A-Z][A-Za-z0-9_]*)");
    private static final Pattern CONST_COMPONENT = Pattern.compile(
            "(?:export\\s+)?const\\s+([A-Z][A-Za-z0-9_]*)\\s*=");
    private static final Pattern TYPE_DECLARATION = Pattern.compile(
            "(?:export\\s+)?(?:interface|type|enum)\\s+([A-Za-z_][A-Za-z0-9_]*)");

    private static final Pattern JSX = Pattern.compile("<[A-Z][A-Za-z0-9]*(?:\\s|/?>)");
    private static final Pattern EVAL = Pattern.compile("\\b(?:eval\\s*\\(|new\\s+Function\\s*\\()");
    private static final Pattern PROTOTYPE = Pattern.compile("\\.prototype\\s*[.=]");
    private static final Pattern ASYNC = Pattern.compile("\\b(?:async|await)\\b|\\bPromise\\b");

    private static final Map<String, String> JAVA_MAPPINGS = buildMappings();

    private static final Map<String, String> FRAMEWORK_PACKAGES = Map.ofEntries(
            Map.entry("react", "React"),
            Map.entry("react-dom", "React"),
            Map.entry("next", "Next.js"),
            Map.entry("vue", "Vue"),
            Map.entry("nuxt", "Nuxt"),
            Map.entry("@angular/core", "Angular"),
            Map.entry("svelte", "Svelte"),
            Map.entry("solid-js", "Solid"),
            Map.entry("express", "Express"),
            Map.entry("fastify", "Fastify"),
            Map.entry("koa", "Koa"),
            Map.entry("@nestjs/core", "NestJS"),
            Map.entry("electron", "Electron"));

    private static final Map<String, String> BROWSER_APIS = Map.ofEntries(
            Map.entry("document", "Browser DOM (no direct Java equivalent)"),
            Map.entry("window", "Browser window (no direct Java equivalent)"),
            Map.entry("localStorage", "Browser storage; use a server-side store or Preferences API"),
            Map.entry("sessionStorage", "Browser storage; use a server-side session"),
            Map.entry("fetch", "java.net.http.HttpClient"),
            Map.entry("XMLHttpRequest", "java.net.http.HttpClient"),
            Map.entry("navigator", "No direct equivalent; implement the capability server-side"),
            Map.entry("alert", "JavaFX Alert / server-side validation message"),
            Map.entry("confirm", "JavaFX Alert / server-side confirmation flow"),
            Map.entry("prompt", "JavaFX TextInputDialog"),
            Map.entry("setTimeout", "ScheduledExecutorService"),
            Map.entry("setInterval", "ScheduledExecutorService.scheduleAtFixedRate"),
            Map.entry("requestAnimationFrame", "JavaFX AnimationTimer"),
            Map.entry("addEventListener", "JavaFX event handlers / DOM replacement"),
            Map.entry("WebSocket", "java.net.http.WebSocket / Spring WebSocket"),
            Map.entry("indexedDB", "Embedded database (SQLite/H2) or server storage"),
            Map.entry("history", "Vaadin routing / server-side navigation"),
            Map.entry("location", "Vaadin routing / server-side navigation"),
            Map.entry("canvas", "JavaFX Canvas"),
            Map.entry("MutationObserver", "No direct equivalent; use framework state"),
            Map.entry("CustomEvent", "JavaFX event bus / Spring ApplicationEvent"));

    private static final Map<String, String> NODE_APIS = Map.ofEntries(
            Map.entry("fs", "java.nio.file"),
            Map.entry("path", "java.nio.file.Path"),
            Map.entry("http", "java.net.http.HttpServer / Spring Web"),
            Map.entry("https", "java.net.http / Spring Web"),
            Map.entry("os", "System.getProperty / OperatingSystemMXBean"),
            Map.entry("child_process", "ProcessBuilder"),
            Map.entry("crypto", "java.security / javax.crypto"),
            Map.entry("process", "System.getenv / System.getProperty"),
            Map.entry("stream", "java.util.stream"),
            Map.entry("events", "java.util.concurrent / Spring ApplicationEvent"),
            Map.entry("url", "java.net.URI"),
            Map.entry("util", "java.util / java.util.concurrent"),
            Map.entry("zlib", "java.util.zip"),
            Map.entry("net", "java.net"),
            Map.entry("dns", "java.net.InetAddress"),
            Map.entry("cluster", "Spring Boot scaling / containers"),
            Map.entry("worker_threads", "java.util.concurrent.ExecutorService"));

    private JavaScriptAnalyzer() {
    }

    /** Analyze one source string. */
    public static ObjectNode analyzeSource(String source, String displayName) {
        String name = displayName == null ? "input.js" : displayName;
        String lower = name.toLowerCase(Locale.ROOT);
        boolean typescript = lower.endsWith(".ts") || lower.endsWith(".tsx");
        boolean jsx = lower.endsWith(".jsx") || lower.endsWith(".tsx") || JSX.matcher(source).find();

        Set<String> imports = new TreeSet<>();
        for (Pattern pattern : List.of(IMPORT_FROM, REQUIRE, DYNAMIC_IMPORT)) {
            Matcher matcher = pattern.matcher(source);
            while (matcher.find()) {
                imports.add(matcher.group(1));
            }
        }

        Set<String> packages = new TreeSet<>();
        for (String specifier : imports) {
            packages.add(packageName(specifier));
        }
        packages.remove("");

        Set<String> frameworks = new TreeSet<>();
        for (String pkg : packages) {
            String framework = FRAMEWORK_PACKAGES.get(pkg);
            if (framework != null) {
                frameworks.add(framework);
            }
        }

        Set<String> browserApis = scanTokens(source, BROWSER_APIS.keySet());
        Set<String> nodeApis = new TreeSet<>();
        for (String pkg : packages) {
            if (NODE_APIS.containsKey(pkg)) {
                nodeApis.add(pkg);
            }
        }

        ArrayNode routes = JsonUtils.MAPPER.createArrayNode();
        addRoutes(routes, source, EXPRESS_ROUTE, true);
        addRoutes(routes, source, REACT_ROUTE, false);
        addRoutes(routes, source, VUE_ROUTE, false);

        Set<String> components = new TreeSet<>();
        collectMatches(source, FUNCTION_COMPONENT, components);
        collectMatches(source, CONST_COMPONENT, components);
        if (lower.endsWith(".vue") || lower.endsWith(".svelte")) {
            components.add(fileStem(name));
        }

        Set<String> types = new TreeSet<>();
        collectMatches(source, TYPE_DECLARATION, types);

        Set<String> unsupported = new LinkedHashSet<>();
        if (jsx) {
            unsupported.add("JSX component syntax");
        }
        if (typescript) {
            unsupported.add("TypeScript type annotations");
        }
        if (!browserApis.isEmpty()) {
            unsupported.add("Browser-only APIs: " + String.join(", ", browserApis));
        }
        if (EVAL.matcher(source).find()) {
            unsupported.add("Dynamic evaluation (eval/new Function)");
        }
        if (PROTOTYPE.matcher(source).find()) {
            unsupported.add("Prototype mutation");
        }
        if (source.contains("...")) {
            unsupported.add("Spread/rest syntax (needs explicit Java collection handling)");
        }

        ObjectNode out = JsonUtils.MAPPER.createObjectNode();
        out.put("file", name);
        out.put("lines", lineCount(source));
        out.put("language", typescript ? "typescript" : "javascript");
        out.put("typescript", typescript);
        out.put("jsx", jsx);
        out.put("async", ASYNC.matcher(source).find());
        out.set("imports", array(imports));
        out.set("packages", array(packages));
        out.set("frameworks", array(frameworks));
        out.set("browser_apis", array(browserApis));
        out.set("node_apis", array(nodeApis));
        out.set("routes", routes);
        out.set("components", array(components));
        out.set("types", array(types));
        out.set("unsupported", array(unsupported));
        out.put("complexity", complexity(source, unsupported.size()));
        return out;
    }

    /**
     * Add dependency mappings and a target recommendation to a single-file
     * analysis.
     */
    public static ObjectNode enrichSource(ObjectNode analysis) {
        Set<String> packages = new TreeSet<>();
        analysis.path("packages").forEach(node -> packages.add(node.asText()));
        Set<String> frameworks = new TreeSet<>();
        analysis.path("frameworks").forEach(node -> frameworks.add(node.asText()));
        Set<String> browserApis = new TreeSet<>();
        analysis.path("browser_apis").forEach(node -> browserApis.add(node.asText()));

        ArrayNode mappings = JsonUtils.MAPPER.createArrayNode();
        for (String pkg : packages) {
            String mapped = JAVA_MAPPINGS.get(pkg);
            if (mapped == null) {
                continue;
            }
            ObjectNode mapping = JsonUtils.MAPPER.createObjectNode();
            mapping.put("npm", pkg);
            mapping.put("java", mapped);
            mappings.add(mapping);
        }
        analysis.set("dependency_mapping", mappings);

        String type = projectType(frameworks);
        analysis.put("project_type", type);
        analysis.put("recommended_target", recommendedTarget(type, frameworks, browserApis));
        return analysis;
    }

    /** Analyze every JavaScript/TypeScript file under a project root. */
    public static ObjectNode analyzeProject(Path root, int maxFiles) throws IOException {
        int limit = Math.max(1, Math.min(2000, maxFiles));
        List<Path> collected = new ArrayList<>();
        Files.walkFileTree(root, Set.of(), MAX_DEPTH, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(root) && IGNORED_DIRECTORIES.contains(
                        dir.getFileName().toString().toLowerCase(Locale.ROOT))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!attrs.isRegularFile() || attrs.size() > MAX_FILE_BYTES || !isWebFile(file)) {
                    return FileVisitResult.CONTINUE;
                }
                collected.add(file);
                return collected.size() > limit
                        ? FileVisitResult.TERMINATE
                        : FileVisitResult.CONTINUE;
            }
        });

        List<Path> files = collected;
        boolean truncated = files.size() > limit;
        if (truncated) {
            files = files.subList(0, limit);
        }

        ArrayNode fileSummaries = JsonUtils.MAPPER.createArrayNode();
        Set<String> packages = new TreeSet<>();
        Set<String> frameworks = new TreeSet<>();
        Set<String> browserApis = new TreeSet<>();
        Set<String> nodeApis = new TreeSet<>();
        Set<String> components = new TreeSet<>();
        Set<String> unsupported = new TreeSet<>();
        List<ObjectNode> routeList = new ArrayList<>();
        Set<String> routeKeys = new LinkedHashSet<>();
        int totalLines = 0;
        int jsxFiles = 0;
        int tsFiles = 0;

        for (Path file : files) {
            String relative = root.relativize(file).toString().replace('\\', '/');
            String source;
            try {
                source = Files.readString(file);
            } catch (IOException | OutOfMemoryError e) {
                continue;
            }
            ObjectNode analysis = analyzeSource(source, relative);
            totalLines += analysis.path("lines").asInt();

            for (JsonNode pkg : analysis.path("packages")) packages.add(pkg.asText());
            for (JsonNode framework : analysis.path("frameworks")) frameworks.add(framework.asText());
            for (JsonNode api : analysis.path("browser_apis")) browserApis.add(api.asText());
            for (JsonNode api : analysis.path("node_apis")) nodeApis.add(api.asText());
            for (JsonNode component : analysis.path("components")) components.add(component.asText());
            for (JsonNode item : analysis.path("unsupported")) unsupported.add(item.asText());
            if (analysis.path("jsx").asBoolean()) jsxFiles++;
            if (analysis.path("typescript").asBoolean()) tsFiles++;

            for (JsonNode route : analysis.path("routes")) {
                String key = route.path("method").asText() + " " + route.path("path").asText();
                if (routeKeys.add(key)) {
                    ObjectNode copy = route.deepCopy();
                    copy.put("file", relative);
                    routeList.add(copy);
                }
            }

            ObjectNode summary = JsonUtils.MAPPER.createObjectNode();
            summary.put("path", relative);
            summary.put("lines", analysis.path("lines").asInt());
            summary.put("typescript", analysis.path("typescript").asBoolean());
            summary.put("jsx", analysis.path("jsx").asBoolean());
            summary.set("frameworks", analysis.path("frameworks").deepCopy());
            summary.set("browser_apis", analysis.path("browser_apis").deepCopy());
            fileSummaries.add(summary);
        }

        Map<String, String> packageVersions = readPackageJson(root, packages);

        ObjectNode out = JsonUtils.MAPPER.createObjectNode();
        out.put("root", root.toAbsolutePath().toString());
        out.put("files_analyzed", fileSummaries.size());
        out.put("truncated", truncated);
        out.put("total_lines", totalLines);
        out.put("jsx_files", jsxFiles);
        out.put("typescript_files", tsFiles);
        out.set("packages", array(packages));
        out.set("frameworks", array(frameworks));
        out.set("browser_apis", array(browserApis));
        out.set("node_apis", array(nodeApis));
        out.set("components", array(components));
        out.set("unsupported", array(unsupported));
        ArrayNode routes = JsonUtils.MAPPER.createArrayNode();
        routeList.forEach(routes::add);
        out.set("routes", routes);
        out.set("files", fileSummaries);

        ArrayNode mappings = JsonUtils.MAPPER.createArrayNode();
        for (String pkg : packages) {
            String mapped = JAVA_MAPPINGS.get(pkg);
            if (mapped == null) {
                continue;
            }
            ObjectNode mapping = JsonUtils.MAPPER.createObjectNode();
            mapping.put("npm", pkg);
            mapping.put("java", mapped);
            if (packageVersions.containsKey(pkg)) {
                mapping.put("version", packageVersions.get(pkg));
            }
            mappings.add(mapping);
        }
        out.set("dependency_mapping", mappings);

        String projectType = projectType(frameworks);
        out.put("project_type", projectType);
        out.put("recommended_target", recommendedTarget(projectType, frameworks, browserApis));
        out.set("migration_phases", migrationPhases(projectType, browserApis, jsxFiles));
        out.set("risks", risks(browserApis, unsupported, jsxFiles, tsFiles));
        out.set("coexistence_options", coexistenceOptions());
        return out;
    }

    /** Map npm package names to Java equivalents. */
    public static Map<String, String> javaMappings() {
        return JAVA_MAPPINGS;
    }

    public static boolean isWebFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return WEB_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    public static String packageName(String specifier) {
        String value = specifier == null ? "" : specifier.trim();
        if (value.startsWith("node:")) {
            value = value.substring("node:".length());
        }
        if (value.isEmpty() || value.startsWith(".") || value.startsWith("/")) {
            return "";
        }
        if (value.startsWith("@")) {
            String[] parts = value.split("/");
            return parts.length >= 2 ? parts[0] + "/" + parts[1] : value;
        }
        int slash = value.indexOf('/');
        return slash >= 0 ? value.substring(0, slash) : value;
    }

    private static Set<String> scanTokens(String source, Set<String> tokens) {
        Set<String> found = new TreeSet<>();
        for (String token : tokens) {
            if (Pattern.compile("\\b" + Pattern.quote(token) + "\\b").matcher(source).find()) {
                found.add(token);
            }
        }
        return found;
    }

    private static void collectMatches(String source, Pattern pattern, Set<String> target) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            target.add(matcher.group(1));
        }
    }

    private static void addRoutes(ArrayNode routes, String source, Pattern pattern, boolean hasMethod) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            ObjectNode route = JsonUtils.MAPPER.createObjectNode();
            route.put("method", hasMethod ? matcher.group(1).toUpperCase(Locale.ROOT) : "GET");
            route.put("path", hasMethod ? matcher.group(2) : matcher.group(1));
            routes.add(route);
        }
    }

    private static int lineCount(String source) {
        if (source.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private static String complexity(String source, int unsupportedCount) {
        int lines = lineCount(source);
        if (unsupportedCount >= 3 || (lines > 400 && unsupportedCount > 0)) {
            return "high";
        }
        if (unsupportedCount > 0 || lines > 200) {
            return "medium";
        }
        return "low";
    }

    private static String fileStem(String name) {
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String base = slash >= 0 ? name.substring(slash + 1) : name;
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }

    private static ArrayNode array(Set<String> values) {
        ArrayNode array = JsonUtils.MAPPER.createArrayNode();
        values.forEach(array::add);
        return array;
    }

    private static Map<String, String> readPackageJson(Path root, Set<String> fallbackPackages) {
        Map<String, String> versions = new LinkedHashMap<>();
        Path packageJson = root.resolve("package.json");
        if (!Files.isRegularFile(packageJson)) {
            return versions;
        }
        try {
            JsonNode json = JsonUtils.MAPPER.readTree(Files.readString(packageJson));
            for (String section : List.of("dependencies", "devDependencies", "peerDependencies")) {
                JsonNode deps = json.get(section);
                if (deps != null && deps.isObject()) {
                    deps.fields().forEachRemaining(entry -> {
                        versions.put(entry.getKey(), entry.getValue().asText());
                        fallbackPackages.add(entry.getKey());
                    });
                }
            }
        } catch (Exception e) {
            System.err.println("[javabytes] Cannot read package.json: " + e.getMessage());
        }
        return versions;
    }

    public static String projectType(Set<String> frameworks) {
        boolean frontend = frameworks.stream().anyMatch(f -> Set.of(
                "React", "Vue", "Angular", "Svelte", "Solid", "Next.js", "Nuxt").contains(f));
        boolean backend = frameworks.stream().anyMatch(f -> Set.of(
                "Express", "Fastify", "Koa", "NestJS").contains(f));
        boolean desktop = frameworks.contains("Electron");
        if (desktop) {
            return "desktop";
        }
        if (frontend && backend) {
            return "fullstack";
        }
        if (backend) {
            return "backend";
        }
        if (frontend) {
            return "frontend";
        }
        return "unknown";
    }

    public static String recommendedTarget(String projectType, Set<String> frameworks,
                                            Set<String> browserApis) {
        if (projectType.equals("desktop") || frameworks.contains("Electron")) {
            return "javafx";
        }
        if (projectType.equals("backend")) {
            return "spring-boot-rest";
        }
        if (projectType.equals("frontend") || projectType.equals("fullstack")) {
            return browserApis.isEmpty()
                    ? "spring-boot-rest"
                    : "spring-boot-vaadin";
        }
        return "spring-boot-rest";
    }

    private static ArrayNode migrationPhases(String projectType, Set<String> browserApis, int jsxFiles) {
        ArrayNode phases = JsonUtils.MAPPER.createArrayNode();
        phases.add("1. Freeze the JavaScript app and capture its observable behaviour as tests "
                + "(routes, status codes, validation rules, key user flows).");
        phases.add("2. Model the domain and database in Java first (entities, constraints, "
                + "migration scripts). Do not port UI before the data model is stable.");
        if (projectType.equals("backend") || projectType.equals("fullstack")) {
            phases.add("3. Rebuild HTTP endpoints with Spring Web MVC and keep the same URL "
                    + "contract so clients can switch incrementally.");
        }
        if (projectType.equals("frontend") || projectType.equals("fullstack")) {
            phases.add("3. Port screens to Vaadin (server-side Java UI) or Thymeleaf/HTMX; "
                    + "replace " + (browserApis.isEmpty() ? "framework state" : String.join(", ", browserApis))
                    + " with server-side state and JavaFX/Vaadin components.");
        }
        if (projectType.equals("desktop")) {
            phases.add("3. Port the Electron shell and renderer to JavaFX; replace Node APIs "
                    + "with java.nio/java.net equivalents.");
        }
        phases.add("4. Run both implementations against the same acceptance tests, then cut over "
                + "route by route and delete the old code only after parity is proven.");
        if (jsxFiles > 0) {
            phases.add("Note: " + jsxFiles + " JSX files need manual component design; "
                    + "no mechanical JSX-to-Java conversion is reliable.");
        }
        return phases;
    }

    /**
     * Realistic alternatives to source translation, based on what actually
     * exists in the JVM ecosystem. GraalJS runs JavaScript on the JVM; it does
     * not convert browser code.
     */
    private static ArrayNode coexistenceOptions() {
        ArrayNode options = JsonUtils.MAPPER.createArrayNode();
        options.add("Run server-side JavaScript on GraalJS (GraalVM) behind Spring Boot and "
                + "port routes incrementally; this avoids transpilation entirely.");
        options.add("Keep a REST boundary and rebuild the browser UI in Vaadin, Thymeleaf/HTMX "
                + "or JavaFX; browser/DOM code has no mechanical Java equivalent.");
        options.add("Rhino targets older JavaScript editions and is not a fit for modern JS/TS; "
                + "do not plan a migration around it.");
        options.add("Java-to-JavaScript tools such as JSweet and GWT solve the opposite problem "
                + "and cannot convert JavaScript back to Java.");
        return options;
    }

    private static ArrayNode risks(Set<String> browserApis, Set<String> unsupported,
                                   int jsxFiles, int tsFiles) {
        ArrayNode risks = JsonUtils.MAPPER.createArrayNode();
        if (!browserApis.isEmpty()) {
            risks.add("Browser APIs have no mechanical Java equivalent: "
                    + String.join(", ", browserApis) + ".");
        }
        if (jsxFiles > 0) {
            risks.add(jsxFiles + " files use JSX; components and state management must be "
                    + "redesigned for JavaFX or Vaadin.");
        }
        if (tsFiles > 0) {
            risks.add(tsFiles + " TypeScript files encode types in syntax that Java tooling "
                    + "cannot import automatically; use them as a specification.");
        }
        if (unsupported.contains("Dynamic evaluation (eval/new Function)")) {
            risks.add("Dynamic evaluation cannot be translated; it must be redesigned.");
        }
        risks.add("npm dependency behaviour (transitive versions, lifecycle scripts, native "
                + "addons) is not reproduced by Java libraries with the same name.");
        return risks;
    }

    private static Map<String, String> buildMappings() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("react", "Vaadin or JavaFX views");
        m.put("react-dom", "Vaadin or JavaFX rendering");
        m.put("next", "Spring Boot MVC + Thymeleaf/HTMX");
        m.put("vue", "Vaadin or Thymeleaf templates");
        m.put("nuxt", "Spring Boot MVC + Thymeleaf");
        m.put("@angular/core", "Vaadin or Thymeleaf");
        m.put("svelte", "Thymeleaf/HTMX (manual component port)");
        m.put("solid-js", "Vaadin or JavaFX");
        m.put("express", "Spring Web MVC (@RestController)");
        m.put("fastify", "Spring Web MVC");
        m.put("koa", "Spring Web MVC");
        m.put("@nestjs/core", "Spring Boot");
        m.put("axios", "java.net.http.HttpClient");
        m.put("node-fetch", "java.net.http.HttpClient");
        m.put("lodash", "java.util.stream + Collections");
        m.put("moment", "java.time");
        m.put("dayjs", "java.time");
        m.put("date-fns", "java.time");
        m.put("uuid", "java.util.UUID");
        m.put("dotenv", "Spring Boot application.properties/environment");
        m.put("jsonwebtoken", "Spring Security + Nimbus JOSE");
        m.put("bcrypt", "Spring Security BCryptPasswordEncoder");
        m.put("mongoose", "Spring Data JPA");
        m.put("sequelize", "Spring Data JPA / jOOQ");
        m.put("knex", "jOOQ or Spring Data JPA");
        m.put("pg", "Spring Data JPA or JDBC");
        m.put("mysql2", "Spring Data JPA or JDBC");
        m.put("redis", "Spring Data Redis");
        m.put("socket.io", "Spring WebSocket/STOMP");
        m.put("ws", "Spring WebSocket or java.net.http.WebSocket");
        m.put("jest", "JUnit 5");
        m.put("mocha", "JUnit 5");
        m.put("vitest", "JUnit 5");
        m.put("cypress", "Selenium or Playwright for Java");
        m.put("playwright", "Playwright for Java");
        m.put("webpack", "Maven or Gradle build");
        m.put("vite", "Maven or Gradle build");
        m.put("eslint", "Checkstyle + SpotBugs");
        m.put("prettier", "IDE formatting (no runtime dependency)");
        m.put("typescript", "Java's static type system (manual port)");
        return Map.copyOf(m);
    }
}
