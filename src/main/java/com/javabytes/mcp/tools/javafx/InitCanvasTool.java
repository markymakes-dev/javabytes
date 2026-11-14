package com.javabytes.mcp.tools.javafx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class InitCanvasTool implements Tool {

    @Override
    public String getName() { return "javafx_init_canvas"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "javafx_init_canvas",
                "Create Canvas",
                "Create an off-screen JavaFX canvas with the given width and height.",
                "javafx",
                true,
                false,
                false,
                false,
                false);
    }

    @Override
    public JsonNode execute(JsonNode params) {
        double width = params.has("width") ? params.get("width").asDouble() : 800;
        double height = params.has("height") ? params.get("height").asDouble() : 600;
        try {
            CanvasManager.init(width, height);
            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("width", width);
            result.put("height", height);
            result.put("message", "Canvas initialized");
            return result;
        } catch (Exception e) {
            return ErrorHelper.error("INIT_ERROR", "Error initializing canvas: " + e.getMessage());
        }
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("width", new Tool.NumberSchema().description("Canvas width in pixels (default: 800)").minimum(1).maximum(16384));
        schema.prop("height", new Tool.NumberSchema().description("Canvas height in pixels (default: 600)").minimum(1).maximum(16384));
        return schema;
    }
}
