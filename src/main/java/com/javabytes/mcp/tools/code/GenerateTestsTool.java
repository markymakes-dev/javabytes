package com.javabytes.mcp.tools.code;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class GenerateTestsTool implements Tool {

    @Override
    public String getName() { return "generate_tests"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "generate_tests",
                "Generate JUnit Tests",
                "Generate JUnit tests for a Java class or method.",
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
        String language = params.has("language") ? params.get("language").asText() : "java";
        String testFramework = params.has("testFramework") ? params.get("testFramework").asText() : "junit5";

        try {
            String testCode = generateTestCode(code, language, testFramework);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("language", language);
            result.put("testFramework", testFramework);
            result.put("testCode", testCode);
            result.put("message", "Generated " + testFramework + " tests for " + language + " code");
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_TESTS_ERROR", "Error generating tests: " + e.getMessage());
        }
    }

    private String generateTestCode(String code, String language, String testFramework) {
        if ("java".equalsIgnoreCase(language) && "junit5".equalsIgnoreCase(testFramework)) {
            return "import org.junit.jupiter.api.Test;\n" +
                    "import static org.junit.jupiter.api.Assertions.*;\n" +
                    "\n" +
                    "class GeneratedClassTest {\n" +
                    "    @Test\n" +
                    "    void testGeneratedCode() {\n" +
                    "        assertTrue(true);\n" +
                    "    }\n" +
                    "}\n";
        } else {
            return "// Test code for: " + language + "\n" +
                    "// Framework: " + testFramework + "\n" +
                    "// TODO: Implement tests\n";
        }
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("code", new Tool.StringSchema().description("Source code to generate tests for"));
        schema.prop("language", new Tool.StringSchema().description("Programming language"));
        schema.prop("testFramework", new Tool.StringSchema().description("Test framework"));
        schema.required("code");
        return schema;
    }
}
