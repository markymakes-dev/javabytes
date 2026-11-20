package com.javabytes.mcp.tools.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.config.AppConfig;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.JsonUtils;

public class GetVersionTool implements Tool {

    @Override
    public String getName() { return "get_version"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "get_version",
                "Get Server Version",
                "Return the JavaBytes server and Java runtime versions.",
                "system",
                true,
                true,
                false,
                true,
                false);
    }

    @Override
    public JsonNode execute(JsonNode params) {
        ObjectNode result = JsonUtils.MAPPER.createObjectNode();
        result.put("success", true);
        // AppConfig.SERVER_VERSION comes from the jar manifest (Implementation-Version,
        // set by the Maven Shade plugin) — never hardcode it here.
        result.put("version", AppConfig.SERVER_VERSION);
        result.put("java_version", System.getProperty("java.version"));
        return result;
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        return new Tool.JsonSchema();
    }
}
