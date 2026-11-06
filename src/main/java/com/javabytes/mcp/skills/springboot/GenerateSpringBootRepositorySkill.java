package com.javabytes.mcp.skills.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class GenerateSpringBootRepositorySkill implements Skill {

    @Override
    public String getName() { return "springboot_generate_repository"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String entityName = params.has("entityName") ? params.get("entityName").asText() : "";
        String packageName = params.has("packageName") ? params.get("packageName").asText() : "com.example.demo.repository";

        try {
            String code = generateRepositoryCode(entityName, packageName);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("entityName", entityName);
            result.put("packageName", packageName);
            result.put("code", code);
            result.put("message", "Generated Spring Data JPA repository for: " + entityName);
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_REPOSITORY_ERROR", "Error generating repository: " + e.getMessage());
        }
    }

    private String generateRepositoryCode(String entityName, String packageName) {
        return "package " + packageName + ";\n\n" +
               "import " + getEntityPackage(entityName) + "." + entityName + ";\n" +
               "import org.springframework.data.jpa.repository.JpaRepository;\n" +
               "import org.springframework.stereotype.Repository;\n\n" +
               "@Repository\n" +
               "public interface " + entityName + "Repository extends JpaRepository<" + entityName + ", Long> {\n" +
               "    // Add custom query methods here\n" +
               "}\n";
    }

    private String getEntityPackage(String entityName) {
        return "com.example.demo.entity";
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.property("entityName", new Skill.StringSchema().description("Entity class name (e.g., Employee, Order)"));
        schema.property("packageName", new Skill.StringSchema().description("Repository package name (default: com.example.demo.repository)"));
        schema.required("entityName");
        return schema;
    }
}




