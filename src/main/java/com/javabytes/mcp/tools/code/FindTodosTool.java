package com.javabytes.mcp.tools.code;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FindTodosTool implements Tool {

    @Override
    public String getName() { return "find_todos"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "find_todos",
                "Find TODO Markers",
                "Find TODO, FIXME and HACK markers in a source tree.",
                "analysis",
                true,
                true,
                false,
                true,
                false);
    }

    @Override
    public JsonNode execute(JsonNode params) {
        String code = params.has("code") ? params.get("code").asText() : "";
        String filePath = params.has("filePath") ? params.get("filePath").asText() : "unknown";

        try {
            ArrayNode todos = JsonUtils.MAPPER.createArrayNode();
            Pattern todoPattern = Pattern.compile("(//|#|--|\\*)\\s*(TODO|FIXME|XXX|HACK|OPTIMIZE)\\s*[:\\-]?\\s*(.*)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = todoPattern.matcher(code);
            int lineNum = 0;
            for (String line : code.split("\\r?\\n")) {
                lineNum++;
                Matcher lineMatcher = todoPattern.matcher(line);
                while (lineMatcher.find()) {
                    ObjectNode todo = JsonUtils.MAPPER.createObjectNode();
                    todo.put("line", lineNum);
                    todo.put("type", lineMatcher.group(2).toUpperCase());
                    todo.put("message", lineMatcher.group(3).trim());
                    todo.put("filePath", filePath);
                    todos.add(todo);
                }
            }

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("filePath", filePath);
            result.set("todos", todos);
            result.put("todoCount", todos.size());
            result.put("message", "Found " + todos.size() + " TODO/FIXME items");
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("FIND_TODOS_ERROR", "Error finding TODOs: " + e.getMessage());
        }
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("code", new Tool.StringSchema().description("Source code to search for TODOs"));
        schema.prop("filePath", new Tool.StringSchema().description("File path"));
        schema.required("code");
        return schema;
    }
}
