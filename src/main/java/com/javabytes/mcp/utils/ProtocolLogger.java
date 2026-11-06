package com.javabytes.mcp.utils;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Optional protocol trace logger.
 *
 * <p>Set {@code JAVABYTES_LOG_FILE} to a path to record every JSON-RPC message
 * in both directions. The log is append-only and never written to stdout, so it
 * cannot corrupt the MCP stream.</p>
 */
public final class ProtocolLogger {

    private static final PrintWriter WRITER = open();

    private ProtocolLogger() {
    }

    private static PrintWriter open() {
        String configured = System.getenv("JAVABYTES_LOG_FILE");
        if (configured == null || configured.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(configured.trim()).toAbsolutePath().normalize();
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            return new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND), true);
        } catch (IOException | RuntimeException e) {
            System.err.println("[javabytes] Cannot open protocol log: " + e.getMessage());
            return null;
        }
    }

    public static void log(String direction, String payload) {
        if (WRITER == null) {
            return;
        }
        WRITER.println(Instant.now() + " " + direction + " " + payload);
    }

    public static void close() {
        if (WRITER != null) {
            WRITER.flush();
        }
    }
}
