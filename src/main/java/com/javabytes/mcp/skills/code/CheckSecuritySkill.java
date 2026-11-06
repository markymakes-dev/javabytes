package com.javabytes.mcp.skills.code;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CheckSecuritySkill implements Skill {

    @Override
    public String getName() { return "check_security"; }

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
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.prop("code", new Skill.StringSchema().description("Source code to check for security issues"));
        schema.prop("language", new Skill.StringSchema().description("Programming language"));
        schema.required("code");
        return schema;
    }
}
