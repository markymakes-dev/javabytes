package com.javabytes.mcp.tools.diagnostics;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ProjectInspectorTest {

    private Path project;

    @BeforeEach
    void setUp() throws IOException {
        project = Path.of(System.getProperty("user.dir"), "target", "test-work", getClass().getSimpleName());
        deleteRecursively(project);
        Files.createDirectories(project.resolve("src/main/java/com/example"));
        Files.writeString(project.resolve("pom.xml"), "<project/>");
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteRecursively(project);
    }

    @Test
    void plainProjectIsNotToldToAddSpringOrMcpSdk() throws IOException {
        Files.writeString(project.resolve("src/main/java/com/example/Plain.java"),
                "package com.example; public class Plain { }");

        JsonNode result = ProjectInspector.inspect(project);

        assertTrue(result.get("healthy").asBoolean(), result.toString());
        assertEquals(0, result.get("issueCount").asInt());
    }

    @Test
    void springUsageWithoutDependencyIsReported() throws IOException {
        Files.writeString(project.resolve("src/main/java/com/example/App.java"),
                "import org.springframework.boot.SpringApplication; public class App { }");

        JsonNode issues = ProjectInspector.inspect(project).get("issues");
        assertTrue(hasCode(issues, "MISSING_SPRING_BOOT"), issues.toString());
    }

    @Test
    void springUsageWithDependencyIsHealthy() throws IOException {
        Files.writeString(project.resolve("pom.xml"),
                "<project><dependency>spring-boot-starter-web</dependency></project>");
        Files.writeString(project.resolve("src/main/java/com/example/App.java"),
                "import org.springframework.boot.SpringApplication; public class App { }");

        assertTrue(ProjectInspector.inspect(project).get("healthy").asBoolean());
    }

    @Test
    void legacyMySqlCoordinatesAreFlagged() throws IOException {
        Files.writeString(project.resolve("pom.xml"),
                "<project><dependency>mysql-connector-java</dependency></project>");

        JsonNode issues = ProjectInspector.inspect(project).get("issues");
        assertTrue(hasCode(issues, "LEGACY_MYSQL_COORDINATES"), issues.toString());
    }

    private boolean hasCode(JsonNode issues, String code) {
        for (JsonNode issue : issues) {
            if (code.equals(issue.get("code").asText())) {
                return true;
            }
        }
        return false;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        }
    }
}
