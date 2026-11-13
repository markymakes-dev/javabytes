package com.javabytes.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

import java.util.*;

public class McpServerRegistry {

    private final Map<String, Tool> tools = new HashMap<>();

    public void register(Tool tool) {
        tools.put(tool.getName(), tool);
        // Only JSON-RPC responses may go to stdout; log to stderr
        System.err.println("Registered tool: " + tool.getName());
    }

    public Tool getTool(String toolName) {
        return tools.get(toolName);
    }

    public JsonNode execute(String toolName, JsonNode params) {
        Tool tool = tools.get(toolName);
        if (tool == null) {
            return ErrorHelper.error("SKILL_NOT_FOUND", "Tool '" + toolName + "' not registered");
        }
        try {
            return tool.execute(params);
        } catch (Exception e) {
            return ErrorHelper.error("SKILL_EXECUTION_ERROR", "Error executing '" + toolName + "': " + e.getMessage());
        }
    }

    public List<String> listTools() {
        return new ArrayList<>(tools.keySet());
    }

    public int getToolCount() {
        return tools.size();
    }

    public boolean hasTool(String toolName) {
        return tools.containsKey(toolName);
    }
}
