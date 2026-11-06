package com.javabytes.mcp;

import com.javabytes.mcp.config.AppConfig;
import com.javabytes.mcp.utils.PathValidator;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigSandboxTest {

    @Test
    void allowsWorkingDirectory() {
        assertTrue(AppConfig.isPathAllowed(Path.of(System.getProperty("user.dir"))));
    }

    @Test
    void allowsNewFileInsideWorkingDirectory() {
        Path notYetCreated = Path.of(System.getProperty("user.dir"), "target", "sandbox", "new-file.txt");
        assertTrue(AppConfig.isPathAllowed(notYetCreated));
    }

    @Test
    void rejectsTraversalOutOfWorkingDirectory() {
        Path escape = Path.of(System.getProperty("user.dir"))
                .resolve("..").resolve("..").resolve("..")
                .resolve("Windows").resolve("win.ini");
        assertFalse(AppConfig.isPathAllowed(escape));
    }

    @Test
    void rejectsSystemDirectories() {
        assertFalse(AppConfig.isPathAllowed(Path.of("C:\\Windows\\win.ini")));
        assertFalse(AppConfig.isPathAllowed(Path.of("/etc/passwd")));
    }

    @Test
    void rejectsNullByteAndInvalidPaths() {
        assertFalse(PathValidator.isAllowed("bad\0path"));
        assertFalse(PathValidator.isAllowed(""));
        assertFalse(PathValidator.isAllowed((String) null));
    }

    @Test
    void componentBoundaryDoesNotMatchSiblingPrefix() {
        String root = System.getProperty("user.dir");
        Path sibling = Path.of(root + "-evil", "secret.txt");
        assertFalse(AppConfig.isPathAllowed(sibling));
    }
}
