package com.javabytes.mcp.tools.code;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class GenerateCodeTool implements Tool {

    @Override
    public String getName() { return "generate_code"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String description = params.has("description") ? params.get("description").asText() : "";
        String language = params.has("language") ? params.get("language").asText() : "java";
        String framework = params.has("framework") ? params.get("framework").asText() : "spring-boot";

        try {
            String generatedCode = generateTemplate(description, language, framework);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("description", description);
            result.put("language", language);
            result.put("framework", framework);
            result.put("code", generatedCode);
            result.put("message", "Generated " + language + " code for: " + description);
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_ERROR", "Error generating code: " + e.getMessage());
        }
    }

    private String generateTemplate(String description, String language, String framework) {
        if ("java".equalsIgnoreCase(language)) {
            return "// Generated Java code for: " + description + "\n" +
                    "// Framework: " + framework + "\n" +
                    "// TODO: Implement business logic\n" +
                    "public class GeneratedClass {\n" +
                    "    // Add your implementation here\n" +
                    "}\n";
        } else if ("javascript".equalsIgnoreCase(language) || "js".equalsIgnoreCase(language)) {
            return "// Generated JavaScript code for: " + description + "\n" +
                    "// Framework: " + framework + "\n" +
                    "// TODO: Implement business logic\n" +
                    "export class GeneratedClass {\n" +
                    "    // Add your implementation here\n" +
                    "}\n";
        } else {
            return "// Generated code for: " + description + "\n" +
                    "// Language: " + language + "\n" +
                    "// TODO: Implement business logic\n";
        }
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("description", new Tool.StringSchema().description("Description of code to generate"));
        schema.prop("language", new Tool.StringSchema().description("Programming language"));
        schema.prop("framework", new Tool.StringSchema().description("Framework"));
        schema.required("description");
        return schema;
    }
}
