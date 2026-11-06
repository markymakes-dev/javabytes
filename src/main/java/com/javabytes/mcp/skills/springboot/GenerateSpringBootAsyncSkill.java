package com.javabytes.mcp.skills.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class GenerateSpringBootAsyncSkill implements Skill {

    @Override
    public String getName() { return "springboot_generate_async"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String serviceName = params.has("serviceName") ? params.get("serviceName").asText() : "AsyncService";
        String packageName = params.has("packageName") ? params.get("packageName").asText() : "com.example.demo.service";

        try {
            String code = generateAsyncCode(serviceName, packageName);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("serviceName", serviceName);
            result.put("packageName", packageName);
            result.put("code", code);
            result.put("message", "Generated async service: " + serviceName);
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_ASYNC_ERROR", "Error generating async service: " + e.getMessage());
        }
    }

    private String generateAsyncCode(String serviceName, String packageName) {
        return "package " + packageName + ";\n\n" +
               "import org.springframework.scheduling.annotation.Async;\n" +
               "import org.springframework.stereotype.Service;\n" +
               "import java.util.concurrent.CompletableFuture;\n\n" +
               "@Service\n" +
               "public class " + serviceName + " {\n\n" +
               "    @Async\n" +
               "    public CompletableFuture<Void> processAsync() {\n" +
               "        try {\n" +
               "            // Add your async logic here\n" +
               "            Thread.sleep(1000);\n" +
               "        } catch (InterruptedException e) {\n" +
               "            Thread.currentThread().interrupt();\n" +
               "        }\n" +
               "        return CompletableFuture.completedFuture(null);\n" +
               "    }\n" +
               "}\n";
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.property("serviceName", new Skill.StringSchema().description("Service class name"));
        schema.property("packageName", new Skill.StringSchema().description("Service package name"));
        schema.required("serviceName");
        return schema;
    }
}




