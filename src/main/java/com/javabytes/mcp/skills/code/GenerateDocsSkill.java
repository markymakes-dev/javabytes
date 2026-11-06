package com.javabytes.mcp.skills.code;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class GenerateDocsSkill implements Skill {

    @Override
    public String getName() { return "generate_docs"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String code = params.has("code") ? params.get("code").asText() : "";
        String language = params.has("language") ? params.get("language").asText() : "java";
        String docFormat = params.has("docFormat") ? params.get("docFormat").asText() : "markdown";

        try {
            String docs = generateDocumentation(code, language, docFormat);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("language", language);
            result.put("docFormat", docFormat);
            result.put("documentation", docs);
            result.put("message", "Generated " + docFormat + " documentation");
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_DOCS_ERROR", "Error generating docs: " + e.getMessage());
        }
    }

    private String generateDocumentation(String code, String language, String docFormat) {
        StringBuilder docs = new StringBuilder();
        docs.append("# Documentation\n\n");
        docs.append("## Overview\n\n");
        docs.append("Generated documentation for ").append(language).append(" code.\n\n");
        docs.append("## Code Summary\n\n");
        docs.append("- Lines: ").append(code.split("\\r?\\n").length).append("\n");
        docs.append("- Language: ").append(language).append("\n\n");
        docs.append("## Implementation\n\n");
        docs.append("```").append(language).append("\n");
        docs.append(code);
        docs.append("\n```\n");
        return docs.toString();
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.prop("code", new Skill.StringSchema().description("Source code to document"));
        schema.prop("language", new Skill.StringSchema().description("Programming language"));
        schema.prop("docFormat", new Skill.StringSchema().description("Documentation format"));
        schema.required("code");
        return schema;
    }
}
