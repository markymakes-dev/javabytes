package com.javabytes.mcp.tools.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class GenerateSpringBootActuatorTool implements Tool {

    @Override
    public String getName() { return "springboot_generate_actuator"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String endpoint = params.has("endpoint") ? params.get("endpoint").asText() : "health";
        String packageName = params.has("packageName") ? params.get("packageName").asText() : "com.example.demo.actuator";

        try {
            String code = generateActuatorCode(endpoint, packageName);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("endpoint", endpoint);
            result.put("packageName", packageName);
            result.put("code", code);
            result.put("message", "Generated Spring Boot Actuator configuration");
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_ACTUATOR_ERROR", "Error generating actuator config: " + e.getMessage());
        }
    }

    private String generateActuatorCode(String endpoint, String packageName) {
        return "package " + packageName + ";\n\n" +
               "// Spring Boot Actuator Configuration\n" +
               "// Add to application.properties:\n" +
               "management.endpoints.web.exposure.include=health,info,metrics\n" +
               "management.endpoint.health.show-details=always\n" +
               "\n" +
               "// Custom Health Indicator\n" +
               "import org.springframework.boot.actuate.health.Health;\n" +
               "import org.springframework.boot.actuate.health.HealthIndicator;\n" +
               "import org.springframework.stereotype.Component;\n" +
               "\n" +
               "@Component\n" +
               "public class CustomHealthIndicator implements HealthIndicator {\n" +
               "    @Override\n" +
               "    public Health health() {\n" +
               "        // Add your custom health check logic\n" +
               "        boolean healthy = checkDatabaseConnection();\n" +
               "        if (healthy) {\n" +
               "            return Health.up().build();\n" +
               "        }\n" +
               "        return Health.down().build();\n" +
               "    }\n" +
               "    private boolean checkDatabaseConnection() {\n" +
               "        // Implement database connection check\n" +
               "        return true;\n" +
               "    }\n" +
               "}\n";
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.property("endpoint", new Tool.StringSchema().description("Actuator endpoint: health, info, metrics"));
        schema.property("packageName", new Tool.StringSchema().description("Package name"));
        return schema;
    }
}




