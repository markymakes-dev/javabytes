package com.javabytes.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.javabytes.mcp.config.AppConfig;
import com.javabytes.mcp.config.FeatureConfiguration;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.ToolMetadata;

import java.util.List;

/**
 * Backward-compatible facade for McpServerMain.
 * <p>
 * The actual server implementation lives in {@link CompositionRoot}.
 * This class remains as the public entry point (main) and provides
 * static accessors used by tests (declaredTools, features).
 * <p>
 * All logic has been extracted to:
 * - {@link CompositionRoot} — wiring, tool declaration, feature config
 * - {@link JsonRpcTransport} — stdio transport loop
 * - {@link ProtocolSession} — protocol handling (initialize, tools, roots)
 * - {@link SchemaRenderer} — tool descriptor wire rendering
 * - {@link JavaFxPlatform} — JavaFX platform init
 */
public class McpServerMain {

    /**
     * Main entry point — delegates to CompositionRoot.
     */
    public static void main(String[] args) {
        CompositionRoot.main(args);
    }

    /**
     * Every declared tool, in startup order. Idempotent per JVM.
     * Delegates to CompositionRoot singleton.
     */
    public static synchronized List<Tool> declaredTools() {
        return CompositionRoot.declaredTools();
    }

    /**
     * The catalog-derived feature configuration.
     * Delegates to CompositionRoot singleton.
     */
    public static synchronized FeatureConfiguration features() {
        return CompositionRoot.features();
    }

    /**
     * Supported MCP protocol versions, newest first.
     * @see AppConfig#SUPPORTED_PROTOCOL_VERSIONS
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    public static final List<String> SUPPORTED_PROTOCOL_VERSIONS = AppConfig.SUPPORTED_PROTOCOL_VERSIONS;

    /**
     * Build instructions for initialize response.
     * @deprecated Use {@link ProtocolSession#buildInstructions()} (generated from catalog).
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    public static String getInstructions() {
        return """
                JavaBytes is a general-purpose Java tool server. It does not require an MCP SDK.

                Environment:
                - File tools are sandboxed to the client's MCP roots when the client advertises them,
                  otherwise to JAVABYTES_ALLOWED_DIRS, otherwise to the working directory plus the
                  JVM temporary directory. Paths outside the current sandbox are rejected.
                - Database tools are read-only: one SELECT/SHOW/DESCRIBE/EXPLAIN/WITH statement,
                  capped at 1000 rows with a query timeout.

                Workflow:
                1. Inspect before changing: get_version, list_services, get_file_info, read_text_file,
                   search_files.
                2. Prefer evidence over guesses. project_diagnostic and analyze_springboot_project
                   report what they actually found; they do not invent missing dependencies.
                3. Generation tools emit templates. They do not compile, run, or verify their output.
                   Write the result and verify it with your own build or test command.
                4. Keep file edits inside the sandbox; ask the user for a new root instead of escaping it.

                Tool groups:
                - Enabled by default: files, database, system, json, javafx, analysis, diagnostics.
                - Opt-in generators: ui, design, codegen, springboot.
                Enable groups or individual tools with JAVABYTES_ENABLE=springboot,codegen (or "all");
                disable with JAVABYTES_DISABLE=<tool-or-group>. A tool disabled by name always wins.
                """;
    }

    /**
     * Tool descriptor wire rendering.
     * @deprecated Use {@link SchemaRenderer#render(Tool)}.
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    public static com.fasterxml.jackson.databind.node.ObjectNode toolDescriptor(Tool tool) {
        // This method is kept for any external code that might call it.
        // The actual implementation is in SchemaRenderer.
        return new com.javabytes.mcp.schema.types.SchemaRenderer().render(tool);
    }

    /**
     * Unknown tool message helper.
     * @deprecated Use {@link ProtocolSession#unknownToolMessage(String)}.
     */
    @Deprecated(since = "1.1.0", forRemoval = true)
    public static String unknownToolMessage(String toolName) {
        if (ToolMetadata.isKnown(toolName) && !features().isEnabled(toolName)) {
            return "Tool '" + toolName + "' is disabled by feature configuration (category: "
                    + ToolMetadata.get(toolName).category()
                    + "). Enable it with JAVABYTES_ENABLE=" + toolName
                    + " or JAVABYTES_ENABLE=" + ToolMetadata.get(toolName).category() + ".";
        }
        return "Unknown tool: " + toolName;
    }
}