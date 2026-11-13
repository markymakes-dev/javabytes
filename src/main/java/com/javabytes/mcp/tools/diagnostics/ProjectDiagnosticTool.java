package com.javabytes.mcp.tools.diagnostics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.config.AppConfig;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.PathValidator;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Read-only project health check. Dependencies are only reported missing when
 * the source actually uses them.
 */
public class ProjectDiagnosticTool implements Tool {

    @Override
    public String getName() { return "project_diagnostic"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String projectPath = params.has("projectPath") ? params.get("projectPath").asText() : ".";
        try {
            Path path = Path.of(projectPath);
            if (!PathValidator.isAllowed(path)) {
                return ErrorHelper.error("PATH_NOT_ALLOWED",
                        "Path not allowed: " + projectPath + ". Allowed roots: "
                                + AppConfig.describeAllowedDirectories());
            }
            if (!Files.exists(path) || !Files.isDirectory(path)) {
                return ErrorHelper.error("PATH_NOT_FOUND", "Project directory not found: " + projectPath);
            }

            ObjectNode inspection = ProjectInspector.inspect(path);
            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.setAll(inspection);
            result.put("projectHealthy", inspection.get("healthy").asBoolean());
            result.put("message", inspection.get("healthy").asBoolean()
                    ? "No dependency or configuration problems detected"
                    : inspection.get("issueCount").asInt() + " issue(s) found - see issues");
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("DIAGNOSTIC_ERROR", "Error diagnosing project: " + e.getMessage());
        }
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("projectPath", new Tool.StringSchema().description("Path to the project directory (default: working directory)"));
        return schema;
    }
}
