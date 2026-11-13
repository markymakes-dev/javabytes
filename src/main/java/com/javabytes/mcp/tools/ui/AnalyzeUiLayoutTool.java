package com.javabytes.mcp.tools.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalyzeUiLayoutTool implements Tool {

    @Override
    public String getName() { return "analyze_ui_layout"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String html = params.has("html") ? params.get("html").asText() : "";
        String css = params.has("css") ? params.get("css").asText() : "";

        try {
            int gridCount = countPattern(css, "display:\\s*grid");
            int flexCount = countPattern(css, "display:\\s*flex");
            int absoluteCount = countPattern(css, "position:\\s*absolute");

            String javafxLayout;
            if (gridCount > 0) javafxLayout = "GridPane or TilePane";
            else if (flexCount > 0) javafxLayout = "HBox or VBox";
            else if (absoluteCount > 0) javafxLayout = "Pane (absolute)";
            else javafxLayout = "VBox (default)";

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("gridCount", gridCount);
            result.put("flexCount", flexCount);
            result.put("absoluteCount", absoluteCount);
            result.put("recommendedJavafxLayout", javafxLayout);
            result.put("message", "UI layout analyzed");
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("ANALYZE_UI_ERROR", "Error analyzing UI layout: " + e.getMessage());
        }
    }

    private int countPattern(String text, String pattern) {
        Matcher m = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("html", new Tool.StringSchema().description("HTML source"));
        schema.prop("css", new Tool.StringSchema().description("CSS source"));
        schema.required("css");
        return schema;
    }
}
