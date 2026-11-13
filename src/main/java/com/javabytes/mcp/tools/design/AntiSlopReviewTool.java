package com.javabytes.mcp.tools.design;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AntiSlopReviewTool implements Tool {

    @Override
    public String getName() { return "anti_slop_review"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String code = params.has("code") ? params.get("code").asText() : "";
        String fileType = params.has("fileType") ? params.get("fileType").asText() : "css";

        try {
            ArrayNode issues = JsonUtils.MAPPER.createArrayNode();
            int score = 100;

            if (code.contains("border-radius: 8px") || code.contains("border-radius: 0.5rem")) {
                ObjectNode issue = JsonUtils.MAPPER.createObjectNode();
                issue.put("type", "generic-border-radius");
                issue.put("severity", "medium");
                issue.put("message", "Generic 8px border-radius detected.");
                issue.put("suggestion", "Use design token: var(--radius-md) or custom value.");
                issues.add(issue);
                score -= 10;
            }

            if (code.contains("!important")) {
                ObjectNode issue = JsonUtils.MAPPER.createObjectNode();
                issue.put("type", "important-override");
                issue.put("severity", "high");
                issue.put("message", "!important usage detected.");
                issue.put("suggestion", "Refactor CSS specificity instead of using !important.");
                issues.add(issue);
                score -= 15;
            }

            if (score < 0) score = 0;

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("fileType", fileType);
            result.put("slopScore", score);
            result.put("grade", score >= 90 ? "A" : score >= 80 ? "B" : score >= 70 ? "C" : score >= 60 ? "D" : "F");
            result.set("issues", issues);
            result.put("issueCount", issues.size());
            result.put("message", score >= 80 ? "Good design intent" : "Consider anti-slop refactoring");
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("REVIEW_ERROR", "Error reviewing code: " + e.getMessage());
        }
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("code", new Tool.StringSchema().description("CSS/HTML/JS code to review"));
        schema.prop("fileType", new Tool.StringSchema().description("File type: css, html, js, tsx"));
        schema.required("code");
        return schema;
    }
}
