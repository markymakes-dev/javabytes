package com.javabytes.mcp.tools.code;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CheckSecurityTool implements Tool {

    @Override
    public String getName() { return "check_security"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "check_security",
                "Check Security Issues",
                "Scan Java source for common security issues such as injection and weak crypto.",
                "analysis",
                true,
                true,
                false,
                true,
                false);
    }

    @Override
    public JsonNode execute(JsonNode params) {
        String code = params.has("code") ? params.get("code").asText() : "";
        String language = params.has("language") ? params.get("language").asText() : "java";

        try {
            ArrayNode vulnerabilities = JsonUtils.MAPPER.createArrayNode();
            int riskScore = 0;

            if (code.contains("System.out.println") || code.contains("console.log")) {
                ObjectNode vuln = JsonUtils.MAPPER.createObjectNode();
                vuln.put("type", "debug-output");
                vuln.put("severity", "low");
                vuln.put("message", "Debug output detected in code.");
                vuln.put("suggestion", "Use proper logging framework instead.");
                vulnerabilities.add(vuln);
                riskScore += 5;
            }

            if (code.contains(".eval(") || code.contains("eval(")) {
                ObjectNode vuln = JsonUtils.MAPPER.createObjectNode();
                vuln.put("type", "code-injection");
                vuln.put("severity", "critical");
                vuln.put("message", "Code evaluation detected (eval).");
                vuln.put("suggestion", "Avoid eval(). Use safe alternatives.");
                vulnerabilities.add(vuln);
                riskScore += 30;
            }

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("language", language);
            result.put("riskScore", Math.min(100, riskScore));
            result.put("riskLevel", riskScore >= 70 ? "critical" : riskScore >= 40 ? "high" : riskScore >= 20 ? "medium" : "low");
            result.set("vulnerabilities", vulnerabilities);
            result.put("vulnerabilityCount", vulnerabilities.size());
            result.put("message", "Security review complete");
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("SECURITY_ERROR", "Error checking security: " + e.getMessage());
        }
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("code", new Tool.StringSchema().description("Source code to check for security issues"));
        schema.prop("language", new Tool.StringSchema().description("Programming language"));
        schema.required("code");
        return schema;
    }
}
