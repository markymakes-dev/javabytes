package com.javabytes.mcp.skills.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.PathValidator;

import java.nio.file.*;

public class DeleteFileSkill implements Skill {

    @Override
    public String getName() { return "delete_file"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String path = params.has("path") ? params.get("path").asText() : "";
        try {
            Path p = Path.of(path);
            if (!PathValidator.isAllowed(p)) {
                return ErrorHelper.error("PATH_NOT_ALLOWED", "Path not allowed: " + path);
            }
            if (!Files.exists(p)) {
                return ErrorHelper.error("FILE_NOT_FOUND", "File not found: " + path);
            }
            Files.delete(p);
            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("path", path);
            return result;
        } catch (Exception e) {
            return ErrorHelper.error("DELETE_ERROR", "Error deleting file: " + e.getMessage());
        }
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.prop("path", new Skill.StringSchema().description("File path"));
        schema.required("path");
        return schema;
    }
}
