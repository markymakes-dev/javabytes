package com.javabytes.mcp.tools.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

/** Query JSON data using dot-notation path */
public class JsonQueryTool implements Tool {

    private final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getName() { return "json_query"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "json_query",
                "Query JSON",
                "Query a JSON document with a dot/bracket path and optionally return pretty output.",
                "json",
                true,
                true,
                false,
                true,
                false);
    }

    @Override
    public JsonNode execute(JsonNode params) {
        String jsonInput = params.has("json") ? params.get("json").asText() : "";
        String query = params.has("query") ? params.get("query").asText() : "";
        try {
            JsonNode root = JsonUtils.MAPPER.readTree(jsonInput);
            JsonNode result = root;
            if (query != null && !query.isEmpty()) {
                String[] parts = query.split("\\.");
                for (String part : parts) {
                    if (result == null || !result.isObject()) {
                        return ErrorHelper.error("QUERY_ERROR", "Cannot navigate into non-object at: " + part);
                    }
                    result = result.get(part);
                    if (result == null) {
                        ObjectNode errorResult = JsonUtils.MAPPER.createObjectNode();
                        errorResult.put("success", true);
                        errorResult.put("found", false);
                        errorResult.put("query", query);
                        errorResult.put("message", "Path not found: " + part);
                        return errorResult;
                    }
                }
            }
            ObjectNode response = JsonUtils.MAPPER.createObjectNode();
            response.put("success", true);
            response.put("query", query);
            response.put("found", true);
            response.set("result", result);
            return response;
        } catch (Exception e) {
            return ErrorHelper.error("JSON_ERROR", "Error querying JSON: " + e.getMessage());
        }
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.property("json", new Tool.StringSchema().description("JSON string to query"));
        schema.property("query", new Tool.StringSchema().description("Dot-notation path query (e.g., 'users.0.name')"));
        schema.required("json");
        return schema;
    }
}




