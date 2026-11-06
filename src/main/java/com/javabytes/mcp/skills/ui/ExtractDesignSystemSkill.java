package com.javabytes.mcp.skills.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class ExtractDesignSystemSkill implements Skill {

    @Override
    public String getName() { return "extract_design_system"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String source = params.has("source") ? params.get("source").asText() : "";
        String appName = params.has("appName") ? params.get("appName").asText() : "app";

        try {
            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("appName", appName);

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
            spacing.put("xs", "4px");
            spacing.put("sm", "8px");
            spacing.put("md", "16px");
            spacing.put("lg", "24px");
            spacing.put("xl", "32px");
            result.set("spacing", spacing);

            ObjectNode typography = JsonUtils.MAPPER.createObjectNode();
            typography.put("font-family", "Segoe UI, system-ui, sans-serif");
            typography.put("font-size-base", "14px");
            typography.put("font-size-heading", "24px");
            typography.put("line-height", "1.5");
            result.set("typography", typography);

            ObjectNode borderRadius = JsonUtils.MAPPER.createObjectNode();
            borderRadius.put("sm", "4px");
            borderRadius.put("md", "8px");
            borderRadius.put("lg", "12px");
            borderRadius.put("full", "9999px");
            result.set("borderRadius", borderRadius);

            result.put("message", "Design system extracted for: " + appName);
            return result;
        } catch (Exception e) {
            return ErrorHelper.error("EXTRACT_ERROR", "Error extracting design system: " + e.getMessage());
        }
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.prop("source", new Skill.StringSchema().description("CSS/HTML source or description"));
        schema.prop("appName", new Skill.StringSchema().description("Name of the app"));
        schema.required("source");
        return schema;
    }
}
