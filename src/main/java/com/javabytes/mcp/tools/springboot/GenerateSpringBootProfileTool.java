package com.javabytes.mcp.tools.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class GenerateSpringBootProfileTool implements Tool {

    @Override
    public String getName() { return "springboot_generate_profile"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "springboot_generate_profile",
                "Generate Profiles",
                "Generate profile-specific configuration (dev, test, prod).",
                "springboot",
                false,
                true,
                false,
                true,
                false);
    }

    @Override
    public JsonNode execute(JsonNode params) {
        String profileName = params.has("profileName") ? params.get("profileName").asText() : "dev";
        String propertyKey = params.has("propertyKey") ? params.get("propertyKey").asText() : "app.message";
        String propertyValue = params.has("propertyValue") ? params.get("propertyValue").asText() : "development value";

        try {
            String code = generateProfileCode(profileName, propertyKey, propertyValue);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("profileName", profileName);
            result.put("propertyKey", propertyKey);
            result.put("propertyValue", propertyValue);
            result.put("code", code);
            result.put("message", "Generated profile configuration: " + profileName);
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_PROFILE_ERROR", "Error generating profile config: " + e.getMessage());
        }
    }

    private String generateProfileCode(String profileName, String propertyKey, String propertyValue) {
        return "# application.properties\n" +
               "# Profile-specific properties for: " + profileName + "\n" +
               "spring.profiles.active=" + profileName + "\n" +
               "\n" +
               "--- # Profile separator\n" +
               "spring.config.activate.on-profile=" + profileName + "\n" +
               propertyKey + "=" + propertyValue + "\n" +
               "\n" +
               "--- # Default profile\n" +
               "spring.config.activate.on-profile=default\n" +
               propertyKey + "=default value\n";
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.property("profileName", new Tool.StringSchema().description("Profile name (dev, test, prod)"));
        schema.property("propertyKey", new Tool.StringSchema().description("Property key"));
        schema.property("propertyValue", new Tool.StringSchema().description("Property value"));
        schema.required("profileName");
        return schema;
    }
}




