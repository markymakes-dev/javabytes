package com.javabytes.mcp.tools.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class GenerateSpringBootValidationTool implements Tool {

    @Override
    public String getName() { return "springboot_generate_validation"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String className = params.has("className") ? params.get("className").asText() : "UserDTO";
        String packageName = params.has("packageName") ? params.get("packageName").asText() : "com.example.demo.dto";

        try {
            String code = generateValidationCode(className, packageName);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("className", dtoName(className));
            result.put("packageName", packageName);
            result.put("code", code);
            result.put("message", "Generated DTO with Bean Validation annotations");
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_VALIDATION_ERROR", "Error generating validation DTO: " + e.getMessage());
        }
    }

    private String generateValidationCode(String className, String packageName) {
        return "package " + packageName + ";\n\n" +
               "import jakarta.validation.constraints.Email;\n" +
               "import jakarta.validation.constraints.NotBlank;\n" +
               "import jakarta.validation.constraints.NotNull;\n" +
               "import jakarta.validation.constraints.Size;\n" +
               "import jakarta.validation.constraints.PastOrPresent;\n" +
               "import java.time.LocalDateTime;\n\n" +
               "public class " + className + " {\n\n" +
               "    @NotNull(message = \"ID is required\")\n" +
               "    private Long id;\n\n" +
               "    @NotBlank(message = \"Name is required\")\n" +
               "    @Size(min = 2, max = 100, message = \"Name must be between 2 and 100 characters\")\n" +
               "    private String name;\n\n" +
               "    @NotBlank(message = \"Email is required\")\n" +
               "    @Email(message = \"Email should be valid\")\n" +
               "    private String email;\n\n" +
               "    @NotNull(message = \"Created date is required\")\n" +
               "    @PastOrPresent(message = \"Created date must be past or present\")\n" +
               "    private LocalDateTime createdAt;\n\n" +
               "    // Getters and Setters\n" +
               "    public Long getId() { return id; }\n" +
               "    public void setId(Long id) { this.id = id; }\n" +
               "    public String getName() { return name; }\n" +
               "    public void setName(String name) { this.name = name; }\n" +
               "    public String getEmail() { return email; }\n" +
               "    public void setEmail(String email) { this.email = email; }\n" +
               "    public LocalDateTime getCreatedAt() { return createdAt; }\n" +
               "    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }\n" +
               "}\n";
    }

    private String dtoName(String name) {
        if (!name.endsWith("DTO")) return name + "DTO";
        return name;
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.property("className", new Tool.StringSchema().description("DTO class name (e.g., UserDTO)"));
        schema.property("packageName", new Tool.StringSchema().description("DTO package name"));
        schema.required("className");
        return schema;
    }
}




