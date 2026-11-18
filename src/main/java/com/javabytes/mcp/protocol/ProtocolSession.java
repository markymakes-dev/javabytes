package com.javabytes.mcp.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.config.AppConfig;
import com.javabytes.mcp.config.FeatureConfiguration;
import com.javabytes.mcp.McpServerRegistry;
import com.javabytes.mcp.prompts.PromptArgument;
import com.javabytes.mcp.prompts.PromptDefinition;
import com.javabytes.mcp.prompts.PromptRegistry;
import com.javabytes.mcp.schema.types.SchemaRenderer;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.Roots;
import com.javabytes.mcp.utils.ToolMetadata;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP protocol session: handles initialize, tools/list, tools/call,
 * prompts/list, prompts/get, roots negotiation.
 * Pure handler — no I/O. Transport calls these methods.
 */
public class ProtocolSession {

    private final McpServerRegistry registry;
    private final FeatureConfiguration featureConfig;
    private final SchemaRenderer schemaRenderer;
    private final PromptRegistry promptRegistry;
    private boolean clientSupportsRoots = false;

    public ProtocolSession(McpServerRegistry registry, FeatureConfiguration featureConfig,
                           SchemaRenderer schemaRenderer, PromptRegistry promptRegistry) {
        this.registry = registry;
        this.featureConfig = featureConfig;
        this.schemaRenderer = schemaRenderer;
        this.promptRegistry = promptRegistry;
    }

    /**
     * Handle initialize request: version negotiation, capabilities, serverInfo, instructions.
     */
    public JsonNode handleInitialize(JsonNode msg) {
        String requested = msg.at("/params/protocolVersion").asText("");
        String negotiated = AppConfig.SUPPORTED_PROTOCOL_VERSIONS.contains(requested)
                ? requested
                : AppConfig.SUPPORTED_PROTOCOL_VERSIONS.getFirst();
        clientSupportsRoots = msg.at("/params/capabilities/roots").isObject();

        ObjectNode result = JsonUtils.MAPPER.createObjectNode();
        result.put("protocolVersion", negotiated);
        ObjectNode capabilities = JsonUtils.MAPPER.createObjectNode();
        capabilities.set("tools", JsonUtils.MAPPER.createObjectNode());
        // Prompts are documentation (issue #18): always advertised, never
        // feature-gated — gating them would hide the crew manual from the
        // agents who need it.
        capabilities.set("prompts", JsonUtils.MAPPER.createObjectNode());
        result.set("capabilities", capabilities);
        ObjectNode serverInfo = JsonUtils.MAPPER.createObjectNode();
        serverInfo.put("name", "JavaBytes");
        serverInfo.put("version", AppConfig.SERVER_VERSION);
        result.set("serverInfo", serverInfo);
        result.put("instructions", buildInstructions());
        return result;
    }

    /**
     * Handle tools/list request.
     */
    public JsonNode handleToolsList() {
        ObjectNode result = JsonUtils.MAPPER.createObjectNode();
        ArrayNode tools = JsonUtils.MAPPER.createArrayNode();
        for (String name : registry.listTools()) {
            Tool tool = registry.getTool(name);
            if (tool != null) {
                tools.add(schemaRenderer.render(tool));
            }
        }
        result.set("tools", tools);
        return result;
    }

    /**
     * Handle tools/call request.
     */
    public JsonNode handleToolsCall(String toolName, JsonNode args) {
        Tool tool = registry.getTool(toolName);
        if (tool == null) {
            ObjectNode error = JsonUtils.MAPPER.createObjectNode();
            error.put("code", -32602);
            error.put("message", unknownToolMessage(toolName));
            ObjectNode resp = JsonUtils.MAPPER.createObjectNode();
            resp.put("jsonrpc", "2.0");
            resp.set("error", error);
            return resp;
        }
        JsonNode toolResult = registry.execute(toolName, args);
        ObjectNode result = JsonUtils.MAPPER.createObjectNode();
        ArrayNode content = JsonUtils.MAPPER.createArrayNode();
        ObjectNode text = JsonUtils.MAPPER.createObjectNode();
        text.put("type", "text");
        try {
            text.put("text", JsonUtils.MAPPER.writeValueAsString(toolResult));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            text.put("text", "{\"error\": \"Failed to serialize tool result: " + e.getMessage() + "\"}");
        }
        content.add(text);
        result.set("content", content);
        // isError derived from the wire envelope's success field (ADR 0008)
        boolean isError = toolResult.has("success") && !toolResult.get("success").asBoolean();
        result.put("isError", isError);
        // Issue #19: typed clients read the envelope as data. Successful
        // results carry structuredContent (the envelope object itself, not a
        // re-serialization); error results omit it per MCP guidance — the
        // envelope still travels in text content either way, so text-reading
        // clients see zero change.
        if (!isError) {
            result.set("structuredContent", toolResult);
        }
        return result;
    }

    /**
     * Handle prompts/list request (issue #18).
     */
    public JsonNode handlePromptsList() {
        ObjectNode result = JsonUtils.MAPPER.createObjectNode();
        ArrayNode prompts = JsonUtils.MAPPER.createArrayNode();
        for (PromptDefinition prompt : promptRegistry.list()) {
            ObjectNode entry = JsonUtils.MAPPER.createObjectNode();
            entry.put("name", prompt.name());
            entry.put("description", prompt.description());
            ArrayNode arguments = JsonUtils.MAPPER.createArrayNode();
            for (PromptArgument argument : prompt.arguments()) {
                ObjectNode arg = JsonUtils.MAPPER.createObjectNode();
                arg.put("name", argument.name());
                arg.put("description", argument.description());
                arg.put("required", argument.required());
                arguments.add(arg);
            }
            entry.set("arguments", arguments);
            prompts.add(entry);
        }
        result.set("prompts", prompts);
        return result;
    }

    /**
     * Handle prompts/get request (issue #18): render the template with the
     * supplied arguments. Unknown prompts and missing required arguments
     * produce invalid-params errors, mirroring handleToolsCall's shape.
     */
    public JsonNode handlePromptsGet(String promptName, JsonNode arguments) {
        if (!promptRegistry.hasPrompt(promptName)) {
            return invalidParams("Unknown prompt: " + promptName);
        }
        Map<String, String> supplied = new HashMap<>();
        if (arguments != null && arguments.isObject()) {
            arguments.fieldNames().forEachRemaining(field -> supplied.put(field, arguments.get(field).asText("")));
        }
        List<String> missing = promptRegistry.get(promptName).arguments().stream()
                .filter(PromptArgument::required)
                .map(PromptArgument::name)
                .filter(name -> !supplied.containsKey(name) || supplied.get(name).isBlank())
                .toList();
        if (!missing.isEmpty()) {
            return invalidParams("Missing required prompt argument(s): " + String.join(", ", missing));
        }
        PromptDefinition prompt = promptRegistry.get(promptName);
        ObjectNode result = JsonUtils.MAPPER.createObjectNode();
        result.put("description", prompt.description());
        ArrayNode messages = JsonUtils.MAPPER.createArrayNode();
        ObjectNode message = JsonUtils.MAPPER.createObjectNode();
        message.put("role", "user");
        ObjectNode content = JsonUtils.MAPPER.createObjectNode();
        content.put("type", "text");
        content.put("text", promptRegistry.render(promptName, supplied));
        message.set("content", content);
        messages.add(message);
        result.set("messages", messages);
        return result;
    }

    private JsonNode invalidParams(String message) {
        ObjectNode error = JsonUtils.MAPPER.createObjectNode();
        error.put("code", -32602);
        error.put("message", message);
        ObjectNode resp = JsonUtils.MAPPER.createObjectNode();
        resp.put("jsonrpc", "2.0");
        resp.set("error", error);
        return resp;
    }

    /**
     * Handle roots/list response from client.
     */
    public void handleRootsResponse(JsonNode msg) {
        if (msg.has("error")) {
            System.err.println("[javabytes] roots/list failed: "
                    + msg.at("/error/message").asText("unknown error")
                    + "; keeping fallback sandbox.");
            return;
        }
        List<java.nio.file.Path> roots = Roots.paths(msg.get("result"));
        if (roots.isEmpty()) {
            System.err.println("[javabytes] Client returned no file roots; keeping fallback sandbox.");
            return;
        }
        AppConfig.applyClientRoots(roots);
    }

    public boolean clientSupportsRoots() {
        return clientSupportsRoots;
    }

    private String unknownToolMessage(String toolName) {
        if (ToolMetadata.isKnown(toolName) && !featureConfig.isEnabled(toolName)) {
            return "Tool '" + toolName + "' is disabled by feature configuration (category: "
                    + ToolMetadata.get(toolName).category()
                    + "). Enable it with JAVABYTES_ENABLE=" + toolName
                    + " or JAVABYTES_ENABLE=" + ToolMetadata.get(toolName).category() + ".";
        }
        return "Unknown tool: " + toolName;
    }

    /**
     * Build instructions dynamically from catalog (eliminates drift from static INSTRUCTIONS blob).
     */
    private String buildInstructions() {
        StringBuilder md = new StringBuilder();
        md.append("JavaBytes is a general-purpose Java tool server. It does not require an MCP SDK.\n\n");
        md.append("Environment:\n");
        md.append("- File tools are sandboxed to the client's MCP roots when the client advertises them,\n");
        md.append("  otherwise to JAVABYTES_ALLOWED_DIRS, otherwise to the working directory plus the\n");
        md.append("  JVM temporary directory. Paths outside the current sandbox are rejected.\n");
        md.append("- Database tools are read-only: one SELECT/SHOW/DESCRIBE/EXPLAIN/WITH statement,\n");
        md.append("  capped at 1000 rows with a query timeout.\n\n");
        md.append("Workflow:\n");
        md.append("1. Inspect before changing: get_version, list_services, get_file_info, read_text_file,\n");
        md.append("   search_files.\n");
        md.append("2. Prefer evidence over guesses. project_diagnostic and analyze_springboot_project\n");
        md.append("   report what they actually found; they do not invent missing dependencies.\n");
        md.append("3. Generation tools emit templates. They do not compile, run, or verify their output.\n");
        md.append("   Write the result and verify it with your own build or test command.\n");
        md.append("4. Keep file edits inside the sandbox; ask the user for a new root instead of escaping it.\n\n");
        md.append("Tool groups:\n");
        md.append("- Enabled by default: files, database, system, json, javafx, analysis, diagnostics.\n");
        md.append("- Opt-in generators: ui, design, codegen, springboot.\n");
        md.append("Enable groups or individual tools with JAVABYTES_ENABLE=springboot,codegen (or \"all\");\n");
        md.append("disable with JAVABYTES_DISABLE=<tool-or-group>. A tool disabled by name always wins.\n");
        return md.toString();
    }
}