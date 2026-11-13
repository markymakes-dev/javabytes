package com.javabytes.mcp.tools.javafx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;
import javafx.scene.paint.Color;

public class SetColorTool implements Tool {

    @Override
    public String getName() { return "javafx_set_color"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String colorStr = params.has("color") ? params.get("color").asText() : "black";
        try {
            Color color = parseColor(colorStr);
            CanvasManager.setColor(color);
            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("color", colorStr);
            result.put("message", "Color set to " + colorStr);
            return result;
        } catch (Exception e) {
            return ErrorHelper.error("COLOR_ERROR", "Error setting color: " + e.getMessage());
        }
    }

    private Color parseColor(String c) {
        try {
            return Color.web(c);
        } catch (Exception e) {
            switch (c.toLowerCase()) {
                case "red": return Color.RED;
                case "blue": return Color.BLUE;
                case "green": return Color.GREEN;
                case "yellow": return Color.YELLOW;
                case "black": return Color.BLACK;
                case "white": return Color.WHITE;
                case "orange": return Color.ORANGE;
                case "purple": return Color.PURPLE;
                default: return Color.BLACK;
            }
        }
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("color", new Tool.StringSchema().description("Color name or hex"));
        schema.required("color");
        return schema;
    }
}
