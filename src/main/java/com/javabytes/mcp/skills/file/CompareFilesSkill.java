package com.javabytes.mcp.skills.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.PathValidator;

import java.nio.file.*;
import java.util.*;

public class CompareFilesSkill implements Skill {

    @Override
    public String getName() { return "compare_files"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String path1 = params.has("path1") ? params.get("path1").asText() : "";
        String path2 = params.has("path2") ? params.get("path2").asText() : "";
        try {
            Path p1 = Path.of(path1);
            Path p2 = Path.of(path2);
            if (!PathValidator.isAllowed(p1)) {
                return ErrorHelper.error("PATH_NOT_ALLOWED", "Path not allowed: " + path1);
            }
            if (!PathValidator.isAllowed(p2)) {
                return ErrorHelper.error("PATH_NOT_ALLOWED", "Path not allowed: " + path2);
            }
            if (!Files.exists(p1) || !Files.isRegularFile(p1)) {
                return ErrorHelper.error("FILE_NOT_FOUND", "File not found: " + path1);
            }
            if (!Files.exists(p2) || !Files.isRegularFile(p2)) {
                return ErrorHelper.error("FILE_NOT_FOUND", "File not found: " + path2);
            }
            String content1 = Files.readString(p1);
            String content2 = Files.readString(p2);
            List<String> lines1 = Arrays.asList(content1.split("\\r?\\n"));
            List<String> lines2 = Arrays.asList(content2.split("\\r?\\n"));
            ArrayNode diffs = JsonUtils.MAPPER.createArrayNode();
            int maxLines = Math.max(lines1.size(), lines2.size());
            int diffCount = 0;
            for (int i = 0; i < maxLines; i++) {
                String l1 = i < lines1.size() ? lines1.get(i) : null;
                String l2 = i < lines2.size() ? lines2.get(i) : null;
                if (!Objects.equals(l1, l2)) {
                    ObjectNode d = JsonUtils.MAPPER.createObjectNode();
                    d.put("line", i + 1);
                    d.put("file1", l1 != null ? l1 : "(missing)");
                    d.put("file2", l2 != null ? l2 : "(missing)");
                    diffs.add(d);
                    diffCount++;
                }
            }
            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("file1", path1);
            result.put("file2", path2);
            result.put("identical", diffCount == 0);
            result.put("diff_count", diffCount);
            result.set("diffs", diffs);
            result.put("file1_lines", lines1.size());
            result.put("file2_lines", lines2.size());
            return result;
        } catch (Exception e) {
            return ErrorHelper.error("COMPARE_ERROR", "Error comparing files: " + e.getMessage());
        }
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.prop("path1", new Skill.StringSchema().description("First file path"));
        schema.prop("path2", new Skill.StringSchema().description("Second file path"));
        schema.required("path1");
        schema.required("path2");
        return schema;
    }
}
