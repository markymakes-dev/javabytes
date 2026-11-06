package com.javabytes.mcp.skills.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class GenerateSpringBootExceptionHandlerSkill implements Skill {

    @Override
    public String getName() { return "springboot_generate_exception_handler"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String packageName = params.has("packageName") ? params.get("packageName").asText() : "com.example.demo.exception";

        try {
            String code = generateExceptionHandlerCode(packageName);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("packageName", packageName);
            result.put("code", code);
            result.put("message", "Generated Spring Boot global exception handler");
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_EXCEPTION_HANDLER_ERROR", "Error generating exception handler: " + e.getMessage());
        }
    }

    private String generateExceptionHandlerCode(String packageName) {
        return "package " + packageName + ";\n\n" +
               "import org.springframework.http.HttpStatus;\n" +
               "import org.springframework.http.ResponseEntity;\n" +
               "import org.springframework.web.bind.annotation.ExceptionHandler;\n" +
               "import org.springframework.web.bind.annotation.RestControllerAdvice;\n" +
               "import java.time.LocalDateTime;\n\n" +
               "@RestControllerAdvice\n" +
               "public class GlobalExceptionHandler {\n\n" +
               "    @ExceptionHandler(RuntimeException.class)\n" +
               "    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {\n" +
               "        ErrorResponse error = new ErrorResponse(\n" +
               "            LocalDateTime.now(),\n" +
               "            HttpStatus.INTERNAL_SERVER_ERROR.value(),\n" +
               "            ex.getMessage(),\n" +
               "            \"Internal Server Error\"\n" +
               "        );\n" +
               "        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);\n" +
               "    }\n\n" +
               "    @ExceptionHandler(IllegalArgumentException.class)\n" +
               "    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {\n" +
               "        ErrorResponse error = new ErrorResponse(\n" +
               "            LocalDateTime.now(),\n" +
               "            HttpStatus.BAD_REQUEST.value(),\n" +
               "            ex.getMessage(),\n" +
               "            \"Bad Request\"\n" +
               "        );\n" +
               "        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);\n" +
               "    }\n\n" +
               "    public record ErrorResponse(\n" +
               "        LocalDateTime timestamp,\n" +
               "        int status,\n" +
               "        String message,\n" +
               "        String error\n" +
               "    ) {}\n" +
               "}\n";
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.property("packageName", new Skill.StringSchema().description("Exception package name"));
        return schema;
    }
}




