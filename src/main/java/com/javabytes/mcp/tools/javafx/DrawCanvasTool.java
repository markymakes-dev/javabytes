package com.javabytes.mcp.tools.javafx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class DrawCanvasTool implements Tool {

    @Override
    public String getName() { return "javafx_draw_canvas"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "javafx_draw_canvas",
                "Draw on Canvas",
                "Draw shapes and primitives onto the active off-screen canvas.",
                "javafx",
                true,
                false,
                false,
                false,
                false);
    }

    @Override
    public JsonNode execute(JsonNode params) {
        String shape = params.has("shape") ? params.get("shape").asText() : "";
        double x = params.has("x") ? params.get("x").asDouble() : 0;
        double y = params.has("y") ? params.get("y").asDouble() : 0;
        double width = params.has("width") ? params.get("width").asDouble() : 50;
        double height = params.has("height") ? params.get("height").asDouble() : 50;
        String colorStr = params.has("color") ? params.get("color").asText() : "black";

        try {
            GraphicsContext gc = CanvasManager.getGc();
            if (gc == null) {
                return ErrorHelper.error("CANVAS_NOT_INIT", "Canvas not initialized. Call javafx_init_canvas first.");
            }
            Color color = parseColor(colorStr);
            gc.setFill(color);
            gc.setStroke(color);

            switch (shape.toLowerCase()) {
                case "rectangle":
                    gc.fillRect(x, y, width, height);
                    break;
                case "circle":
                    gc.fillOval(x, y, width, height);
                    break;
                case "line":
                    double x2 = params.has("x2") ? params.get("x2").asDouble() : x + width;
                    double y2 = params.has("y2") ? params.get("y2").asDouble() : y + height;
                    gc.strokeLine(x, y, x2, y2);
                    break;
                case "text":
                    String text = params.has("text") ? params.get("text").asText() : "Hello";
                    gc.fillText(text, x, y);
                    break;
                default:
                    return ErrorHelper.error("INVALID_SHAPE", "Unknown shape: " + shape);
            }

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("shape", shape);
            result.put("x", x);
            result.put("y", y);
            result.put("width", width);
            result.put("height", height);
            result.put("color", colorStr);
            result.put("message", "Drew " + shape + " on canvas");
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("DRAW_ERROR", "Error drawing: " + e.getMessage());
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
        schema.prop("shape", new Tool.StringSchema().description("Shape: rectangle, circle, line, text"));
        schema.prop("x", new Tool.NumberSchema().description("X coordinate"));
        schema.prop("y", new Tool.NumberSchema().description("Y coordinate"));
        schema.prop("width", new Tool.NumberSchema().description("Width (default: 50)"));
        schema.prop("height", new Tool.NumberSchema().description("Height (default: 50)"));
        schema.prop("color", new Tool.StringSchema().description("Color name or hex"));
        schema.prop("text", new Tool.StringSchema().description("Text content"));
        schema.prop("x2", new Tool.NumberSchema().description("Line end X coordinate"));
        schema.prop("y2", new Tool.NumberSchema().description("Line end Y coordinate"));
        schema.required("shape");
        schema.required("x");
        schema.required("y");
        return schema;
    }
}
