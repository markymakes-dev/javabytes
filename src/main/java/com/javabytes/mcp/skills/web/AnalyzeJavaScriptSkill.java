package com.javabytes.mcp.skills.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.config.AppConfig;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.PathValidator;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Inventories a JavaScript/TypeScript file or project and reports the real
 * migration surface: frameworks, npm dependencies, routes, components, browser
 * APIs, and constructs with no mechanical Java equivalent.
 */
public class AnalyzeJavaScriptSkill implements Skill {

    @Override
    public String getName() { return "analyze_javascript"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String code = params.has("code") && !params.get("code").isNull()
                ? params.get("code").asText()
                : null;
        String pathParam = params.has("path") ? params.get("path").asText().trim() : "";
        int maxFiles = params.has("max_files") ? params.get("max_files").asInt(500) : 500;

        try {
            if (code != null) {
                String fileName = params.has("file_name")
                        ? params.get("file_name").asText("input.js")
                        : "input.js";
                ObjectNode analysis = JavaScriptAnalyzer.enrichSource(
                        JavaScriptAnalyzer.analyzeSource(code, fileName));
                analysis.put("success", true);
                return analysis;
            }

            if (pathParam.isEmpty()) {
                return ErrorHelper.error("MISSING_PARAM",
                        "Provide either 'path' (file or directory) or 'code'");
            }

            Path path = Path.of(pathParam);
            if (!PathValidator.isAllowed(path)) {
                return ErrorHelper.error("PATH_NOT_ALLOWED",
                        "Path not allowed: " + pathParam + ". Allowed roots: "
                                + AppConfig.describeAllowedDirectories());
            }
            if (!Files.exists(path)) {
                return ErrorHelper.error("PATH_NOT_FOUND", "Path not found: " + pathParam);
            }

            ObjectNode result;
            if (Files.isDirectory(path)) {
                result = JavaScriptAnalyzer.analyzeProject(path, maxFiles);
            } else {
                result = JavaScriptAnalyzer.enrichSource(
                        JavaScriptAnalyzer.analyzeSource(Files.readString(path), path.toString()));
            }
            result.put("success", true);
            return result;
        } catch (Exception e) {
            return ErrorHelper.error("ANALYZE_ERROR",
                    "Error analyzing JavaScript: " + e.getMessage());
        }
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.prop("path", new Skill.StringSchema()
                .description("JavaScript/TypeScript file or project directory inside an allowed root"));
        schema.prop("code", new Skill.StringSchema()
                .description("Inline source to analyze instead of reading a path"));
        schema.prop("file_name", new Skill.StringSchema()
                .description("Virtual file name for inline code, e.g. app.tsx (default input.js)"));
        schema.prop("max_files", new Skill.NumberSchema()
                .integer()
                .minimum(1)
                .maximum(2000)
                .description("Maximum project files to scan (default 500)"));
        return schema;
    }
}
