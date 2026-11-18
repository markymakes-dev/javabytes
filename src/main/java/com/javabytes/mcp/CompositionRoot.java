package com.javabytes.mcp;

import com.javabytes.mcp.config.AppConfig;
import com.javabytes.mcp.config.FeatureConfiguration;
import com.javabytes.mcp.javafx.JavaFxPlatform;
import com.javabytes.mcp.protocol.ProtocolSession;
import com.javabytes.mcp.McpServerRegistry;
import com.javabytes.mcp.schema.types.SchemaRenderer;
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
import com.javabytes.mcp.transport.JsonRpcTransport;
import com.javabytes.mcp.utils.ToolMetadata;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Composition root: wires all components together.
 * Single place where the full tool catalog is declared and the server is started.
 * <p>
 * Replaces the monolithic {@code McpServerMain} responsibilities:
 * - Tool declaration (declareAllTools)
 * - Feature configuration derivation
 * - Transport + protocol session creation
 * - JavaFX platform init
 * - Main entry point
 */
public class CompositionRoot {

    /** Tools declared on this JVM, in declaration order (ADR 0006). */
    private final List<Tool> toolInstances = new ArrayList<>();

    /** Feature configuration, parsed once the full catalog exists (ADR 0006). */
    private FeatureConfiguration features;

    /** The protocol session handling initialize, tools/list, tools/call, roots. */
    private ProtocolSession protocolSession;

    /** The JSON-RPC transport reading from stdin, writing to stdout. */
    private JsonRpcTransport transport;

    /** Singleton instance for backward-compatible static accessors. */
    private static CompositionRoot instance;

    /**
     * Create and wire the complete server.
     */
    public CompositionRoot() {
        // 1. Initialize JavaFX platform (Monocle for headless)
        JavaFxPlatform.init();

        // 2. Declare all tools
        McpServerRegistry registry = new McpServerRegistry();
        declareAllTools(registry);

        // 3. Derive feature configuration from declared tools (ADR 0006)
        ToolMetadata.derive(toolInstances);
        this.features = FeatureConfiguration.fromEnvironment();

        // 4. Register enabled tools
        for (Tool tool : toolInstances) {
            if (features.isEnabled(tool.getName())) {
                registry.register(tool);
            }
        }

        // 5. Create schema renderer (wire format for tool descriptors)
        SchemaRenderer schemaRenderer = new SchemaRenderer();

        // 6. Create protocol session (pure handler, no I/O)
        this.protocolSession = new ProtocolSession(registry, features, schemaRenderer);

        // 7. Create transport (stdio loop)
        PrintStream out = System.out;
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        this.transport = new JsonRpcTransport(protocolSession, out, reader);

        // 8. Store singleton for static accessors
        instance = this;

        System.err.println("JavaBytes MCP Server starting... v" + AppConfig.SERVER_VERSION);
        System.err.println("Registered tools: " + registry.getToolCount()
                + " of " + ToolMetadata.catalog().size() + " (" + features.describe() + ")");
        System.err.println("Sandbox roots: " + AppConfig.describeAllowedDirectories());
    }

    /**
     * Start the server (blocks until stdin closes).
     */
    public void start() {
        transport.run();
        // Transport.run() returns when stdin closes.
        // JVM exits naturally (Monocle uses daemon threads).
        // No System.exit(0) needed.
    }

    /**
     * Get the singleton instance (for backward-compatible static accessors).
     */
    public static CompositionRoot getInstance() {
        return instance;
    }

    // --- Backward-compatible accessors ---

    /**
     * Every declared tool, in startup order. Idempotent per JVM.
     * Delegates to singleton instance.
     */
    public static synchronized List<Tool> declaredTools() {
        if (instance == null) {
            new CompositionRoot(); // Initialize singleton
        }
        return instance.getToolInstances();
    }

    /**
     * The catalog-derived feature configuration.
     * Delegates to singleton instance.
     */
    public static synchronized FeatureConfiguration features() {
        if (instance == null) {
            new CompositionRoot(); // Initialize singleton
        }
        return instance.getFeatures();
    }

    // --- Tool declaration (identical to old McpServerMain.declareAllTools) ---

    private void declareAllTools(McpServerRegistry registry) {
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

        // Database tools (11)
        registerTool(registry, new QueryDatabaseTool());
        registerTool(registry, new ListTablesTool());
        registerTool(registry, new DescribeTableTool());
        registerTool(registry, new ListIndexesTool());
        registerTool(registry, new ListForeignKeysTool());
        registerTool(registry, new ExplainQueryTool());
        registerTool(registry, new ExportSchemaDdlTool());
        registerTool(registry, new AnalyzeSchemaNormalizationTool());
        registerTool(registry, new ExtractDataModelsTool());
        registerTool(registry, new ModelsToSchemaTool());
        registerTool(registry, new GenerateDatabaseTool());

        // System tools (4)
        registerTool(registry, new GetSystemInfoTool());
        registerTool(registry, new ConvertTimeTool());
        registerTool(registry, new ListServicesTool(registry::listTools));
        registerTool(registry, new GetVersionTool());

        // JSON tools (1)
        registerTool(registry, new JsonQueryTool());

        // JavaScript / web migration tools (5)
        registerTool(registry, new AnalyzeJavaScriptTool());
        registerTool(registry, new TranslateJavaScriptTool());
        registerTool(registry, new TranslateWebappTool());
        registerTool(registry, new MigratePlanTool());
        registerTool(registry, new MigrateRecordTool());

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

        // Diagnostic tools (6)
        registerTool(registry, new ProjectDiagnosticTool());
        registerTool(registry, new FixMavenDependenciesTool());
        registerTool(registry, new CreateMyDatabaseClientTool());
        registerTool(registry, new FixJavaFXHeadlessTool());
        registerTool(registry, new FixAllTool());
        registerTool(registry, new VerifyGeneratedTool());
    }

    /** Declares a tool; registration happens once the catalog is derived. */
    private void registerTool(McpServerRegistry registry, Tool tool) {
        toolInstances.add(tool);
    }

    // --- Getters for backward compatibility ---

    List<Tool> getToolInstances() {
        return List.copyOf(toolInstances);
    }

    FeatureConfiguration getFeatures() {
        return features;
    }

    /**
     * Main entry point.
     */
    public static void main(String[] args) {
        CompositionRoot root = new CompositionRoot();
        root.start();
    }
}