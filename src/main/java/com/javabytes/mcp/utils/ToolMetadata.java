package com.javabytes.mcp.utils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Static catalog describing every tool: its human-readable title, its feature
 * category, whether it is on by default, and its MCP behaviour hints.
 *
 * <p>This mirrors the Dart/Flutter MCP server's feature configuration: tools
 * belong to categories that can be enabled or disabled as a group, while an
 * individual tool can still be overridden by name.</p>
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

    /**
     * Categories that are opt-in. Everything else is registered on startup so
     * that a fresh client gets the capability tools without any configuration.
     */
    public static final Set<String> DEFAULT_DISABLED_CATEGORIES =
            Set.of(UI, DESIGN, CODEGEN, SPRINGBOOT);

    /**
     * A single tool's metadata.
     *
     * <p>{@code readOnly}, {@code destructive}, {@code idempotent} and
     * {@code openWorld} map directly to the MCP tool annotation hints.</p>
     */
    public record Metadata(
            String title,
            String category,
            boolean enabledByDefault,
            boolean readOnly,
            boolean destructive,
            boolean idempotent,
            boolean openWorld) {
    }

    private static final Map<String, Metadata> CATALOG = buildCatalog();

    private ToolMetadata() {
    }

    public static Metadata get(String toolName) {
        Metadata metadata = CATALOG.get(toolName);
        return metadata != null ? metadata : fallback(toolName);
    }

    public static boolean isKnown(String toolName) {
        return CATALOG.containsKey(toolName);
    }

    public static Map<String, Metadata> catalog() {
        return CATALOG;
    }

    public static Set<String> categories() {
        Set<String> categories = new LinkedHashSet<>();
        for (Metadata metadata : CATALOG.values()) {
            categories.add(metadata.category());
        }
        return Collections.unmodifiableSet(categories);
    }

    public static Set<String> toolsIn(String category) {
        Set<String> names = new LinkedHashSet<>();
        for (Map.Entry<String, Metadata> entry : CATALOG.entrySet()) {
            if (entry.getValue().category().equals(category)) {
                names.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(names);
    }

    private static Metadata fallback(String toolName) {
        return new Metadata(ToolDescriptions.title(toolName), "uncategorized", true,
                true, false, true, false);
    }

    // --- builders ---------------------------------------------------------

    private static boolean enabledByDefault(String category) {
        return !DEFAULT_DISABLED_CATEGORIES.contains(category);
    }

    /** Read-only, idempotent tool that stays inside the server's own process. */
    private static Metadata read(String title, String category) {
        return new Metadata(title, category, enabledByDefault(category), true, false, true, false);
    }

    /** Read-only tool that observes something outside the server process. */
    private static Metadata readOpen(String title, String category) {
        return new Metadata(title, category, enabledByDefault(category), true, false, true, true);
    }

    /** Mutating tool that adds state without removing anything. */
    private static Metadata additive(String title, String category, boolean idempotent) {
        return new Metadata(title, category, enabledByDefault(category), false, false, idempotent, false);
    }

    /** Mutating tool that can overwrite or delete existing state. */
    private static Metadata destructive(String title, String category, boolean idempotent) {
        return new Metadata(title, category, enabledByDefault(category), false, true, idempotent, false);
    }

    /** In-memory tool that changes server-side state, such as the canvas. */
    private static Metadata stateful(String title, String category, boolean idempotent) {
        return new Metadata(title, category, enabledByDefault(category), false, false, idempotent, false);
    }

    private static Map<String, Metadata> buildCatalog() {
        Map<String, Metadata> m = new LinkedHashMap<>();

        // Filesystem
        m.put("read_text_file", read("Read Text File", FILES));
        m.put("write_file", destructive("Write Text File", FILES, true));
        m.put("list_directory", read("List Directory", FILES));
        m.put("get_file_info", read("Get File Info", FILES));
        m.put("make_directory", additive("Create Directory", FILES, true));
        m.put("delete_file", destructive("Delete File", FILES, false));
        m.put("search_files", read("Search Files", FILES));
        m.put("backup_file", additive("Back Up File", FILES, false));
        m.put("compare_files", read("Compare Files", FILES));
        m.put("environment_variables", readOpen("Environment Variables", FILES));

        // Database
        m.put("query_database", readOpen("Query Database", DATABASE));
        m.put("list_tables", readOpen("List Database Tables", DATABASE));
        m.put("describe_table", readOpen("Describe Database Table", DATABASE));
        m.put("list_indexes", readOpen("List Indexes", DATABASE));
        m.put("list_foreign_keys", readOpen("List Foreign Keys", DATABASE));
        m.put("explain_query", readOpen("Explain Query Plan", DATABASE));
        m.put("export_schema_ddl", readOpen("Export Schema DDL", DATABASE));
        m.put("analyze_schema_normalization", readOpen("Analyze Schema Normalization", DATABASE));

        // System
        m.put("get_system_info", readOpen("Get System Info", SYSTEM));
        m.put("convert_time", read("Convert Time", SYSTEM));
        m.put("list_services", read("List Enabled Tools", SYSTEM));
        m.put("get_version", read("Get Server Version", SYSTEM));

        // JSON
        m.put("json_query", read("Query JSON", JSON));

        // JavaFX canvas
        m.put("javafx_init_canvas", stateful("Create Canvas", JAVAFX, false));
        m.put("javafx_draw_canvas", stateful("Draw on Canvas", JAVAFX, false));
        m.put("javafx_clear_canvas", stateful("Clear Canvas", JAVAFX, true));
        m.put("javafx_set_color", stateful("Set Canvas Color", JAVAFX, true));
        m.put("javafx_export_image", destructive("Export Canvas as PNG", JAVAFX, true));

        // Analysis
        m.put("analyze_code", read("Analyze Java Code", ANALYSIS));
        m.put("find_todos", read("Find TODO Markers", ANALYSIS));
        m.put("check_security", read("Check Security Issues", ANALYSIS));
        m.put("analyze_ui_layout", read("Analyze UI Layout", ANALYSIS));
        m.put("extract_data_models", readOpen("Extract Data Models", ANALYSIS));
        m.put("models_to_schema", readOpen("Generate Schema from Models", ANALYSIS));
        m.put("generate_database", additive("Generate Database from DDL", ANALYSIS, false));
        m.put("extract_design_system", read("Extract Design System", ANALYSIS));
        m.put("html_css_to_javafx_css", read("Convert HTML/CSS to JavaFX CSS", ANALYSIS));
        m.put("analyze_javascript", read("Analyze JavaScript/TypeScript", ANALYSIS));

        // Diagnostics
        m.put("project_diagnostic", read("Diagnose Project", DIAGNOSTICS));
        m.put("fix_maven_dependencies", read("Check Maven Dependencies", DIAGNOSTICS));
        m.put("create_mysql_client", read("Generate MySQL Client", DIAGNOSTICS));
        m.put("fix_javafx_headless", additive("Configure Headless JavaFX", DIAGNOSTICS, true));
        m.put("fix_all", read("Run All Diagnostics", DIAGNOSTICS));

        // UI template generators (opt-in)
        m.put("generate_ui_code", read("Generate UI Code", UI));
        m.put("generate_css", read("Generate JavaFX CSS", UI));
        m.put("generate_javafx_component", read("Generate JavaFX Component", UI));

        // Design helpers (opt-in)
        m.put("design_tokens", read("Generate Design Tokens", DESIGN));
        m.put("anti_slop_review", read("Review for Generic UI", DESIGN));

        // Code generators (opt-in)
        m.put("generate_code", read("Generate Java Code", CODEGEN));
        m.put("generate_tests", read("Generate JUnit Tests", CODEGEN));
        m.put("generate_docs", read("Generate Documentation", CODEGEN));
        m.put("refactor_code", read("Refactor Java Code", CODEGEN));
        m.put("translate_javascript", additive("Translate JavaScript to Java", CODEGEN, true));
        m.put("translate_webapp", additive("Plan Web App Migration", CODEGEN, true));

        // Spring Boot generators (opt-in)
        m.put("springboot_generate_entity", read("Generate JPA Entity", SPRINGBOOT));
        m.put("springboot_generate_repository", read("Generate Spring Repository", SPRINGBOOT));
        m.put("springboot_generate_service", read("Generate Spring Service", SPRINGBOOT));
        m.put("springboot_generate_controller", read("Generate REST Controller", SPRINGBOOT));
        m.put("springboot_generate_properties", read("Generate Application Properties", SPRINGBOOT));
        m.put("springboot_generate_dto", read("Generate DTO Records", SPRINGBOOT));
        m.put("springboot_generate_security", read("Generate Security Config", SPRINGBOOT));
        m.put("springboot_generate_relationship", read("Generate JPA Relationships", SPRINGBOOT));
        m.put("springboot_generate_actuator", read("Generate Actuator Config", SPRINGBOOT));
        m.put("springboot_generate_exception_handler", read("Generate Error Handler", SPRINGBOOT));
        m.put("springboot_generate_validation", read("Generate Validation", SPRINGBOOT));
        m.put("springboot_generate_pagination", read("Generate Pagination", SPRINGBOOT));
        m.put("springboot_generate_file_upload", read("Generate File Upload", SPRINGBOOT));
        m.put("springboot_generate_scheduled", read("Generate Scheduled Jobs", SPRINGBOOT));
        m.put("springboot_generate_async", read("Generate Async Config", SPRINGBOOT));
        m.put("springboot_generate_cache", read("Generate Cache Config", SPRINGBOOT));
        m.put("springboot_generate_profile", read("Generate Profiles", SPRINGBOOT));
        m.put("springboot_generate_docker", read("Generate Dockerfile", SPRINGBOOT));
        m.put("springboot_generate_test", read("Generate Spring Tests", SPRINGBOOT));

        // Spring Boot analysis is inspection, not generation, so it stays on.
        m.put("analyze_springboot_project", read("Analyze Spring Boot Project", ANALYSIS));

        return Collections.unmodifiableMap(m);
    }
}
