package com.javabytes.mcp.skills.diagnostics;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.utils.JsonUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Shared, evidence-based inspection of a Java project.
 *
 * <p>Dependencies are only reported as missing when the source actually uses
 * them, so a plain library project is never told it needs Spring Boot or an MCP
 * SDK. Diagnostics read files; they never modify the project.</p>
 */
final class ProjectInspector {

    private static final Pattern JAVA_VERSION = Pattern.compile(
            "<(?:java\\.version|maven\\.compiler\\.(?:source|target)|release)>([^<]+)<");
    private static final Pattern SPRING_BOOT_VERSION = Pattern.compile(
            "<(?:spring-boot\\.version|version)>([0-9][^<]*)</(?:spring-boot\\.version|version)>");

    private static final int MAX_SOURCE_FILES = 5000;

    private ProjectInspector() {
    }

    static ObjectNode inspect(Path projectPath) throws IOException {
        ObjectNode result = JsonUtils.MAPPER.createObjectNode();
        result.put("projectPath", projectPath.toAbsolutePath().toString());

        Path pomPath = projectPath.resolve("pom.xml");
        boolean hasPom = Files.isRegularFile(pomPath);
        String pom = hasPom ? Files.readString(pomPath) : "";
        ObjectNode maven = JsonUtils.MAPPER.createObjectNode();
        maven.put("pomFound", hasPom);
        maven.put("hasSpringBoot", contains(pom, "spring-boot"));
        maven.put("hasSpringDataJpa", contains(pom, "spring-boot-starter-data-jpa"));
        maven.put("hasMySqlConnector", contains(pom, "mysql-connector-j"));
        maven.put("hasLegacyMySqlConnector", contains(pom, "mysql-connector-java"));
        maven.put("hasJavaFx", contains(pom, "javafx-controls"));
        maven.put("hasJackson", contains(pom, "jackson-databind"));
        maven.put("hasJUnit", contains(pom, "junit-jupiter") || contains(pom, "spring-boot-starter-test"));
        maven.put("javaVersion", firstMatch(JAVA_VERSION, pom, "not specified"));
        maven.put("springBootVersion", firstMatch(SPRING_BOOT_VERSION, pom, "not specified"));
        maven.put("sourceFiles", 0);
        result.set("maven", maven);

        SourceUsage usage = scanSources(projectPath);
        maven.put("sourceFiles", usage.fileCount);

        ObjectNode source = JsonUtils.MAPPER.createObjectNode();
        source.put("javaFileCount", usage.fileCount);
        source.put("usesSpring", usage.spring);
        source.put("usesJpa", usage.jpa);
        source.put("usesMySql", usage.mysql);
        source.put("usesJavaFx", usage.javafx);
        source.put("usesJackson", usage.jackson);
        source.put("usesJUnit", usage.junit);
        source.put("hasJavaFxHeadlessConfig", usage.javafxHeadless);
        result.set("source", source);

        ArrayNode issues = JsonUtils.MAPPER.createArrayNode();
        if (!hasPom) {
            issue(issues, "NO_POM", "error", "pom.xml not found",
                    "Create a Maven build file or adjust projectPath.");
        }
        if (usage.spring && !contains(pom, "spring-boot")) {
            issue(issues, "MISSING_SPRING_BOOT", "error",
                    "Source imports Spring but no Spring Boot dependency is declared",
                    "Add spring-boot-starter or spring-boot-starter-web (managed by spring-boot-starter-parent).");
        }
        if (usage.jpa && !contains(pom, "spring-boot-starter-data-jpa")) {
            issue(issues, "MISSING_JPA", "error",
                    "Source uses JPA/Hibernate but spring-boot-starter-data-jpa is missing",
                    "Add spring-boot-starter-data-jpa.");
        }
        if (usage.mysql && !contains(pom, "mysql-connector-j")) {
            issue(issues, "MISSING_MYSQL_DRIVER", "error",
                    "Source uses the MySQL driver but no mysql-connector-j dependency is declared",
                    "Add com.mysql:mysql-connector-j (runtime scope).");
        }
        if (contains(pom, "mysql-connector-java")) {
            issue(issues, "LEGACY_MYSQL_COORDINATES", "warning",
                    "mysql-connector-java is the old, relocated artifact",
                    "Replace it with com.mysql:mysql-connector-j.");
        }
        if (usage.javafx && !contains(pom, "javafx-controls")) {
            issue(issues, "MISSING_JAVAFX", "error",
                    "Source imports JavaFX but javafx-controls is missing",
                    "Add org.openjfx:javafx-controls (and javafx-graphics for headless rendering).");
        }
        if (usage.javafx && !usage.javafxHeadless) {
            issue(issues, "JAVAFX_HEADLESS", "warning",
                    "JavaFX code is present but the Monocle headless platform is not configured",
                    "Set System.setProperty(\"javafx.platform\", \"monocle\") before JavaFX starts when running on a server.");
        }
        if (usage.jackson && !contains(pom, "jackson-databind")) {
            issue(issues, "MISSING_JACKSON", "error",
                    "Source uses Jackson but jackson-databind is missing",
                    "Add com.fasterxml.jackson.core:jackson-databind or use spring-boot-starter-json.");
        }
        if (usage.junit && !contains(pom, "junit-jupiter") && !contains(pom, "spring-boot-starter-test")) {
            issue(issues, "MISSING_JUNIT", "warning",
                    "Test sources use JUnit but no JUnit dependency is declared",
                    "Add org.junit.jupiter:junit-jupiter with test scope.");
        }

        result.set("issues", issues);
        result.put("issueCount", issues.size());
        result.put("healthy", issues.size() == 0);
        return result;
    }

    private static void issue(ArrayNode issues, String code, String severity, String description, String suggestion) {
        ObjectNode issue = JsonUtils.MAPPER.createObjectNode();
        issue.put("code", code);
        issue.put("severity", severity);
        issue.put("description", description);
        issue.put("suggestion", suggestion);
        issues.add(issue);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.contains(needle);
    }

    private static String firstMatch(Pattern pattern, String value, String fallback) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1).trim() : fallback;
    }

    private static SourceUsage scanSources(Path projectPath) throws IOException {
        SourceUsage usage = new SourceUsage();
        Path src = projectPath.resolve("src");
        Path root = Files.isDirectory(src) ? src : projectPath;
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !isIgnored(projectPath.relativize(p)))
                    .limit(MAX_SOURCE_FILES)
                    .toList();
            usage.fileCount = files.size();
            for (Path file : files) {
                String text;
                try {
                    text = Files.readString(file);
                } catch (IOException e) {
                    continue;
                }
                String path = file.toString().replace('\\', '/');
                boolean testSource = path.contains("/src/test/");
                usage.spring |= text.contains("org.springframework");
                usage.jpa |= text.contains("jakarta.persistence") || text.contains("javax.persistence");
                usage.mysql |= text.contains("com.mysql") || text.contains("MysqlDataSource") || text.contains("jdbc:mysql");
                usage.javafx |= text.contains("javafx.");
                usage.jackson |= text.contains("com.fasterxml.jackson");
                usage.junit |= testSource && (text.contains("org.junit.jupiter") || text.contains("org.junit."));
                usage.javafxHeadless |= text.contains("javafx.platform") && text.contains("monocle");
            }
        }
        return usage;
    }

    private static boolean isIgnored(Path relative) {
        for (Path part : relative) {
            String name = part.toString();
            if (name.equals("target") || name.equals("build") || name.equals("out")
                    || name.equals("bin") || name.equals("node_modules") || name.equals(".git")) {
                return true;
            }
        }
        return false;
    }

    private static final class SourceUsage {
        int fileCount;
        boolean spring;
        boolean jpa;
        boolean mysql;
        boolean javafx;
        boolean jackson;
        boolean junit;
        boolean javafxHeadless;
    }
}
