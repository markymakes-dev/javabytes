package com.javabytes.mcp.skills.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.PathValidator;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public class ReadTextFileSkill implements Skill {

    @Override
    public String getName() { return "read_text_file"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String path = params.has("path") ? params.get("path").asText() : "";
        try {
            Path p = Path.of(path);
            if (!PathValidator.isAllowed(p)) {
                return ErrorHelper.error("PATH_NOT_ALLOWED", "Path not allowed: " + path);
            }
            if (!Files.exists(p) || !Files.isRegularFile(p)) {
                return ErrorHelper.error("FILE_NOT_FOUND", "File not found: " + path);
            }
            String content = Files.readString(p);
            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("path", path);
            result.put("content", content);
            result.put("size_bytes", content.getBytes(StandardCharsets.UTF_8).length);
            return result;
        } catch (Exception e) {
            return ErrorHelper.error("READ_ERROR", "Error reading file: " + e.getMessage());
        }
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.prop("path", new Skill.StringSchema().description("File path to read"));
        schema.required("path");
        return schema;
    }
}
