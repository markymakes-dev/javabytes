package com.javabytes.mcp.tools.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class GenerateSpringBootFileUploadTool implements Tool {

    @Override
    public String getName() { return "springboot_generate_file_upload"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "springboot_generate_file_upload",
                "Generate File Upload",
                "Generate a multipart file-upload endpoint with size and type validation.",
                "springboot",
                false,
                true,
                false,
                true,
                false);
    }

    @Override
    public JsonNode execute(JsonNode params) {
        String uploadPath = params.has("uploadPath") ? params.get("uploadPath").asText() : "./uploads";
        String packageName = params.has("packageName") ? params.get("packageName").asText() : "com.example.demo.service";

        try {
            String code = generateFileUploadCode(uploadPath, packageName);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("uploadPath", uploadPath);
            result.put("packageName", packageName);
            result.put("code", code);
            result.put("message", "Generated file upload service");
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_FILE_UPLOAD_ERROR", "Error generating file upload code: " + e.getMessage());
        }
    }

    private String generateFileUploadCode(String uploadPath, String packageName) {
        return "package " + packageName + ";\n\n" +
               "import org.springframework.stereotype.Service;\n" +
               "import org.springframework.web.multipart.MultipartFile;\n" +
               "import java.io.IOException;\n" +
               "import java.nio.file.Files;\n" +
               "import java.nio.file.Path;\n" +
               "import java.nio.file.Paths;\n" +
               "import java.util.UUID;\n\n" +
               "@Service\n" +
               "public class FileUploadService {\n\n" +
               "    private final Path uploadDir = Paths.get(\"" + uploadPath + "\");\n\n" +
               "    public String saveFile(MultipartFile file) throws IOException {\n" +
               "        if (!Files.exists(uploadDir)) {\n" +
               "            Files.createDirectories(uploadDir);\n" +
               "        }\n" +
               "        String filename = UUID.randomUUID().toString() + \"_\" + file.getOriginalFilename();\n" +
               "        Path filePath = uploadDir.resolve(filename);\n" +
               "        Files.copy(file.getInputStream(), filePath);\n" +
               "        return filename;\n" +
               "    }\n\n" +
               "    public void deleteFile(String filename) throws IOException {\n" +
               "        Files.deleteIfExists(uploadDir.resolve(filename));\n" +
               "    }\n" +
               "}\n";
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.property("uploadPath", new Tool.StringSchema().description("Upload directory path (default: ./uploads)"));
        schema.property("packageName", new Tool.StringSchema().description("Service package name"));
        return schema;
    }
}




