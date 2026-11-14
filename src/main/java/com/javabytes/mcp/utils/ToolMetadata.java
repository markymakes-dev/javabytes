package com.javabytes.mcp.utils;

import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The derived tool catalog (ADR 0006): a name → spec index built at startup
 * from the registered tool instances. Every tool declares its own spec (see
 * {@link ToolSpec}); nothing is cataloged by hand here.
 *
 * <p>Failing closed: a registered tool whose spec is missing, or whose spec
 * name does not match its registration, is <em>uncataloged</em> — it is never
 * enabled, never advertised in {@code tools/list}, and produces a loud startup
 * warning on stderr and, when set, in {@code JAVABYTES_LOG_FILE}. There is no
 * fallback metadata; the catalog can only be wrong in the safe direction.</p>
 */
public final class ToolMetadata {

    public static final String FILES = "files";
    public static final String DATABASE = "database";
    public static final String SYSTEM = "system";
    public static final String JSON = "json";
    public static final String JAVAFX = "javafx";
    public static final String ANALYSIS = "analysis";
    public static final String DIAGNOSTICS = "diagnostics";
    public static final String UI = "ui";
    public static final String DESIGN = "design";
    public static final String CODEGEN = "codegen";
    public static final String SPRINGBOOT = "springboot";

    /** The canonical category vocabulary, kept as strings for now (ADR 0006). */
    public static final Set<String> KNOWN_CATEGORIES = Set.of(
            FILES, DATABASE, SYSTEM, JSON, JAVAFX, ANALYSIS, DIAGNOSTICS,
            UI, DESIGN, CODEGEN, SPRINGBOOT);

    /**
     * Categories that are opt-in. Everything else is registered on startup so
     * that a fresh client gets the capability tools without any configuration.
     */
    public static final Set<String> DEFAULT_DISABLED_CATEGORIES =
            Set.of(UI, DESIGN, CODEGEN, SPRINGBOOT);

    private static volatile Map<String, ToolSpec> catalog = Map.of();

    private ToolMetadata() {
    }

    /**
     * Derive the catalog from the tools that will be registered. Idempotent:
     * calling it again simply rebuilds the index (tests use this to restore).
     */
    public static void derive(Collection<Tool> tools) {
        Map<String, ToolSpec> derived = new LinkedHashMap<>();
        for (Tool tool : tools) {
            ToolSpec spec = tool.spec();
            if (spec == null || !tool.getName().equals(spec.name())) {
                warnUncataloged(tool);
                continue;
            }
            derived.put(spec.name(), spec);
        }
        catalog = Collections.unmodifiableMap(derived);
    }

    /** The spec for a tool name, or null when the tool is uncataloged. */
    public static ToolSpec get(String toolName) {
        return catalog.get(toolName);
    }

    public static boolean isKnown(String toolName) {
        return catalog.containsKey(toolName);
    }

    public static Map<String, ToolSpec> catalog() {
        return catalog;
    }

    public static Set<String> categories() {
        Set<String> categories = new LinkedHashSet<>();
        for (ToolSpec spec : catalog.values()) {
            categories.add(spec.category());
        }
        return Collections.unmodifiableSet(categories);
    }

    public static Set<String> toolsIn(String category) {
        Set<String> names = new LinkedHashSet<>();
        for (Map.Entry<String, ToolSpec> entry : catalog.entrySet()) {
            if (entry.getValue().category().equals(category)) {
                names.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(names);
    }

    private static void warnUncataloged(Tool tool) {
        String reason = tool.spec() == null
                ? "no spec declared"
                : "spec declares name '" + tool.spec().name() + "'";
        String message = "[javabytes] registered but uncataloged: " + tool.getName()
                + " (" + reason + "); the tool stays disabled until its spec matches (ADR 0006)";
        System.err.println(message);
        appendToProtocolLog(message);
    }

    /** Mirrors the protocol log's append-only, never-on-stdout discipline. */
    private static void appendToProtocolLog(String message) {
        String configured = System.getenv("JAVABYTES_LOG_FILE");
        if (configured == null || configured.isBlank()) {
            return;
        }
        try {
            Path path = Path.of(configured.trim()).toAbsolutePath().normalize();
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, Instant.now() + " " + message + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException e) {
            System.err.println("[javabytes] Cannot write startup warning to log file: " + e.getMessage());
        }
    }
}
