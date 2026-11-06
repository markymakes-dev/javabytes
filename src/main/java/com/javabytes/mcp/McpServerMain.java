package com.javabytes.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.config.AppConfig;
import com.javabytes.mcp.config.FeatureConfiguration;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.skills.database.*;
import com.javabytes.mcp.skills.design.*;
import com.javabytes.mcp.skills.diagnostics.*;
import com.javabytes.mcp.skills.file.*;
import com.javabytes.mcp.skills.javafx.*;
import com.javabytes.mcp.skills.json.JsonQuerySkill;
import com.javabytes.mcp.skills.code.*;
import com.javabytes.mcp.skills.system.*;
import com.javabytes.mcp.skills.springboot.*;
import com.javabytes.mcp.skills.ui.*;
import com.javabytes.mcp.skills.web.*;
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

        // File skills (10)
        registerSkill(registry, new ReadTextFileSkill());
        registerSkill(registry, new WriteFileSkill());
        registerSkill(registry, new ListDirectorySkill());
        registerSkill(registry, new GetFileInfoSkill());
        registerSkill(registry, new MakeDirectorySkill());
        registerSkill(registry, new DeleteFileSkill());
        registerSkill(registry, new SearchFilesSkill());
        registerSkill(registry, new BackupFileSkill());
        registerSkill(registry, new CompareFilesSkill());
        registerSkill(registry, new EnvironmentVariablesSkill());

        // Database skills (8)
        registerSkill(registry, new QueryDatabaseSkill());
        registerSkill(registry, new ListTablesSkill());
        registerSkill(registry, new DescribeTableSkill());
        registerSkill(registry, new ListIndexesSkill());
        registerSkill(registry, new ListForeignKeysSkill());
        registerSkill(registry, new ExplainQuerySkill());
        registerSkill(registry, new ExportSchemaDdlSkill());
        registerSkill(registry, new AnalyzeSchemaNormalizationSkill());

        // System skills (4)
        registerSkill(registry, new GetSystemInfoSkill());
        registerSkill(registry, new ConvertTimeSkill());
        registerSkill(registry, new ListServicesSkill(registry::listSkills));
        registerSkill(registry, new GetVersionSkill());

        // JSON skills (1)
        registerSkill(registry, new JsonQuerySkill());

        // JavaScript / web migration skills (3)
        registerSkill(registry, new AnalyzeJavaScriptSkill());
        registerSkill(registry, new TranslateJavaScriptSkill());
        registerSkill(registry, new TranslateWebappSkill());

        // JavaFX skills (5)
        registerSkill(registry, new InitCanvasSkill());
        registerSkill(registry, new DrawCanvasSkill());
        registerSkill(registry, new ClearCanvasSkill());
        registerSkill(registry, new ExportCanvasSkill());
        registerSkill(registry, new SetColorSkill());

        // UI skills (6)
        registerSkill(registry, new GenerateUICodeSkill());
        registerSkill(registry, new GenerateCSSSkill());
        registerSkill(registry, new ExtractDesignSystemSkill());
        registerSkill(registry, new HtmlCssToJavaFxCssSkill());
        registerSkill(registry, new GenerateJavaFxComponentSkill());
        registerSkill(registry, new AnalyzeUiLayoutSkill());

        // Design skills (2)
        registerSkill(registry, new DesignTokensSkill());
        registerSkill(registry, new AntiSlopReviewSkill());

        // AI Coding skills (7)
        registerSkill(registry, new AnalyzeCodeSkill());
        registerSkill(registry, new GenerateCodeSkill());
        registerSkill(registry, new GenerateTestsSkill());
        registerSkill(registry, new GenerateDocsSkill());
        registerSkill(registry, new RefactorCodeSkill());
        registerSkill(registry, new CheckSecuritySkill());
        registerSkill(registry, new FindTodosSkill());

        // Spring Boot skills (20)
        registerSkill(registry, new GenerateSpringBootEntitySkill());
        registerSkill(registry, new GenerateSpringBootRepositorySkill());
        registerSkill(registry, new GenerateSpringBootServiceSkill());
        registerSkill(registry, new GenerateSpringBootControllerSkill());
        registerSkill(registry, new GenerateSpringBootPropertiesSkill());
        registerSkill(registry, new GenerateSpringBootDTOskill());
        registerSkill(registry, new AnalyzeSpringBootProjectSkill());
        registerSkill(registry, new GenerateSpringBootSecuritySkill());
        registerSkill(registry, new GenerateSpringBootRelationshipSkill());
        registerSkill(registry, new GenerateSpringBootActuatorSkill());
        registerSkill(registry, new GenerateSpringBootExceptionHandlerSkill());
        registerSkill(registry, new GenerateSpringBootValidationSkill());
        registerSkill(registry, new GenerateSpringBootPaginationSkill());
        registerSkill(registry, new GenerateSpringBootFileUploadSkill());
        registerSkill(registry, new GenerateSpringBootScheduledSkill());
        registerSkill(registry, new GenerateSpringBootAsyncSkill());
        registerSkill(registry, new GenerateSpringBootCacheSkill());
        registerSkill(registry, new GenerateSpringBootProfileSkill());
        registerSkill(registry, new GenerateSpringBootDockerSkill());
        registerSkill(registry, new GenerateSpringBootTestSkill());

        // Diagnostic skills (5)
        registerSkill(registry, new ProjectDiagnosticSkill());
        registerSkill(registry, new FixMavenDependenciesSkill());
        registerSkill(registry, new CreateMyDatabaseClientSkill());
        registerSkill(registry, new FixJavaFXHeadlessSkill());
        registerSkill(registry, new FixAllSkill());

        System.err.println("JavaBytes MCP Server starting... v" + AppConfig.SERVER_VERSION);
        System.err.println("Registered tools: " + registry.getSkillCount()
                + " of " + ToolMetadata.catalog().size() + " (" + FEATURES.describe() + ")");
        System.err.println("Sandbox roots: " + AppConfig.describeAllowedDirectories());

        startServer(registry);
        // The JavaFX toolkit, if it was started for canvas rendering, uses a
        // non-daemon thread. Exit explicitly once stdin closes.
        System.exit(0);
    }

    private static void registerSkill(McpServerRegistry registry, Skill skill) {
        if (!FEATURES.isEnabled(skill.getName())) {
            return;
        }
        registry.register(skill);
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
                        for (String name : registry.listSkills()) {
                            tools.add(toolDescriptor(registry.getSkill(name)));
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
                        Skill skill = registry.getSkill(toolName);
                        if (skill == null) {
                            writeResponse(-32602, unknownToolMessage(toolName), id, System.out);
                            continue;
                        }
                        try {
                            JsonNode skillResult = registry.execute(toolName, args);
                            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
                            ArrayNode content = JsonUtils.MAPPER.createArrayNode();
                            ObjectNode text = JsonUtils.MAPPER.createObjectNode();
                            text.put("type", "text");
                            text.put("text", JsonUtils.MAPPER.writeValueAsString(skillResult));
                            content.add(text);
                            result.set("content", content);
                            result.put("isError", skillResult.has("success") && !skillResult.get("success").asBoolean());
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
    static ObjectNode toolDescriptor(Skill skill) {
        ObjectNode tool = JsonUtils.MAPPER.createObjectNode();
        tool.put("name", skill.getName());
        tool.put("description", ToolDescriptions.describe(skill.getName()));

        ToolMetadata.Metadata metadata = ToolMetadata.get(skill.getName());
        tool.put("title", metadata.title());

        ObjectNode schema = JsonUtils.MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = JsonUtils.MAPPER.createObjectNode();
        Skill.JsonSchema input = skill.getInputSchema();
        for (java.util.Map.Entry<String, Object> e : input.getProperties().entrySet()) {
            ObjectNode prop = JsonUtils.MAPPER.createObjectNode();
            if (e.getValue() instanceof Skill.StringSchema s) {
                prop.put("type", "string");
                if (s.getDescription() != null) prop.put("description", s.getDescription());
            } else if (e.getValue() instanceof Skill.BoolSchema b) {
                prop.put("type", "boolean");
                if (b.getDescription() != null) prop.put("description", b.getDescription());
            } else if (e.getValue() instanceof Skill.NumberSchema n) {
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
