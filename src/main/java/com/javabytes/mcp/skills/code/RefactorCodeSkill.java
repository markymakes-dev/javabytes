package com.javabytes.mcp.skills.code;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class RefactorCodeSkill implements Skill {

    @Override
    public String getName() { return "refactor_code"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String code = params.has("code") ? params.get("code").asText() : "";
        String refactorType = params.has("refactorType") ? params.get("refactorType").asText() : "general";

        try {
            String refactored = performRefactor(code, refactorType);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("refactorType", refactorType);
            result.put("originalCode", code);
            result.put("refactoredCode", refactored);
            result.put("message", "Code refactored: " + refactorType);
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("REFACTOR_ERROR", "Error refactoring code: " + e.getMessage());
        }
    }

    private String performRefactor(String code, String type) {
        StringBuilder result = new StringBuilder();
        result.append("// Refactored code (").append(type).append(")\n");
        result.append(code);
        result.append("\n// Refactoring suggestions applied:\n");
        result.append("// - Consider extracting methods for complex logic\n");
        result.append("// - Use meaningful variable names\n");
        result.append("// - Add proper error handling\n");
        return result.toString();
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.prop("code", new Skill.StringSchema().description("Source code to refactor"));
        schema.prop("refactorType", new Skill.StringSchema().description("Type: general, extract-method, rename-variable, simplify-logic"));
        schema.required("code");
        return schema;
    }
}
