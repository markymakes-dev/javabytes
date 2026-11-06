package com.javabytes.mcp.skills.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.PathValidator;

import java.nio.file.*;

public class GetFileInfoSkill implements Skill {

    @Override
    public String getName() { return "get_file_info"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String path = params.has("path") ? params.get("path").asText() : "";
        try {
            Path p = Path.of(path);
            if (!PathValidator.isAllowed(p)) {
                return ErrorHelper.error("PATH_NOT_ALLOWED",
                        "Path not allowed: " + path + ". Allowed roots: "
                                + com.javabytes.mcp.config.AppConfig.describeAllowedDirectories());
            }
            if (!Files.exists(p)) {
                return ErrorHelper.error("FILE_NOT_FOUND", "File not found: " + path);
            }
            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("path", path);
            result.put("exists", true);
            result.put("is_directory", Files.isDirectory(p));
            result.put("is_file", Files.isRegularFile(p));
            result.put("size_bytes", Files.isRegularFile(p) ? Files.size(p) : 0);
            result.put("last_modified", Files.getLastModifiedTime(p).toString());
            result.put("readable", Files.isReadable(p));
            result.put("writable", Files.isWritable(p));
            return result;
        } catch (Exception e) {
            return ErrorHelper.error("INFO_ERROR", "Error getting file info: " + e.getMessage());
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
