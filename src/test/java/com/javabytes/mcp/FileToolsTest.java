package com.javabytes.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.javabytes.mcp.tools.file.GetFileInfoTool;
import com.javabytes.mcp.tools.file.MakeDirectoryTool;
import com.javabytes.mcp.tools.file.SearchFilesTool;
import com.javabytes.mcp.utils.JsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class FileToolsTest {

    private Path workDir;

    @BeforeEach
    void setUp() throws IOException {
        workDir = Path.of(System.getProperty("user.dir"), "target", "test-work", getClass().getSimpleName());
        deleteRecursively(workDir);
        Files.createDirectories(workDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteRecursively(workDir);
    }

    @Test
    void makeDirectoryReportsNewDirectoryCorrectly() {
        Path newDir = workDir.resolve("created").resolve("nested");
        JsonNode first = new MakeDirectoryTool().execute(params("path", newDir.toString()));

        assertTrue(first.get("success").asBoolean());
        assertTrue(first.get("created").asBoolean());
        assertFalse(first.get("already_existed").asBoolean());
        assertTrue(Files.isDirectory(newDir));

        JsonNode second = new MakeDirectoryTool().execute(params("path", newDir.toString()));
        assertTrue(second.get("success").asBoolean());
        assertFalse(second.get("created").asBoolean());
        assertTrue(second.get("already_existed").asBoolean());
    }

    @Test
    void searchFilesTreatsPatternAsGlob() throws IOException {
        Files.writeString(workDir.resolve("Alpha.java"), "class Alpha {}");
        Files.writeString(workDir.resolve("Beta.java"), "class Beta {}");
        Files.writeString(workDir.resolve("notes.txt"), "hello");

        JsonNode result = new SearchFilesTool().execute(
                JsonUtils.MAPPER.createObjectNode()
                        .put("directory", workDir.toString())
                        .put("pattern", "*.java"));

        assertTrue(result.get("success").asBoolean(), result.toString());
        assertEquals(2, result.get("count").asInt());
    }

    @Test
    void searchFilesSupportsContentRegex() throws IOException {
        Files.writeString(workDir.resolve("Match.java"), "public class Match { /* TODO fix */ }");
        Files.writeString(workDir.resolve("Other.java"), "public class Other {}");

        JsonNode result = new SearchFilesTool().execute(
                JsonUtils.MAPPER.createObjectNode()
                        .put("directory", workDir.toString())
                        .put("pattern", "*.java")
                        .put("content", "TODO"));

        assertTrue(result.get("success").asBoolean(), result.toString());
        assertEquals(1, result.get("count").asInt());
        assertEquals("Match.java", result.get("rows").get(0).get("name").asText());
    }

    @Test
    void getFileInfoRejectsPathsOutsideSandbox() {
        JsonNode result = new GetFileInfoTool().execute(params("path", systemFileOutsideSandbox().toString()));
        assertFalse(result.get("success").asBoolean());
        assertEquals("PATH_NOT_ALLOWED", result.get("error").asText());
    }

    /**
     * An absolute system file outside the sandbox. A Windows path is merely
     * relative on Unix, which would land inside the working directory and be
     * allowed, so the platform picks its own.
     */
    private static Path systemFileOutsideSandbox() {
        return System.getProperty("os.name").toLowerCase().contains("win")
                ? Path.of("C:\\Windows\\win.ini")
                : Path.of("/etc/passwd");
    }

    private com.fasterxml.jackson.databind.node.ObjectNode params(String key, String value) {
        return JsonUtils.MAPPER.createObjectNode().put(key, value);
    }

    static void deleteRecursively(Path path) throws IOException {
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
