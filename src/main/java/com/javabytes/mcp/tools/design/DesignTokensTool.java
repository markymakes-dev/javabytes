package com.javabytes.mcp.tools.design;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.JsonUtils;

public class DesignTokensTool implements Tool {

    @Override
    public String getName() { return "design_tokens"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "design_tokens",
                "Generate Design Tokens",
                "Produce a coherent set of design tokens (colors, type scale, spacing, radii).",
                "design",
                false,
                true,
                false,
                true,
                false);
    }

    @Override
    public JsonNode execute(JsonNode params) {
        ObjectNode result = JsonUtils.MAPPER.createObjectNode();
        result.put("success", true);

        ObjectNode colors = JsonUtils.MAPPER.createObjectNode();
        colors.put("primary", "#3b82f6");
        colors.put("primary-hover", "#2563eb");
        colors.put("secondary", "#64748b");
        colors.put("accent", "#0f766e");
        colors.put("background", "#ffffff");
        colors.put("background-muted", "#f8fafc");
        colors.put("text-primary", "#0f172a");
        colors.put("text-secondary", "#475569");
        colors.put("border", "#e2e8f0");
        result.set("colors", colors);

        ObjectNode spacing = JsonUtils.MAPPER.createObjectNode();
        spacing.put("xs", "0.25rem");
        spacing.put("sm", "0.5rem");
        spacing.put("md", "1rem");
        spacing.put("lg", "1.5rem");
        spacing.put("xl", "2rem");
        result.set("spacing", spacing);

        ObjectNode typography = JsonUtils.MAPPER.createObjectNode();
        typography.put("font-family", "Segoe UI, system-ui, sans-serif");
        typography.put("font-size-base", "1rem");
        typography.put("line-height", "1.5");
        typography.put("font-weight-regular", "400");
        typography.put("font-weight-medium", "500");
        typography.put("font-weight-bold", "700");
        result.set("typography", typography);

        ObjectNode borderRadius = JsonUtils.MAPPER.createObjectNode();
        borderRadius.put("none", "0");
        borderRadius.put("sm", "0.25rem");
        borderRadius.put("md", "0.375rem");
        borderRadius.put("lg", "0.5rem");
        borderRadius.put("full", "9999px");
        result.set("borderRadius", borderRadius);

        ObjectNode shadows = JsonUtils.MAPPER.createObjectNode();
        shadows.put("none", "none");
        shadows.put("sm", "0 1px 2px 0 rgb(0 0 0 / 0.05)");
        shadows.put("md", "0 4px 6px -1px rgb(0 0 0 / 0.1)");
        shadows.put("lg", "0 10px 15px -3px rgb(0 0 0 / 0.1)");
        result.set("shadows", shadows);

        result.put("message", "Design tokens for JavaBytes UI design system");
        return result;
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        return new Tool.JsonSchema();
    }
}
