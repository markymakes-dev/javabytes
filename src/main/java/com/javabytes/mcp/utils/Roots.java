package com.javabytes.mcp.utils;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts MCP {@code roots/list} results into filesystem paths.
 *
 * <p>Only {@code file://} roots are meaningful to a local stdio server; other
 * schemes are ignored rather than treated as an error, because a client may
 * legitimately advertise mixed roots.</p>
 */
public final class Roots {

    private Roots() {
    }

    /** Extract paths from a {@code roots/list} result object. */
    public static List<Path> paths(JsonNode result) {
        List<Path> paths = new ArrayList<>();
        if (result == null || !result.isObject()) {
            return paths;
        }
        JsonNode roots = result.get("roots");
        if (roots == null || !roots.isArray()) {
            return paths;
        }
        for (JsonNode root : roots) {
            String uri = root.path("uri").asText("");
            Path path = toPath(uri);
            if (path != null && !paths.contains(path)) {
                paths.add(path);
            }
        }
        return paths;
    }

    /** Convert a single {@code file://} URI to a normalized absolute path. */
    public static Path toPath(String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        try {
            URI parsed = URI.create(uri.trim());
            if (!"file".equalsIgnoreCase(parsed.getScheme())) {
                System.err.println("[javabytes] Ignoring non-file root: " + uri);
                return null;
            }
            return Path.of(parsed).toAbsolutePath().normalize();
        } catch (Exception e) {
            System.err.println("[javabytes] Ignoring invalid root URI '" + uri + "': " + e.getMessage());
            return null;
        }
    }
}
