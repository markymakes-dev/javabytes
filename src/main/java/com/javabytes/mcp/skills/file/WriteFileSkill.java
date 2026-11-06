package com.javabytes.mcp.skills.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.PathValidator;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public class WriteFileSkill implements Skill {

    @Override
    public String getName() { return "write_file"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String path = params.has("path") ? params.get("path").asText() : "";
        if (path.isBlank()) {
            return ErrorHelper.error("MISSING_PARAM", "Parameter 'path' is required");
        }
        String content = params.has("content") ? params.get("content").asText() : "";
        try {
            Path p = Path.of(path);
            if (!PathValidator.isAllowed(p)) {
                return ErrorHelper.error("PATH_NOT_ALLOWED",
                        "Path not allowed: " + path + ". Allowed roots: "
                                + com.javabytes.mcp.config.AppConfig.describeAllowedDirectories());
            }
            if (p.getParent() != null) {
                Files.createDirectories(p.getParent());
            }
            Files.writeString(p, content);
            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("path", p.toAbsolutePath().toString());
            result.put("bytes_written", content.getBytes(StandardCharsets.UTF_8).length);
            return result;
        } catch (Exception e) {
            return ErrorHelper.error("WRITE_ERROR", "Error writing file: " + e.getMessage());
        }
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.prop("path", new Skill.StringSchema().description("File path to write"));
        schema.prop("content", new Skill.StringSchema().description("Content to write"));
        schema.required("path");
        schema.required("content");
        return schema;
    }
}
