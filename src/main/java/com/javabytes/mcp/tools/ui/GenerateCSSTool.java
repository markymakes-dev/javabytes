package com.javabytes.mcp.tools.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class GenerateCSSTool implements Tool {

    @Override
    public String getName() { return "generate_css"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "generate_css",
                "Generate JavaFX CSS",
                "Generate JavaFX CSS from design tokens such as color, radius and spacing.",
                "ui",
                false,
                true,
                false,
                true,
                false);
    }

    @Override
    public JsonNode execute(JsonNode params) {
        String component = params.has("component") ? params.get("component").asText() : "all";
        String colorScheme = params.has("colorScheme") ? params.get("colorScheme").asText() : "blue";
        String tone = params.has("tone") ? params.get("tone").asText() : "light";

        try {
            String css = generateCSS(component, colorScheme, tone);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("component", component);
            result.put("colorScheme", colorScheme);
            result.put("tone", tone);
            result.put("css", css);
            result.put("message", "Generated CSS for " + component);
            return result;
        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_CSS_ERROR", "Error generating CSS: " + e.getMessage());
        }
    }

    private String generateCSS(String component, String colorScheme, String tone) {
        String primary = getPrimaryColor(colorScheme);
        String surface = tone.equals("dark") ? "#1a1a1a" : "#ffffff";
        String onSurface = tone.equals("dark") ? "#e0e0e0" : "#1a1a1a";
        String outline = tone.equals("dark") ? "#444444" : "#cbd5e1";

        StringBuilder css = new StringBuilder();
        css.append("/* Custom Design CSS for JavaFX */\n");
        css.append("/* Color: ").append(colorScheme).append(" | Tone: ").append(tone).append(" */\n\n");

        css.append(":root {\n");
        css.append("    -fx-primary: ").append(primary).append(";\n");
        css.append("    -fx-surface: ").append(surface).append(";\n");
        css.append("    -fx-on-surface: ").append(onSurface).append(";\n");
        css.append("    -fx-outline: ").append(outline).append(";\n");
        css.append("}\n\n");

        css.append(".btn {\n");
        css.append("    -fx-background-color: -fx-primary;\n");
        css.append("    -fx-text-fill: white;\n");
        css.append("    -fx-background-radius: 6px;\n");
        css.append("    -fx-cursor: hand;\n");
        css.append("    -fx-font-weight: 500;\n");
        css.append("    -fx-padding: 12 24;\n");
        css.append("}\n\n");

        css.append(".text-field {\n");
        css.append("    -fx-background-color: -fx-surface;\n");
        css.append("    -fx-border-color: -fx-outline;\n");
        css.append("    -fx-border-radius: 6px;\n");
        css.append("    -fx-padding: 8px 12px;\n");
        css.append("    -fx-text-fill: -fx-on-surface;\n");
        css.append("}\n\n");

        css.append(".card {\n");
        css.append("    -fx-background-color: -fx-surface;\n");
        css.append("    -fx-background-radius: 8px;\n");
        css.append("    -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 4);\n");
        css.append("    -fx-padding: 24;\n");
        css.append("}\n");

        return css.toString();
    }

    private String getPrimaryColor(String scheme) {
        return switch (scheme.toLowerCase()) {
            case "blue" -> "#3b82f6";
            case "green" -> "#22c55e";
            case "red" -> "#ef4444";
            case "purple" -> "#a855f7";
            case "orange" -> "#f97316";
            case "teal" -> "#14b8a6";
            case "pink" -> "#ec4899";
            default -> "#3b82f6";
        };
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("component", new Tool.StringSchema().description("Component: button, textfield, card, dialog, all"));
        schema.prop("colorScheme", new Tool.StringSchema().description("Color scheme"));
        schema.prop("tone", new Tool.StringSchema().description("Tone: light or dark"));
        return schema;
    }
}
