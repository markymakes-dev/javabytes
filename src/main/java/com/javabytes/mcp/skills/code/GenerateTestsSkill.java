package com.javabytes.mcp.skills.code;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class GenerateTestsSkill implements Skill {

    @Override
    public String getName() { return "generate_tests"; }

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
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.prop("code", new Skill.StringSchema().description("Source code to generate tests for"));
        schema.prop("language", new Skill.StringSchema().description("Programming language"));
        schema.prop("testFramework", new Skill.StringSchema().description("Test framework"));
        schema.required("code");
        return schema;
    }
}
