package com.javabytes.mcp.tools.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.PathValidator;

import java.nio.file.*;

public class MakeDirectoryTool implements Tool {

    @Override
    public String getName() { return "make_directory"; }

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
            boolean existed = Files.isDirectory(p);
            if (!existed && Files.exists(p)) {
                return ErrorHelper.error("NOT_A_DIRECTORY", "A file already exists at: " + path);
            }
            Files.createDirectories(p);
            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("path", p.toAbsolutePath().toString());
            result.put("created", !existed);
            result.put("already_existed", existed);
            return result;
        } catch (Exception e) {
            return ErrorHelper.error("MKDIR_ERROR", "Error creating directory: " + e.getMessage());
        }
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("path", new Tool.StringSchema().description("Directory path"));
        schema.required("path");
        return schema;
    }
}
