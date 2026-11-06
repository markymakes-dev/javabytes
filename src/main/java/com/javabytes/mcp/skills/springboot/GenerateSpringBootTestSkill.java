package com.javabytes.mcp.skills.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class GenerateSpringBootTestSkill implements Skill {

    @Override
    public String getName() { return "springboot_generate_test"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String className = params.has("className") ? params.get("className").asText() : "EmployeeController";
        String packageName = params.has("packageName") ? params.get("packageName").asText() : "com.example.demo.controller";
        String testType = params.has("testType") ? params.get("testType").asText() : "unit";

        try {
            String code = generateTestCode(className, packageName, testType);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("className", className);
            result.put("packageName", packageName);
            result.put("testType", testType);
            result.put("code", code);
            result.put("message", "Generated Spring Boot test: " + className + "Test");
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_TEST_ERROR", "Error generating test: " + e.getMessage());
        }
    }

    private String generateTestCode(String className, String packageName, String testType) {
        String testName = className + "Test";
        String serviceName = className.replace("Controller", "Service");

        if ("unit".equalsIgnoreCase(testType)) {
            return "package " + packageName + ";\n\n" +
                   "import org.junit.jupiter.api.Test;\n" +
                   "import org.junit.jupiter.api.extension.ExtendWith;\n" +
                   "import org.mockito.InjectMocks;\n" +
                   "import org.mockito.Mock;\n" +
                   "import org.mockito.junit.jupiter.MockitoExtension;\n" +
                   "import static org.mockito.Mockito.*;\n" +
                   "import static org.junit.jupiter.api.Assertions.*;\n\n" +
                   "@ExtendWith(MockitoExtension.class)\n" +
                   "public class " + testName + " {\n\n" +
                   "    @Mock\n" +
                   "    private " + serviceName + " service;\n\n" +
                   "    @InjectMocks\n" +
                   "    private " + className + " controller;\n\n" +
                   "    @Test\n" +
               "    void testSomething() {\n" +
               "        // Arrange\n" +
               "        when(service.findAll()).thenReturn(java.util.List.of());\n\n" +
               "        // Act\n" +
               "        var result = controller.getAll();\n\n" +
               "        // Assert\n" +
               "        assertNotNull(result);\n" +
               "        verify(service, times(1)).findAll();\n" +
               "    }\n" +
               "}\n";
        } else {
            return "package " + packageName + ";\n\n" +
                   "import org.springframework.boot.test.context.SpringBootTest;\n" +
                   "import org.junit.jupiter.api.Test;\n\n" +
                   "@SpringBootTest\n" +
                   "public class " + testName + " {\n\n" +
               "    @Test\n" +
               "    void contextLoads() {\n" +
               "    }\n" +
               "}\n";
        }
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.property("className", new Skill.StringSchema().description("Class name to test (e.g., EmployeeController)"));
        schema.property("packageName", new Skill.StringSchema().description("Test package name"));
        schema.property("testType", new Skill.StringSchema().description("Test type: unit or integration"));
        schema.required("className");
        return schema;
    }
}




