package com.javabytes.mcp.skills.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class GenerateSpringBootServiceSkill implements Skill {

    @Override
    public String getName() { return "springboot_generate_service"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String entityName = params.has("entityName") ? params.get("entityName").asText() : "";
        String packageName = params.has("packageName") ? params.get("packageName").asText() : "com.example.demo.service";

        try {
            String code = generateServiceCode(entityName, packageName);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("entityName", entityName);
            result.put("packageName", packageName);
            result.put("code", code);
            result.put("message", "Generated Spring Boot service for: " + entityName);
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_SERVICE_ERROR", "Error generating service: " + e.getMessage());
        }
    }

    private String generateServiceCode(String entityName, String packageName) {
        String serviceName = entityName + "Service";
        String repoName = entityName + "Repository";

        return "package " + packageName + ";\n\n" +
               "import " + getEntityPackage(entityName) + "." + entityName + ";\n" +
               "import " + getRepoPackage(entityName) + "." + repoName + ";\n" +
               "import org.springframework.beans.factory.annotation.Autowired;\n" +
               "import org.springframework.stereotype.Service;\n" +
               "import org.springframework.transaction.annotation.Transactional;\n" +
               "import java.util.List;\n" +
               "import java.util.Optional;\n\n" +
               "@Service\n" +
               "@Transactional\n" +
               "public class " + serviceName + " {\n\n" +
               "    @Autowired\n" +
               "    private " + repoName + " " + repoName.substring(0, 1).toLowerCase() + repoName.substring(1) + ";\n\n" +
               "    public List<" + entityName + "> findAll() {\n" +
               "        return " + repoName.substring(0, 1).toLowerCase() + repoName.substring(1) + ".findAll();\n" +
               "    }\n\n" +
               "    public Optional<" + entityName + "> findById(Long id) {\n" +
               "        return " + repoName.substring(0, 1).toLowerCase() + repoName.substring(1) + ".findById(id);\n" +
               "    }\n\n" +
               "    public " + entityName + " save(" + entityName + " entity) {\n" +
               "        return " + repoName.substring(0, 1).toLowerCase() + repoName.substring(1) + ".save(entity);\n" +
               "    }\n\n" +
               "    public void deleteById(Long id) {\n" +
               "        " + repoName.substring(0, 1).toLowerCase() + repoName.substring(1) + ".deleteById(id);\n" +
               "    }\n" +
               "}\n";
    }

    private String getEntityPackage(String entityName) {
        return "com.example.demo.entity";
    }

    private String getRepoPackage(String entityName) {
        return "com.example.demo.repository";
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.property("entityName", new Skill.StringSchema().description("Entity class name (e.g., Employee, Order)"));
        schema.property("packageName", new Skill.StringSchema().description("Service package name (default: com.example.demo.service)"));
        schema.required("entityName");
        return schema;
    }
}




