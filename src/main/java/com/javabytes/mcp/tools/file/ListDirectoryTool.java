package com.javabytes.mcp.tools.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.PathValidator;

import java.nio.file.*;
import java.util.*;

public class ListDirectoryTool implements Tool {

    @Override
    public String getName() { return "list_directory"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String path = params.has("path") ? params.get("path").asText() : "";
        try {
            Path p = Path.of(path);
            if (!PathValidator.isAllowed(p)) {
                return ErrorHelper.error("PATH_NOT_ALLOWED", "Path not allowed: " + path);
            }
            if (!Files.exists(p) || !Files.isDirectory(p)) {
                return ErrorHelper.error("NOT_A_DIRECTORY", "Not a directory: " + path);
            }
            ArrayNode entries = JsonUtils.MAPPER.createArrayNode();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(p)) {
                for (Path entry : stream) {
                    ObjectNode entryNode = JsonUtils.MAPPER.createObjectNode();
                    entryNode.put("name", entry.getFileName().toString());
                    entryNode.put("path", entry.toString());
                    entryNode.put("is_directory", Files.isDirectory(entry));
                    entryNode.put("size_bytes", Files.isDirectory(entry) ? 0 : Files.size(entry));
                    entries.add(entryNode);
                }
            }
            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("path", path);
            result.set("entries", entries);
            result.put("count", entries.size());
            return result;
        } catch (Exception e) {
            return ErrorHelper.error("LIST_ERROR", "Error listing directory: " + e.getMessage());
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
