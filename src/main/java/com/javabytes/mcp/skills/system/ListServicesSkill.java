package com.javabytes.mcp.skills.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.JsonUtils;

import java.util.List;
import java.util.function.Supplier;

/**
 * Reports the tools the server actually registered, instead of a hard-coded list.
 */
public class ListServicesSkill implements Skill {

    private final Supplier<List<String>> catalog;

    public ListServicesSkill(Supplier<List<String>> catalog) {
        this.catalog = catalog;
    }

    @Override
    public String getName() { return "list_services"; }

    @Override
    public JsonNode execute(JsonNode params) {
        ObjectNode result = JsonUtils.MAPPER.createObjectNode();
        result.put("success", true);
        ArrayNode services = JsonUtils.MAPPER.createArrayNode();
        List<String> names = catalog != null ? catalog.get() : List.of();
        names.stream().sorted().forEach(services::add);
        result.set("services", services);
        result.put("count", services.size());
        return result;
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        return new Skill.JsonSchema();
    }
}
