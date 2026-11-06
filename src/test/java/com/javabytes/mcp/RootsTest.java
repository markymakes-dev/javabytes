package com.javabytes.mcp;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.config.AppConfig;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.Roots;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RootsTest {

    @Test
    void extractsFileRootsAndIgnoresOtherSchemes(@TempDir Path tempDir) {
        ObjectNode result = JsonUtils.MAPPER.createObjectNode();
        ArrayNode roots = JsonUtils.MAPPER.createArrayNode();

        ObjectNode file = JsonUtils.MAPPER.createObjectNode();
        file.put("uri", tempDir.toUri().toString());
        file.put("name", "workspace");
        roots.add(file);

        ObjectNode remote = JsonUtils.MAPPER.createObjectNode();
        remote.put("uri", "https://example.com/repo");
        roots.add(remote);

        ObjectNode broken = JsonUtils.MAPPER.createObjectNode();
        broken.put("uri", "not a uri");
        roots.add(broken);

        result.set("roots", roots);

        List<Path> paths = Roots.paths(result);
        assertEquals(1, paths.size());
        assertEquals(tempDir.toAbsolutePath().normalize(), paths.get(0));
    }

    @Test
    void missingOrMalformedResultYieldsNoRoots() {
        assertTrue(Roots.paths(null).isEmpty());
        assertTrue(Roots.paths(JsonUtils.MAPPER.createObjectNode()).isEmpty());
        assertTrue(Roots.paths(JsonUtils.MAPPER.createObjectNode().put("roots", "nope")).isEmpty());
    }

    @Test
    void clientRootsNarrowTheSandboxAndResetRestoresFallback(@TempDir Path tempDir) throws Exception {
        Path workspace = tempDir.resolve("workspace");
        try {
            Files.createDirectories(workspace);

            AppConfig.applyClientRoots(List.of(workspace));

            assertEquals(List.of(workspace.toAbsolutePath().normalize()),
                    AppConfig.getAllowedDirectories());
            assertTrue(AppConfig.isPathAllowed(workspace.resolve("src/Main.java")));
            assertFalse(AppConfig.isPathAllowed(Path.of(System.getProperty("user.dir"))));
        } finally {
            AppConfig.resetClientRoots();
        }

        assertTrue(AppConfig.isPathAllowed(Path.of(System.getProperty("user.dir"))));
    }

    @Test
    void nonFileRootsLeaveFallbackInPlace(@TempDir Path tempDir) throws Exception {
        Path file = Files.createFile(tempDir.resolve("file.txt"));
        try {
            AppConfig.applyClientRoots(List.of(file));

            assertTrue(AppConfig.isPathAllowed(Path.of(System.getProperty("user.dir"))));
            assertFalse(AppConfig.getAllowedDirectories().contains(file));
        } finally {
            AppConfig.resetClientRoots();
        }
    }

    @Test
    void filesystemRootIsRejected(@TempDir Path tempDir) {
        Path filesystemRoot = tempDir.getRoot();
        try {
            AppConfig.applyClientRoots(List.of(filesystemRoot));

            assertFalse(AppConfig.getAllowedDirectories().contains(filesystemRoot));
            assertTrue(AppConfig.isPathAllowed(Path.of(System.getProperty("user.dir"))));
        } finally {
            AppConfig.resetClientRoots();
        }
    }

    @Test
    void emptyRootListRestoresFallback() {
        try {
            AppConfig.applyClientRoots(List.of());

            assertEquals(AppConfig.getFallbackDirectories(), AppConfig.getAllowedDirectories());
        } finally {
            AppConfig.resetClientRoots();
        }
    }
}
