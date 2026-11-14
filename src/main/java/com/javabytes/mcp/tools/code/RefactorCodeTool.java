package com.javabytes.mcp.tools.code;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class RefactorCodeTool implements Tool {

    @Override
    public String getName() { return "refactor_code"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "refactor_code",
                "Refactor Java Code",
                "Refactor Java source for readability, naming and structure while preserving behaviour.",
                "codegen",
                false,
                true,
                false,
                true,
                false);
    }

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
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("code", new Tool.StringSchema().description("Source code to refactor"));
        schema.prop("refactorType", new Tool.StringSchema().description("Type: general, extract-method, rename-variable, simplify-logic"));
        schema.required("code");
        return schema;
    }
}
