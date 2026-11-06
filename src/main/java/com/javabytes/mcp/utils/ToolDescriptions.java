package com.javabytes.mcp.utils;

import java.util.Map;

/**
 * Human-readable descriptions for every tool exposed over MCP.
 *
 * <p>Clients show these to the model when choosing a tool, so they explain what
 * the tool does rather than repeating its internal name.</p>
 */
public final class ToolDescriptions {

    private ToolDescriptions() {
    }

    private static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
            // File system
            Map.entry("read_text_file", "Read a UTF-8 text file inside an allowed directory and return its contents."),
            Map.entry("write_file", "Create or overwrite a text file inside an allowed directory."),
            Map.entry("list_directory", "List the entries of a directory with type and size information."),
            Map.entry("get_file_info", "Return existence, type, size, timestamps and permissions for a path."),
            Map.entry("make_directory", "Create a directory and any missing parents inside an allowed directory."),
            Map.entry("delete_file", "Delete a file or an empty directory inside an allowed directory."),
            Map.entry("search_files", "Find files by glob name pattern and, optionally, a regular expression on their contents."),
            Map.entry("backup_file", "Copy a file to a timestamped sibling backup without overwriting existing backups."),
            Map.entry("compare_files", "Compare two text files and report whether they differ, with a diff summary."),
            Map.entry("environment_variables", "List, read or explain environment variables. Secret-like values are masked."),

            // Database
            Map.entry("query_database", "Run a read-only SQL query against the configured MySQL database and return rows."),
            Map.entry("list_tables", "List the tables and views in the configured MySQL database."),
            Map.entry("describe_table", "Describe the columns, types, keys and defaults of a database table."),
            Map.entry("list_indexes", "List indexes, uniqueness and column order for a table or the whole schema."),
            Map.entry("list_foreign_keys", "List foreign keys with referenced tables, columns and delete/update rules."),
            Map.entry("explain_query", "Run MySQL EXPLAIN (or EXPLAIN ANALYZE) on a read-only SELECT and return the plan."),
            Map.entry("export_schema_ddl", "Export SHOW CREATE TABLE DDL for one table or every base table."),
            Map.entry("analyze_schema_normalization", "Review keys, foreign keys, indexes and sampled list-like columns for likely normal-form problems."),

            // System
            Map.entry("get_system_info", "Report JVM, operating-system and hardware information."),
            Map.entry("convert_time", "Convert a timestamp between time zones or between epoch and ISO-8601 formats."),
            Map.entry("list_services", "List all tools currently registered by this JavaBytes server."),
            Map.entry("get_version", "Return the JavaBytes server and Java runtime versions."),

            // JSON
            Map.entry("json_query", "Query a JSON document with a dot/bracket path and optionally return pretty output."),

            // JavaFX canvas
            Map.entry("javafx_init_canvas", "Create an off-screen JavaFX canvas with the given width and height."),
            Map.entry("javafx_draw_canvas", "Draw shapes and primitives onto the active off-screen canvas."),
            Map.entry("javafx_clear_canvas", "Clear the active off-screen canvas."),
            Map.entry("javafx_set_color", "Set the current fill or stroke color used by canvas drawing operations."),
            Map.entry("javafx_export_image", "Export the active off-screen canvas to a PNG image file."),

            // UI
            Map.entry("generate_ui_code", "Generate compilable JavaFX component code and matching JavaFX CSS for a variant."),
            Map.entry("generate_css", "Generate JavaFX CSS from design tokens such as color, radius and spacing."),
            Map.entry("extract_design_system", "Extract colors, typography and spacing tokens from CSS or HTML into a design system."),
            Map.entry("html_css_to_javafx_css", "Translate HTML/CSS declarations into valid JavaFX CSS and report unsupported properties."),
            Map.entry("analyze_javascript", "Inventory a JS/TS file or project: frameworks, npm dependencies, routes, components, browser APIs and migration risks."),
            Map.entry("translate_javascript", "Translate the safely typed subset of JS/TS to a compilable Java class and report everything that needs manual work."),
            Map.entry("translate_webapp", "Produce a migration plan for a web app and optionally scaffold a Spring Boot or JavaFX project."),
            Map.entry("generate_javafx_component", "Generate a reusable JavaFX component class from a specification."),
            Map.entry("analyze_ui_layout", "Analyze a UI layout description and report spacing, hierarchy and accessibility concerns."),

            // Design
            Map.entry("design_tokens", "Produce a coherent set of design tokens (colors, type scale, spacing, radii)."),
            Map.entry("anti_slop_review", "Review UI code or a design description for generic, low-quality visual patterns."),

            // Code
            Map.entry("analyze_code", "Analyze Java source for complexity, smells and structural problems."),
            Map.entry("generate_code", "Generate Java code for a described class or method."),
            Map.entry("generate_tests", "Generate JUnit tests for a Java class or method."),
            Map.entry("generate_docs", "Generate Javadoc or Markdown documentation for Java source."),
            Map.entry("refactor_code", "Refactor Java source for readability, naming and structure while preserving behaviour."),
            Map.entry("find_todos", "Find TODO, FIXME and HACK markers in a source tree."),
            Map.entry("check_security", "Scan Java source for common security issues such as injection and weak crypto."),

            // Spring Boot
            Map.entry("springboot_generate_entity", "Generate a JPA entity class with id, fields, equals/hashCode and auditing hooks."),
            Map.entry("springboot_generate_repository", "Generate a Spring Data JPA repository interface with derived and custom queries."),
            Map.entry("springboot_generate_service", "Generate a Spring service with transactions and constructor injection."),
            Map.entry("springboot_generate_controller", "Generate a Spring MVC REST controller with validation and correct status codes."),
            Map.entry("springboot_generate_properties", "Generate application.properties/yml for a Spring Boot service and database."),
            Map.entry("springboot_generate_dto", "Generate request/response DTO records with validation annotations and mapping."),
            Map.entry("analyze_springboot_project", "Inspect a Spring Boot project and report missing layers, configuration and dependency issues."),
            Map.entry("springboot_generate_security", "Generate a Spring Security configuration with authentication and authorization rules."),
            Map.entry("springboot_generate_relationship", "Generate JPA entity relationships (one-to-many, many-to-many) with correct mappings."),
            Map.entry("springboot_generate_actuator", "Generate Spring Boot Actuator configuration and health/metrics endpoints."),
            Map.entry("springboot_generate_exception_handler", "Generate a @RestControllerAdvice with consistent error responses."),
            Map.entry("springboot_generate_validation", "Generate Bean Validation constraints and a validation error handler."),
            Map.entry("springboot_generate_pagination", "Generate pagination and sorting support for Spring Data endpoints."),
            Map.entry("springboot_generate_file_upload", "Generate a multipart file-upload endpoint with size and type validation."),
            Map.entry("springboot_generate_scheduled", "Generate scheduled jobs with safe scheduling configuration."),
            Map.entry("springboot_generate_async", "Generate asynchronous service methods and the required executor configuration."),
            Map.entry("springboot_generate_cache", "Generate Spring Cache configuration and annotated service methods."),
            Map.entry("springboot_generate_profile", "Generate profile-specific configuration (dev, test, prod)."),
            Map.entry("springboot_generate_docker", "Generate a multi-stage Dockerfile and docker-compose file for a Spring Boot service."),
            Map.entry("springboot_generate_test", "Generate Spring Boot integration and slice tests."),

            // Diagnostics
            Map.entry("project_diagnostic", "Check a project for build, dependency and configuration problems without modifying it."),
            Map.entry("fix_maven_dependencies", "Detect and report incorrect or missing Maven dependencies with suggested fixes."),
            Map.entry("create_mysql_client", "Generate a MySQL JDBC client helper class with safe connection handling."),
            Map.entry("fix_javafx_headless", "Detect and explain headless JavaFX problems and generate the required configuration."),
            Map.entry("fix_all", "Run all diagnostics, apply safe fixes and summarize what changed.")
    );

    public static String describe(String toolName) {
        return DESCRIPTIONS.getOrDefault(toolName, humanize(toolName));
    }

    private static String humanize(String toolName) {
        return title(toolName) + " (JavaBytes tool)";
    }

    /** Title-cased tool name, used as a fallback when no explicit title exists. */
    public static String title(String toolName) {
        String[] parts = toolName.split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
