package com.javabytes.mcp.tools.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

import java.util.*;

public class GetSystemInfoTool implements Tool {

    @Override
    public String getName() { return "get_system_info"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "get_system_info",
                "Get System Info",
                "Report JVM, operating-system and hardware information.",
                "system",
                true,
                true,
                false,
                true,
                true);
    }

    @Override
    public JsonNode execute(JsonNode params) {
        try {
            Runtime rt = Runtime.getRuntime();
            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);

            ObjectNode sys = JsonUtils.MAPPER.createObjectNode();
            sys.put("os.name", System.getProperty("os.name"));
            sys.put("os.version", System.getProperty("os.version"));
            sys.put("os.arch", System.getProperty("os.arch"));
            sys.put("processors", rt.availableProcessors());
            result.set("system", sys);

            ObjectNode mem = JsonUtils.MAPPER.createObjectNode();
            mem.put("total", rt.totalMemory());
            mem.put("free", rt.freeMemory());
            mem.put("max", rt.maxMemory());
            result.set("memory", mem);

            ObjectNode java = JsonUtils.MAPPER.createObjectNode();
            java.put("version", System.getProperty("java.version"));
            java.put("home", System.getProperty("java.home"));
            result.set("java", java);

            ObjectNode user = JsonUtils.MAPPER.createObjectNode();
            user.put("name", System.getProperty("user.name"));
            user.put("home", System.getProperty("user.home"));
            user.put("dir", System.getProperty("user.dir"));
            result.set("user", user);

            return result;
        } catch (Exception e) {
            return ErrorHelper.error("SYS_ERROR", e.getMessage());
        }
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        return new Tool.JsonSchema();
    }
}
