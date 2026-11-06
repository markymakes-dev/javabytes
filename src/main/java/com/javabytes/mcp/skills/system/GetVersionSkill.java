package com.javabytes.mcp.skills.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.JsonUtils;

public class GetVersionSkill implements Skill {

    @Override
    public String getName() { return "get_version"; }

    @Override
    public JsonNode execute(JsonNode params) {
        ObjectNode result = JsonUtils.MAPPER.createObjectNode();
        result.put("success", true);
        result.put("version", "1.0.0");
        result.put("java_version", System.getProperty("java.version"));
        return result;
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        return new Skill.JsonSchema();
    }
}
