package com.javabytes.mcp.tools.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class GenerateSpringBootControllerTool implements Tool {

    @Override
    public String getName() { return "springboot_generate_controller"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String entityName = params.has("entityName") ? params.get("entityName").asText() : "";
        String packageName = params.has("packageName") ? params.get("packageName").asText() : "com.example.demo.controller";
        String basePath = params.has("basePath") ? params.get("basePath").asText() : "";

        try {
            String code = generateControllerCode(entityName, packageName, basePath);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("entityName", entityName);
            result.put("packageName", packageName);
            result.put("basePath", basePath);
            result.put("code", code);
            result.put("message", "Generated Spring Boot REST controller for: " + entityName);
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_CONTROLLER_ERROR", "Error generating controller: " + e.getMessage());
        }
    }

    private String generateControllerCode(String entityName, String packageName, String basePath) {
        String serviceName = entityName + "Service";
        String controllerName = entityName + "Controller";
        String path = basePath.isEmpty() ? "/" + entityName.toLowerCase() + "s" : basePath;

        return "package " + packageName + ";\n\n" +
               "import " + getServicePackage(entityName) + "." + serviceName + ";\n" +
               "import " + getEntityPackage(entityName) + "." + entityName + ";\n" +
               "import org.springframework.beans.factory.annotation.Autowired;\n" +
               "import org.springframework.http.ResponseEntity;\n" +
               "import org.springframework.web.bind.annotation.*;\n" +
               "import java.util.List;\n" +
               "import java.util.Optional;\n\n" +
               "@RestController\n" +
               "@RequestMapping(\"" + path + "\")\n" +
               "public class " + controllerName + " {\n\n" +
               "    @Autowired\n" +
               "    private " + serviceName + " " + serviceName.substring(0, 1).toLowerCase() + serviceName.substring(1) + ";\n\n" +
               "    @GetMapping\n" +
               "    public List<" + entityName + "> getAll() {\n" +
               "        return " + serviceName.substring(0, 1).toLowerCase() + serviceName.substring(1) + ".findAll();\n" +
               "    }\n\n" +
               "    @GetMapping(\"/{id}\")\n" +
               "    public ResponseEntity<" + entityName + "> getById(@PathVariable Long id) {\n" +
               "        return " + serviceName.substring(0, 1).toLowerCase() + serviceName.substring(1) + ".findById(id)\n" +
               "                .map(ResponseEntity::ok)\n" +
               "                .orElse(ResponseEntity.notFound().build());\n" +
               "    }\n\n" +
               "    @PostMapping\n" +
               "    public " + entityName + " create(@RequestBody " + entityName + " entity) {\n" +
               "        return " + serviceName.substring(0, 1).toLowerCase() + serviceName.substring(1) + ".save(entity);\n" +
               "    }\n\n" +
               "    @DeleteMapping(\"/{id}\")\n" +
               "    public void delete(@PathVariable Long id) {\n" +
               "        " + serviceName.substring(0, 1).toLowerCase() + serviceName.substring(1) + ".deleteById(id);\n" +
               "    }\n" +
               "}\n";
    }

    private String getServicePackage(String entityName) {
        return "com.example.demo.service";
    }

    private String getEntityPackage(String entityName) {
        return "com.example.demo.entity";
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.property("entityName", new Tool.StringSchema().description("Entity class name (e.g., Employee, Order)"));
        schema.property("packageName", new Tool.StringSchema().description("Controller package name (default: com.example.demo.controller)"));
        schema.property("basePath", new Tool.StringSchema().description("Base URL path (optional, auto-generated if not provided)"));
        schema.required("entityName");
        return schema;
    }
}




