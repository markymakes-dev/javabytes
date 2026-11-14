package com.javabytes.mcp.tools.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class GenerateSpringBootPaginationTool implements Tool {

    @Override
    public String getName() { return "springboot_generate_pagination"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "springboot_generate_pagination",
                "Generate Pagination",
                "Generate pagination and sorting support for Spring Data endpoints.",
                "springboot",
                false,
                true,
                false,
                true,
                false);
    }

    @Override
    public JsonNode execute(JsonNode params) {
        String entityName = params.has("entityName") ? params.get("entityName").asText() : "Employee";
        String packageName = params.has("packageName") ? params.get("packageName").asText() : "com.example.demo.service";

        try {
            String code = generatePaginationCode(entityName, packageName);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("entityName", entityName);
            result.put("packageName", packageName);
            result.put("code", code);
            result.put("message", "Generated paginated service method for: " + entityName);
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_PAGINATION_ERROR", "Error generating pagination code: " + e.getMessage());
        }
    }

    private String generatePaginationCode(String entityName, String packageName) {
        String serviceName = entityName + "Service";
        String repoName = entityName + "Repository";

        return "package " + packageName + ";\n\n" +
               "import " + getEntityPackage(entityName) + "." + entityName + ";\n" +
               "import " + getRepoPackage(entityName) + "." + entityName + "Repository;\n" +
               "import org.springframework.beans.factory.annotation.Autowired;\n" +
               "import org.springframework.data.domain.Page;\n" +
               "import org.springframework.data.domain.Pageable;\n" +
               "import org.springframework.stereotype.Service;\n" +
               "import org.springframework.transaction.annotation.Transactional;\n\n" +
               "@Service\n" +
               "@Transactional(readOnly = true)\n" +
               "public class " + serviceName + " {\n\n" +
               "    @Autowired\n" +
               "    private " + entityName + "Repository " + entityName.toLowerCase() + "Repository;\n\n" +
               "    public Page<" + entityName + "> findAll(Pageable pageable) {\n" +
               "        return " + entityName.toLowerCase() + "Repository.findAll(pageable);\n" +
               "    }\n\n" +
               "    public Page<" + entityName + "> findByNameContaining(String name, Pageable pageable) {\n" +
               "        return " + entityName.toLowerCase() + "Repository.findByNameContainingIgnoreCase(name, pageable);\n" +
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
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.property("entityName", new Tool.StringSchema().description("Entity class name"));
        schema.property("packageName", new Tool.StringSchema().description("Service package name"));
        schema.required("entityName");
        return schema;
    }
}




