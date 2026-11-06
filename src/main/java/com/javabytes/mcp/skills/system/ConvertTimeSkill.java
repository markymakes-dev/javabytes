package com.javabytes.mcp.skills.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

import java.time.*;
import java.time.format.DateTimeFormatter;

public class ConvertTimeSkill implements Skill {

    @Override
    public String getName() { return "convert_time"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String timeStr = params.has("time") ? params.get("time").asText() : "";
        String targetTz = params.has("timezone") ? params.get("timezone").asText() : "UTC";
        try {
            ZonedDateTime zdt;
            if (timeStr.isEmpty()) {
                zdt = ZonedDateTime.now();
            } else {
                try {
                    zdt = ZonedDateTime.parse(timeStr);
                } catch (Exception e) {
                    LocalDateTime ldt = LocalDateTime.parse(timeStr);
                    zdt = ldt.atZone(ZoneId.systemDefault());
                }
            }
            ZoneId tz = ZoneId.of(targetTz);
            ZonedDateTime converted = zdt.withZoneSameInstant(tz);
            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("original", timeStr);
            result.put("from_tz", zdt.getZone().getId());
            result.put("to_tz", targetTz);
            result.put("converted", converted.toString());
            return result;
        } catch (Exception e) {
            return ErrorHelper.error("TIME_ERROR", e.getMessage());
        }
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.prop("time", new Skill.StringSchema().description("ISO time string"));
        schema.prop("timezone", new Skill.StringSchema().description("Target timezone"));
        return schema;
    }
}
