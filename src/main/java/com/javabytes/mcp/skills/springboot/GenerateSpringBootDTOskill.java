package com.javabytes.mcp.skills.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class GenerateSpringBootDTOskill implements Skill {

    @Override
    public String getName() { return "springboot_generate_dto"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String dtoName = params.has("dtoName") ? params.get("dtoName").asText() : "";
        String packageName = params.has("packageName") ? params.get("packageName").asText() : "com.example.demo.dto";

        try {
            String code = generateDTOCode(dtoName, packageName);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("dtoName", dtoName);
            result.put("packageName", packageName);
            result.put("code", code);
            result.put("message", "Generated Spring Boot DTO: " + dtoName);
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_DTO_ERROR", "Error generating DTO: " + e.getMessage());
        }
    }

    private String generateDTOCode(String dtoName, String packageName) {
        return "package " + packageName + ";\n\n" +
               "import java.time.LocalDateTime;\n\n" +
               "public class " + dtoName + " {\n\n" +
               "    private Long id;\n" +
               "    private String name;\n" +
               "    private LocalDateTime createdAt;\n\n" +
               "    // Constructors\n" +
               "    public " + dtoName + "() {}\n\n" +
               "    public " + dtoName + "(Long id, String name, LocalDateTime createdAt) {\n" +
               "        this.id = id;\n" +
               "        this.name = name;\n" +
               "        this.createdAt = createdAt;\n" +
               "    }\n\n" +
               "    // Getters and Setters\n" +
               "    public Long getId() { return id; }\n" +
               "    public void setId(Long id) { this.id = id; }\n" +
               "    public String getName() { return name; }\n" +
               "    public void setName(String name) { this.name = name; }\n" +
               "    public LocalDateTime getCreatedAt() { return createdAt; }\n" +
               "    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }\n" +
               "}\n";
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.property("dtoName", new Skill.StringSchema().description("DTO class name (e.g., EmployeeDTO, OrderDTO)"));
        schema.property("packageName", new Skill.StringSchema().description("DTO package name (default: com.example.demo.dto)"));
        schema.required("dtoName");
        return schema;
    }
}




