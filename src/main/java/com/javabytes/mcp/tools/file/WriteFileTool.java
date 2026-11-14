package com.javabytes.mcp.tools.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.PathValidator;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public class WriteFileTool implements Tool {

    @Override
    public String getName() { return "write_file"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "write_file",
                "Write Text File",
                "Create or overwrite a text file inside an allowed directory.",
                "files",
                true,
                false,
                true,
                true,
                false);
    }

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
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("path", new Tool.StringSchema().description("File path to write"));
        schema.prop("content", new Tool.StringSchema().description("Content to write"));
        schema.required("path");
        schema.required("content");
        return schema;
    }
}
