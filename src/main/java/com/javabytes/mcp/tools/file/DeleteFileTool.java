package com.javabytes.mcp.tools.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.PathValidator;

import java.nio.file.*;

public class DeleteFileTool implements Tool {

    @Override
    public String getName() { return "delete_file"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "delete_file",
                "Delete File",
                "Delete a file or an empty directory inside an allowed directory.",
                "files",
                true,
                false,
                true,
                false,
                false);
    }

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
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("path", new Tool.StringSchema().description("File path"));
        schema.required("path");
        return schema;
    }
}
