package com.javabytes.mcp.tools.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class GenerateSpringBootScheduledTool implements Tool {

    @Override
    public String getName() { return "springboot_generate_scheduled"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "springboot_generate_scheduled",
                "Generate Scheduled Jobs",
                "Generate scheduled jobs with safe scheduling configuration.",
                "springboot",
                false,
                true,
                false,
                true,
                false);
    }

    @Override
    public JsonNode execute(JsonNode params) {
        String taskName = params.has("taskName") ? params.get("taskName").asText() : "dailyTask";
        String cronExpression = params.has("cronExpression") ? params.get("cronExpression").asText() : "0 0 0 * * *";
        String packageName = params.has("packageName") ? params.get("packageName").asText() : "com.example.demo.scheduled";

        try {
            String code = generateScheduledCode(taskName, cronExpression, packageName);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("taskName", taskName);
            result.put("cronExpression", cronExpression);
            result.put("packageName", packageName);
            result.put("code", code);
            result.put("message", "Generated scheduled task: " + taskName);
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_SCHEDULED_ERROR", "Error generating scheduled task: " + e.getMessage());
        }
    }

    private String generateScheduledCode(String taskName, String cronExpression, String packageName) {
        return "package " + packageName + ";\n\n" +
               "import org.springframework.scheduling.annotation.Scheduled;\n" +
               "import org.springframework.stereotype.Component;\n" +
               "import java.time.LocalDateTime;\n\n" +
               "@Component\n" +
               "public class " + taskName + "Scheduler {\n\n" +
               "    @Scheduled(cron = \"" + cronExpression + "\")\n" +
               "    public void execute() {\n" +
               "        System.out.println(\"Executing scheduled task: \" + LocalDateTime.now());\n" +
               "        // Add your scheduled task logic here\n" +
               "    }\n" +
               "}\n";
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.property("taskName", new Tool.StringSchema().description("Task class name"));
        schema.property("cronExpression", new Tool.StringSchema().description("Cron expression (default: 0 0 0 * * *)"));
        schema.property("packageName", new Tool.StringSchema().description("Package name"));
        schema.required("taskName");
        return schema;
    }
}




