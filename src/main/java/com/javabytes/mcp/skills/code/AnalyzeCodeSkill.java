package com.javabytes.mcp.skills.code;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalyzeCodeSkill implements Skill {

    @Override
    public String getName() { return "analyze_code"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String code = params.has("code") ? params.get("code").asText() : "";
        String language = params.has("language") ? params.get("language").asText() : "java";

        try {
            ArrayNode issues = JsonUtils.MAPPER.createArrayNode();
            int complexity = 1;
            int lineCount = code.split("\\r?\\n").length;

            Pattern methodPattern = Pattern.compile("(public|private|protected|static)\\s+\\w+\\s+\\w+\\s*\\(");
            Matcher methodMatcher = methodPattern.matcher(code);
            int methodCount = 0;
            while (methodMatcher.find()) methodCount++;

            Pattern ifPattern = Pattern.compile("\\bif\\s*\\(");
            Matcher ifMatcher = ifPattern.matcher(code);
            int ifCount = 0;
            while (ifMatcher.find()) {
                ifCount++;
                complexity++;
            }

            if (code.contains("TODO") || code.contains("FIXME")) {
                ObjectNode issue = JsonUtils.MAPPER.createObjectNode();
                issue.put("type", "todo-fixme");
                issue.put("severity", "low");
                issue.put("message", "TODO or FIXME comments found.");
                issues.add(issue);
            }

            if (code.contains("System.out.println")) {
                ObjectNode issue = JsonUtils.MAPPER.createObjectNode();
                issue.put("type", "debug-print");
                issue.put("severity", "low");
                issue.put("message", "System.out.println detected. Consider using logging framework.");
                issues.add(issue);
            }

            if (complexity > 10) {
                ObjectNode issue = JsonUtils.MAPPER.createObjectNode();
                issue.put("type", "high-complexity");
                issue.put("severity", "medium");
                issue.put("message", "High cyclomatic complexity detected (" + complexity + ").");
                issue.put("suggestion", "Consider extracting methods or simplifying logic.");
                issues.add(issue);
            }

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("language", language);
            result.put("lineCount", lineCount);
            result.put("methodCount", methodCount);
            result.put("cyclomaticComplexity", complexity);
            result.put("ifCount", ifCount);
            result.set("issues", issues);
            result.put("issueCount", issues.size());
            result.put("maintainabilityIndex", calculateMaintainability(lineCount, complexity, issues.size()));
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("ANALYZE_ERROR", "Error analyzing code: " + e.getMessage());
        }
    }

    private int calculateMaintainability(int lines, int complexity, int issues) {
        int score = 100;
        score -= (lines / 50) * 5;
        score -= (complexity - 1) * 5;
        score -= issues * 10;
        return Math.max(0, Math.min(100, score));
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.prop("code", new Skill.StringSchema().description("Source code to analyze"));
        schema.prop("language", new Skill.StringSchema().description("Programming language"));
        schema.required("code");
        return schema;
    }
}
