package com.javabytes.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.config.AppConfig;
import com.javabytes.mcp.config.FeatureConfiguration;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.database.*;
import com.javabytes.mcp.tools.design.*;
import com.javabytes.mcp.tools.diagnostics.*;
import com.javabytes.mcp.tools.file.*;
import com.javabytes.mcp.tools.javafx.*;
import com.javabytes.mcp.tools.json.JsonQueryTool;
import com.javabytes.mcp.tools.code.*;
import com.javabytes.mcp.tools.system.*;
import com.javabytes.mcp.tools.springboot.*;
import com.javabytes.mcp.tools.ui.*;
import com.javabytes.mcp.tools.web.*;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.ProtocolLogger;
import com.javabytes.mcp.utils.Roots;
import com.javabytes.mcp.utils.ToolDescriptions;
import com.javabytes.mcp.utils.ToolMetadata;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class McpServerMain {

    /**
     * Protocol versions this server understands, newest first. Mirrors the
     * official MCP Java SDK's {@code ProtocolVersions}.
     */
    private static final List<String> SUPPORTED_PROTOCOL_VERSIONS = List.of(
            "2025-11-25",
            "2025-06-18",
            "2025-03-26",
            "2024-11-05");

    /** Which tools are registered, based on JAVABYTES_ENABLE/JAVABYTES_DISABLE. */
    private static final FeatureConfiguration FEATURES = FeatureConfiguration.fromEnvironment();

    /**
     * Workflow guidance returned from initialize. This is the place to explain
     * how the tools relate, rather than repeating each tool's description.
     */
    private static final String INSTRUCTIONS = """
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

    static {
        System.setProperty("javafx.platform", "monocle");
    }

    public static void main(String[] args) {
        McpServerRegistry registry = new McpServerRegistry();

        // File tools (10)
        registerTool(registry, new ReadTextFileTool());
        registerTool(registry, new WriteFileTool());
        registerTool(registry, new ListDirectoryTool());
        registerTool(registry, new GetFileInfoTool());
        registerTool(registry, new MakeDirectoryTool());
        registerTool(registry, new DeleteFileTool());
        registerTool(registry, new SearchFilesTool());
        registerTool(registry, new BackupFileTool());
        registerTool(registry, new CompareFilesTool());
        registerTool(registry, new EnvironmentVariablesTool());

        // Database tools (8)
        registerTool(registry, new QueryDatabaseTool());
        registerTool(registry, new ListTablesTool());
        registerTool(registry, new DescribeTableTool());
        registerTool(registry, new ListIndexesTool());
        registerTool(registry, new ListForeignKeysTool());
        registerTool(registry, new ExplainQueryTool());
        registerTool(registry, new ExportSchemaDdlTool());
        registerTool(registry, new AnalyzeSchemaNormalizationTool());

        // System tools (4)
        registerTool(registry, new GetSystemInfoTool());
        registerTool(registry, new ConvertTimeTool());
        registerTool(registry, new ListServicesTool(registry::listTools));
        registerTool(registry, new GetVersionTool());

        // JSON tools (1)
        registerTool(registry, new JsonQueryTool());

        // JavaScript / web migration tools (3)
        registerTool(registry, new AnalyzeJavaScriptTool());
        registerTool(registry, new TranslateJavaScriptTool());
        registerTool(registry, new TranslateWebappTool());

        // JavaFX tools (5)
        registerTool(registry, new InitCanvasTool());
        registerTool(registry, new DrawCanvasTool());
        registerTool(registry, new ClearCanvasTool());
        registerTool(registry, new ExportCanvasTool());
        registerTool(registry, new SetColorTool());

        // UI tools (6)
        registerTool(registry, new GenerateUICodeTool());
        registerTool(registry, new GenerateCSSTool());
        registerTool(registry, new ExtractDesignSystemTool());
        registerTool(registry, new HtmlCssToJavaFxCssTool());
        registerTool(registry, new GenerateJavaFxComponentTool());
        registerTool(registry, new AnalyzeUiLayoutTool());

        // Design tools (2)
        registerTool(registry, new DesignTokensTool());
        registerTool(registry, new AntiSlopReviewTool());

        // AI Coding tools (7)
        registerTool(registry, new AnalyzeCodeTool());
        registerTool(registry, new GenerateCodeTool());
        registerTool(registry, new GenerateTestsTool());
        registerTool(registry, new GenerateDocsTool());
        registerTool(registry, new RefactorCodeTool());
        registerTool(registry, new CheckSecurityTool());
        registerTool(registry, new FindTodosTool());

        // Spring Boot tools (20)
        registerTool(registry, new GenerateSpringBootEntityTool());
        registerTool(registry, new GenerateSpringBootRepositoryTool());
        registerTool(registry, new GenerateSpringBootServiceTool());
        registerTool(registry, new GenerateSpringBootControllerTool());
        registerTool(registry, new GenerateSpringBootPropertiesTool());
        registerTool(registry, new GenerateSpringBootDTOTool());
        registerTool(registry, new AnalyzeSpringBootProjectTool());
        registerTool(registry, new GenerateSpringBootSecurityTool());
        registerTool(registry, new GenerateSpringBootRelationshipTool());
        registerTool(registry, new GenerateSpringBootActuatorTool());
        registerTool(registry, new GenerateSpringBootExceptionHandlerTool());
        registerTool(registry, new GenerateSpringBootValidationTool());
        registerTool(registry, new GenerateSpringBootPaginationTool());
        registerTool(registry, new GenerateSpringBootFileUploadTool());
        registerTool(registry, new GenerateSpringBootScheduledTool());
        registerTool(registry, new GenerateSpringBootAsyncTool());
        registerTool(registry, new GenerateSpringBootCacheTool());
        registerTool(registry, new GenerateSpringBootProfileTool());
        registerTool(registry, new GenerateSpringBootDockerTool());
        registerTool(registry, new GenerateSpringBootTestTool());

        // Diagnostic tools (5)
        registerTool(registry, new ProjectDiagnosticTool());
        registerTool(registry, new FixMavenDependenciesTool());
        registerTool(registry, new CreateMyDatabaseClientTool());
        registerTool(registry, new FixJavaFXHeadlessTool());
        registerTool(registry, new FixAllTool());

        System.err.println("JavaBytes MCP Server starting... v" + AppConfig.SERVER_VERSION);
        System.err.println("Registered tools: " + registry.getToolCount()
                + " of " + ToolMetadata.catalog().size() + " (" + FEATURES.describe() + ")");
        System.err.println("Sandbox roots: " + AppConfig.describeAllowedDirectories());

        startServer(registry);
        // The JavaFX toolkit, if it was started for canvas rendering, uses a
        // non-daemon thread. Exit explicitly once stdin closes.
        System.exit(0);
    }

    private static void registerTool(McpServerRegistry registry, Tool tool) {
        if (!FEATURES.isEnabled(tool.getName())) {
            return;
        }
        registry.register(tool);
    }

    /**
     * Real MCP stdio transport: reads JSON-RPC 2.0 requests line-by-line from
     * stdin, writes responses to stdout. stdout is reserved ONLY for protocol
     * responses; all logging goes to stderr.
     */
    private static void startServer(McpServerRegistry registry) {
        boolean clientSupportsRoots = false;
        String pendingRootsRequestId = null;
        AtomicInteger rootsRequestCounter = new AtomicInteger();

        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                ProtocolLogger.log("<--", line);

                JsonNode msg;
                try {
                    msg = JsonUtils.MAPPER.readTree(line);
                } catch (Exception e) {
                    writeResponse(-32700, "Parse error", null, System.out);
                    continue;
                }

                // A message without a method is a response to a server-initiated
                // request. Only roots/list responses are expected.
                if (!msg.has("method")) {
                    if (msg.has("id") && pendingRootsRequestId != null
                            && pendingRootsRequestId.equals(msg.get("id").asText())) {
                        pendingRootsRequestId = null;
                        handleRootsResponse(msg);
                    }
                    continue;
                }

                String method = msg.get("method").asText("");
                boolean isNotification = !msg.has("id");
                JsonNode id = isNotification ? null : msg.get("id");

                switch (method) {
                    case "initialize" -> {
                        String requested = msg.at("/params/protocolVersion").asText("");
                        String negotiated = SUPPORTED_PROTOCOL_VERSIONS.contains(requested)
                                ? requested
                                : SUPPORTED_PROTOCOL_VERSIONS.get(0);
                        clientSupportsRoots = msg.at("/params/capabilities/roots").isObject();

                        ObjectNode result = JsonUtils.MAPPER.createObjectNode();
                        result.put("protocolVersion", negotiated);
                        ObjectNode capabilities = JsonUtils.MAPPER.createObjectNode();
                        capabilities.set("tools", JsonUtils.MAPPER.createObjectNode());
                        result.set("capabilities", capabilities);
                        ObjectNode serverInfo = JsonUtils.MAPPER.createObjectNode();
                        serverInfo.put("name", "JavaBytes");
                        serverInfo.put("version", AppConfig.SERVER_VERSION);
                        result.set("serverInfo", serverInfo);
                        result.put("instructions", INSTRUCTIONS);
                        writeResult(result, id, System.out);
                    }
                    case "notifications/initialized" -> {
                        if (clientSupportsRoots) {
                            pendingRootsRequestId =
                                    sendRootsRequest(System.out, rootsRequestCounter);
                        }
                    }
                    case "notifications/roots/list_changed" -> {
                        if (clientSupportsRoots) {
                            pendingRootsRequestId =
                                    sendRootsRequest(System.out, rootsRequestCounter);
                        }
                    }
                    case "ping" -> writeResult(JsonUtils.MAPPER.createObjectNode(), id, System.out);
                    case "tools/list" -> {
                        ObjectNode result = JsonUtils.MAPPER.createObjectNode();
                        ArrayNode tools = JsonUtils.MAPPER.createArrayNode();
                        for (String name : registry.listTools()) {
                            tools.add(toolDescriptor(registry.getTool(name)));
                        }
                        result.set("tools", tools);
                        writeResult(result, id, System.out);
                    }
                    case "tools/call" -> {
                        String toolName = msg.at("/params/name").asText("");
                        JsonNode args = msg.at("/params/arguments");
                        if (args == null || args.isMissingNode() || args.isNull()) {
                            args = JsonUtils.MAPPER.createObjectNode();
                        }
                        if (!args.isObject()) {
                            writeResponse(-32602, "Tool arguments must be a JSON object", id, System.out);
                            continue;
                        }
                        Tool tool = registry.getTool(toolName);
                        if (tool == null) {
                            writeResponse(-32602, unknownToolMessage(toolName), id, System.out);
                            continue;
                        }
                        try {
                            JsonNode toolResult = registry.execute(toolName, args);
                            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
                            ArrayNode content = JsonUtils.MAPPER.createArrayNode();
                            ObjectNode text = JsonUtils.MAPPER.createObjectNode();
                            text.put("type", "text");
                            text.put("text", JsonUtils.MAPPER.writeValueAsString(toolResult));
                            content.add(text);
                            result.set("content", content);
                            result.put("isError", toolResult.has("success") && !toolResult.get("success").asBoolean());
                            writeResult(result, id, System.out);
                        } catch (Exception e) {
                            writeResponse(-32603, "Tool execution failed: " + e.getMessage(), id, System.out);
                        }
                    }
                    default -> {
                        if (!isNotification) {
                            writeResponse(-32601, "Method not found: " + method, id, System.out);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("MCP stdio loop terminated: " + e.getMessage());
        } finally {
            ProtocolLogger.close();
        }
        System.err.println("MCP Server stopped.");
    }

    static String unknownToolMessage(String toolName) {
        if (ToolMetadata.isKnown(toolName) && !FEATURES.isEnabled(toolName)) {
            return "Tool '" + toolName + "' is disabled by feature configuration (category: "
                    + ToolMetadata.get(toolName).category()
                    + "). Enable it with JAVABYTES_ENABLE=" + toolName
                    + " or JAVABYTES_ENABLE=" + ToolMetadata.get(toolName).category() + ".";
        }
        return "Unknown tool: " + toolName;
    }

    /**
     * Ask the client for its roots. The response is routed back by id in the
     * main loop; until then the environment fallback sandbox stays in effect.
     */
    private static String sendRootsRequest(PrintStream out, AtomicInteger counter) {
        String id = "javabytes-roots-" + counter.incrementAndGet();
        ObjectNode request = JsonUtils.MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", "roots/list");
        send(request, out);
        return id;
    }

    private static void handleRootsResponse(JsonNode msg) {
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

    /** Build a tools/list entry (name, title, description, schema, annotations). */
    static ObjectNode toolDescriptor(Tool toolImpl) {
        ObjectNode tool = JsonUtils.MAPPER.createObjectNode();
        tool.put("name", toolImpl.getName());
        tool.put("description", ToolDescriptions.describe(toolImpl.getName()));

        ToolMetadata.Metadata metadata = ToolMetadata.get(toolImpl.getName());
        tool.put("title", metadata.title());

        ObjectNode schema = JsonUtils.MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = JsonUtils.MAPPER.createObjectNode();
        Tool.JsonSchema input = toolImpl.getInputSchema();
        for (java.util.Map.Entry<String, Object> e : input.getProperties().entrySet()) {
            ObjectNode prop = JsonUtils.MAPPER.createObjectNode();
            if (e.getValue() instanceof Tool.StringSchema s) {
                prop.put("type", "string");
                if (s.getDescription() != null) prop.put("description", s.getDescription());
            } else if (e.getValue() instanceof Tool.BoolSchema b) {
                prop.put("type", "boolean");
                if (b.getDescription() != null) prop.put("description", b.getDescription());
            } else if (e.getValue() instanceof Tool.NumberSchema n) {
                prop.put("type", n.isInteger() ? "integer" : "number");
                if (n.getDescription() != null) prop.put("description", n.getDescription());
                if (n.getMinimum() != null) prop.put("minimum", n.getMinimum());
                if (n.getMaximum() != null) prop.put("maximum", n.getMaximum());
            } else {
                prop.put("type", "string");
            }
            properties.set(e.getKey(), prop);
        }
        schema.set("properties", properties);
        ArrayNode required = JsonUtils.MAPPER.createArrayNode();
        for (String r : input.getRequired()) required.add(r);
        schema.set("required", required);
        tool.set("inputSchema", schema);

        ObjectNode annotations = JsonUtils.MAPPER.createObjectNode();
        annotations.put("title", metadata.title());
        annotations.put("readOnlyHint", metadata.readOnly());
        annotations.put("destructiveHint", metadata.destructive());
        annotations.put("idempotentHint", metadata.idempotent());
        annotations.put("openWorldHint", metadata.openWorld());
        tool.set("annotations", annotations);
        return tool;
    }

    private static void writeResult(JsonNode result, JsonNode id, PrintStream out) {
        ObjectNode resp = JsonUtils.MAPPER.createObjectNode();
        resp.put("jsonrpc", "2.0");
        if (id != null) resp.set("id", id);
        resp.set("result", result);
        send(resp, out);
    }

    private static void writeResponse(int code, String message, JsonNode id, PrintStream out) {
        ObjectNode resp = JsonUtils.MAPPER.createObjectNode();
        resp.put("jsonrpc", "2.0");
        if (id != null) resp.set("id", id);
        ObjectNode error = JsonUtils.MAPPER.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        resp.set("error", error);
        send(resp, out);
    }

    private static void send(JsonNode message, PrintStream out) {
        String line = message.toString();
        ProtocolLogger.log("-->", line);
        out.println(line);
        out.flush();
    }
}
