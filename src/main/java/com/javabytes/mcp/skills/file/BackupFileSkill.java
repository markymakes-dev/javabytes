package com.javabytes.mcp.skills.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.PathValidator;

import java.nio.file.*;
import java.time.format.DateTimeFormatter;

public class BackupFileSkill implements Skill {

    @Override
    public String getName() { return "backup_file"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String path = params.has("path") ? params.get("path").asText() : "";
        String suffix = params.has("suffix") ? params.get("suffix").asText() : "_backup";
        try {
            Path source = Path.of(path);
            if (!PathValidator.isAllowed(source)) {
                return ErrorHelper.error("PATH_NOT_ALLOWED", "Path not allowed: " + path);
            }
            if (!Files.exists(source) || !Files.isRegularFile(source)) {
                return ErrorHelper.error("FILE_NOT_FOUND", "File not found: " + path);
            }
            String fileName = source.getFileName().toString();
            String parent = source.getParent().toString();
            String timestamp = java.time.LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
            int dotIndex = fileName.lastIndexOf('.');
            String base;
            String extension;
            if (dotIndex > 0) {
                base = fileName.substring(0, dotIndex);
                extension = fileName.substring(dotIndex);
            } else {
                base = fileName;
                extension = "";
            }
            Path backupPath = Path.of(parent, base + suffix + "_" + timestamp + extension);
            // Guard against same-millisecond collisions and never overwrite an
            // existing backup.
            int attempt = 1;
            while (Files.exists(backupPath)) {
                backupPath = Path.of(parent, base + suffix + "_" + timestamp + "_" + attempt++ + extension);
            }
            String backupName = backupPath.getFileName().toString();
            Files.copy(source, backupPath);
            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("original_path", path);
            result.put("backup_path", backupPath.toString());
            result.put("backup_name", backupName);
            result.put("size_bytes", Files.size(backupPath));
            return result;
        } catch (Exception e) {
            return ErrorHelper.error("BACKUP_ERROR", "Error backing up file: " + e.getMessage());
        }
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.prop("path", new Skill.StringSchema().description("File path"));
        schema.prop("suffix", new Skill.StringSchema().description("Backup suffix"));
        schema.required("path");
        return schema;
    }
}
