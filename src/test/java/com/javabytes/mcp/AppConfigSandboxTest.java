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
        assertFalse(AppConfig.isPathAllowed(systemFileOutsideSandbox()));
    }

    /**
     * An absolute system path that exists on this platform and sits outside the
     * sandbox. Windows-style paths are relative on Unix (and vice versa), so the
     * test has to pick one per platform rather than assert both.
     */
    private static Path systemFileOutsideSandbox() {
        return isWindows() ? Path.of("C:\\Windows\\win.ini") : Path.of("/etc/passwd");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
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
