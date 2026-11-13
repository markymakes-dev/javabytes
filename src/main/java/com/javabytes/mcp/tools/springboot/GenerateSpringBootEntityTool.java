package com.javabytes.mcp.tools.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class GenerateSpringBootEntityTool implements Tool {

    @Override
    public String getName() { return "springboot_generate_entity"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String entityName = params.has("entityName") ? params.get("entityName").asText() : "";
        String packageName = params.has("packageName") ? params.get("packageName").asText() : "com.example.demo.entity";
        String tableName = params.has("tableName") ? params.get("tableName").asText() : "";

        try {
            String code = generateEntityCode(entityName, packageName, tableName);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("entityName", entityName);
            result.put("packageName", packageName);
            result.put("tableName", tableName);
            result.put("code", code);
            result.put("message", "Generated Spring Boot JPA entity: " + entityName);
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_ENTITY_ERROR", "Error generating entity: " + e.getMessage());
        }
    }

    private String generateEntityCode(String entityName, String packageName, String tableName) {
        String table = tableName.isEmpty() ? entityName.toLowerCase() + "s" : tableName;

        return "package " + packageName + ";\n\n" +
               "import jakarta.persistence.*;\n" +
               "import java.time.LocalDateTime;\n\n" +
               "@Entity\n" +
               "@Table(name = \"" + table + "\")\n" +
               "public class " + entityName + " {\n\n" +
               "    @Id\n" +
               "    @GeneratedValue(strategy = GenerationType.IDENTITY)\n" +
               "    private Long id;\n\n" +
               "    @Column(nullable = false)\n" +
               "    private String name;\n\n" +
               "    @Column\n" +
               "    private LocalDateTime createdAt = LocalDateTime.now();\n\n" +
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
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.property("entityName", new Tool.StringSchema().description("Entity class name (e.g., Employee, Order)"));
        schema.property("packageName", new Tool.StringSchema().description("Java package name (default: com.example.demo.entity)"));
        schema.property("tableName", new Tool.StringSchema().description("Database table name (optional, auto-generated if not provided)"));
        schema.required("entityName");
        return schema;
    }
}




