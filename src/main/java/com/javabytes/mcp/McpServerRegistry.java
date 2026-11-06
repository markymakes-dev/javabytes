package com.javabytes.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

import java.util.*;

public class McpServerRegistry {

    private final Map<String, Skill> skills = new HashMap<>();

    public void register(Skill skill) {
        skills.put(skill.getName(), skill);
        // Only JSON-RPC responses may go to stdout; log to stderr
        System.err.println("Registered skill: " + skill.getName());
    }

    public Skill getSkill(String skillName) {
        return skills.get(skillName);
    }

    public JsonNode execute(String skillName, JsonNode params) {
        Skill skill = skills.get(skillName);
        if (skill == null) {
            return ErrorHelper.error("SKILL_NOT_FOUND", "Skill '" + skillName + "' not registered");
        }
        try {
            return skill.execute(params);
        } catch (Exception e) {
            return ErrorHelper.error("SKILL_EXECUTION_ERROR", "Error executing '" + skillName + "': " + e.getMessage());
        }
    }

    public List<String> listSkills() {
        return new ArrayList<>(skills.keySet());
    }

    public int getSkillCount() {
        return skills.size();
    }

    public boolean hasSkill(String skillName) {
        return skills.containsKey(skillName);
    }
}
