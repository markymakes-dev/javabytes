package com.javabytes.mcp.skills.diagnostics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.config.AppConfig;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.PathValidator;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Aggregates project diagnostics. Like {@link FixMavenDependenciesSkill}, it is
 * advisory and does not modify the project; callers apply the suggested fixes.
 */
public class FixAllSkill implements Skill {

    @Override
    public String getName() { return "fix_all"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String projectPath = params.has("projectPath") ? params.get("projectPath").asText() : ".";
        boolean autoFix = params.has("autoFix") && params.get("autoFix").asBoolean();

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
            ArrayNode issues = (ArrayNode) inspection.get("issues");
            ArrayNode fixes = JsonUtils.MAPPER.createArrayNode();
            for (JsonNode issue : issues) {
                ObjectNode fix = JsonUtils.MAPPER.createObjectNode();
                fix.put("code", issue.get("code").asText());
                fix.put("severity", issue.get("severity").asText());
                fix.put("description", issue.get("description").asText());
                fix.put("suggestion", issue.get("suggestion").asText());
                fix.put("status", "actions_required");
                fixes.add(fix);
            }

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("projectPath", path.toAbsolutePath().toString());
            result.put("autoFix", autoFix);
            result.set("maven", inspection.get("maven"));
            result.set("source", inspection.get("source"));
            result.set("allFixes", fixes);
            result.put("totalFixes", fixes.size());
            result.put("readyToBuild", fixes.size() == 0);
            result.put("applied", false);
            result.put("message", fixes.isEmpty()
                    ? "No build or dependency problems detected"
                    : fixes.size() + " issue(s) found - review the suggestions and apply them manually");
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("FIX_ALL_ERROR", "Error running fix_all: " + e.getMessage());
        }
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.prop("projectPath", new Skill.StringSchema().description("Path to the project directory"));
        schema.prop("autoFix", new Skill.BoolSchema().description("Accepted for compatibility; fixes are still advisory"));
        return schema;
    }
}
