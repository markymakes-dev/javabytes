package com.javabytes.mcp.config;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Application configuration for JavaBytes MCP Server.
 * Centralizes configuration values like database connection, allowed directories, etc.
 */
public class AppConfig {

    // Database configuration
    public static final String DB_URL = System.getenv().getOrDefault("JAVABYTES_DB_URL", "jdbc:mysql://localhost:3306/mydb");
    public static final String DB_USERNAME = System.getenv().getOrDefault("JAVABYTES_DB_USER", "user");
    public static final String DB_PASSWORD = System.getenv().getOrDefault("JAVABYTES_DB_PASSWORD", "password");

    // Server configuration
    public static final String SERVER_VERSION = "1.0.0";
    public static final String JAVA_VERSION = System.getProperty("java.version");

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    /**
     * Fallback allowed directories (security boundary for file operations).
     *
     * <p>Override with the {@code JAVABYTES_ALLOWED_DIRS} environment variable using the
     * platform path separator ({@code ;} on Windows, {@code :} on Unix). When unset or
     * blank, the current working directory and the JVM temporary directory are allowed.</p>
     *
     * <p>If the MCP client advertises roots, the client's roots replace this fallback
     * for the lifetime of the session.</p>
     */
    private static final List<Path> FALLBACK_DIRECTORIES = loadAllowedDirectories();

    /** Active sandbox roots. Replaced by client roots when the client provides them. */
    private static volatile List<Path> allowedDirectories = FALLBACK_DIRECTORIES;

    /** The directories configured by the environment, before any client roots. */
    public static List<Path> getFallbackDirectories() {
        return FALLBACK_DIRECTORIES;
    }

    /** The directories currently enforced by the file tools. */
    public static List<Path> getAllowedDirectories() {
        return allowedDirectories;
    }

    /**
     * Replace the sandbox with client-provided roots.
     *
     * <p>Filesystem roots (for example {@code C:\} or {@code /}) are rejected so a
     * misconfigured client cannot accidentally grant access to the whole disk. An
     * empty or fully rejected list restores the environment fallback.</p>
     *
     * @return the roots now in effect
     */
    public static List<Path> applyClientRoots(List<Path> roots) {
        List<Path> accepted = new ArrayList<>();
        if (roots != null) {
            for (Path root : roots) {
                Path normalized = normalizeClientRoot(root);
                if (normalized != null && !accepted.contains(normalized)) {
                    accepted.add(normalized);
                }
            }
        }
        if (accepted.isEmpty()) {
            System.err.println("[javabytes] No usable client roots; keeping fallback sandbox: "
                    + describe(FALLBACK_DIRECTORIES));
            allowedDirectories = FALLBACK_DIRECTORIES;
        } else {
            allowedDirectories = List.copyOf(accepted);
            System.err.println("[javabytes] Sandbox now limited to client roots: "
                    + describe(allowedDirectories));
        }
        return allowedDirectories;
    }

    /** Restore the environment fallback sandbox. */
    public static void resetClientRoots() {
        allowedDirectories = FALLBACK_DIRECTORIES;
    }

    private static Path normalizeClientRoot(Path root) {
        if (root == null) {
            return null;
        }
        Path normalized;
        try {
            normalized = root.toAbsolutePath().normalize();
        } catch (InvalidPathException | SecurityException e) {
            System.err.println("[javabytes] Ignoring unusable client root: " + root);
            return null;
        }
        if (normalized.getParent() == null) {
            System.err.println("[javabytes] Refusing filesystem root as client root: " + normalized);
            return null;
        }
        if (Files.exists(normalized) && !Files.isDirectory(normalized)) {
            System.err.println("[javabytes] Client root is not a directory: " + normalized);
            return null;
        }
        return normalized;
    }

    private static List<Path> loadAllowedDirectories() {
        String configured = System.getenv("JAVABYTES_ALLOWED_DIRS");
        List<String> raw = new ArrayList<>();
        if (configured != null && !configured.isBlank()) {
            for (String entry : configured.split(File.pathSeparator)) {
                if (!entry.isBlank()) {
                    raw.add(entry.trim());
                }
            }
        }
        if (raw.isEmpty()) {
            raw.add(System.getProperty("user.dir"));
            raw.add(System.getProperty("java.io.tmpdir"));
        }

        List<Path> resolved = new ArrayList<>();
        for (String entry : raw) {
            try {
                resolved.add(Path.of(entry).toAbsolutePath().normalize());
            } catch (InvalidPathException e) {
                System.err.println("[javabytes] Ignoring invalid allowed directory: " + entry);
            }
        }
        return List.copyOf(resolved);
    }

    /**
     * Check whether a path is inside one of the configured allowed directories.
     *
     * <p>Follows the same rules as the official MCP filesystem server: reject null
     * bytes and empty paths, normalize, resolve symlinks for the deepest existing
     * ancestor, then compare on path-component boundaries so that
     * {@code /tmp/evil} is not accepted for an allowed root of {@code /tmp-evil}.</p>
     */
    public static boolean isPathAllowed(Path path) {
        if (path == null) {
            return false;
        }
        String raw = path.toString();
        if (raw.isEmpty() || raw.indexOf('\0') >= 0) {
            return false;
        }

        Path candidate;
        try {
            candidate = resolveRealPath(path.toAbsolutePath().normalize());
        } catch (InvalidPathException | SecurityException e) {
            return false;
        }
        if (candidate == null) {
            return false;
        }

        for (Path allowed : allowedDirectories) {
            Path allowedReal;
            try {
                allowedReal = resolveRealPath(allowed);
            } catch (InvalidPathException | SecurityException e) {
                continue;
            }
            if (allowedReal != null && isWithin(candidate, allowedReal)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolve the real (symlink-free) form of a path, tolerating paths that do not
     * exist yet by resolving the deepest existing ancestor and re-appending the rest.
     * Returns {@code null} when no ancestor can be resolved.
     */
    private static Path resolveRealPath(Path path) {
        Path existing = path;
        Path remainder = null;
        while (existing != null && !Files.exists(existing)) {
            Path name = existing.getFileName();
            if (name == null) {
                break;
            }
            remainder = remainder == null ? name : name.resolve(remainder);
            existing = existing.getParent();
        }
        if (existing == null) {
            return path;
        }
        Path real;
        try {
            real = existing.toRealPath();
        } catch (Exception e) {
            real = existing;
        }
        return remainder == null ? real : real.resolve(remainder).normalize();
    }

    private static boolean isWithin(Path candidate, Path allowed) {
        String child = candidate.toString();
        String parent = allowed.toString();
        if (WINDOWS) {
            child = child.toLowerCase(Locale.ROOT);
            parent = parent.toLowerCase(Locale.ROOT);
        }
        if (child.equals(parent)) {
            return true;
        }
        if (!child.startsWith(parent) || child.length() <= parent.length()) {
            return false;
        }
        char boundary = child.charAt(parent.length());
        return boundary == File.separatorChar || boundary == '/' || boundary == '\\';
    }

    /** Human-readable list of allowed roots, used in error messages. */
    public static String describeAllowedDirectories() {
        return describe(allowedDirectories);
    }

    private static String describe(List<Path> directories) {
        return directories.stream()
                .map(Path::toString)
                .reduce((a, b) -> a + ", " + b)
                .orElse("(none)");
    }
}
