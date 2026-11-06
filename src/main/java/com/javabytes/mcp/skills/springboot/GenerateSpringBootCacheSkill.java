package com.javabytes.mcp.skills.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class GenerateSpringBootCacheSkill implements Skill {

    @Override
    public String getName() { return "springboot_generate_cache"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String serviceName = params.has("serviceName") ? params.get("serviceName").asText() : "CachedService";
        String packageName = params.has("packageName") ? params.get("packageName").asText() : "com.example.demo.service";
        String cacheName = params.has("cacheName") ? params.get("cacheName").asText() : "myCache";

        try {
            String code = generateCacheCode(serviceName, packageName, cacheName);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("serviceName", serviceName);
            result.put("packageName", packageName);
            result.put("cacheName", cacheName);
            result.put("code", code);
            result.put("message", "Generated cached service: " + serviceName);
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_CACHE_ERROR", "Error generating cache service: " + e.getMessage());
        }
    }

    private String generateCacheCode(String serviceName, String packageName, String cacheName) {
        return "package " + packageName + ";\n\n" +
               "import org.springframework.cache.annotation.CacheEvict;\n" +
               "import org.springframework.cache.annotation.Cacheable;\n" +
               "import org.springframework.stereotype.Service;\n\n" +
               "@Service\n" +
               "public class " + serviceName + " {\n\n" +
               "    @Cacheable(value = \"" + cacheName + "\", key = \"#id\")\n" +
               "    public String getData(Long id) {\n" +
               "        // Simulate expensive operation\n" +
               "        return \"Data for \" + id;\n" +
               "    }\n\n" +
               "    @CacheEvict(value = \"" + cacheName + "\", key = \"#id\")\n" +
               "    public void evict(Long id) {\n" +
               "        // Cache entry will be removed\n" +
               "    }\n" +
               "}\n";
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.property("serviceName", new Skill.StringSchema().description("Service class name"));
        schema.property("packageName", new Skill.StringSchema().description("Service package name"));
        schema.property("cacheName", new Skill.StringSchema().description("Cache name (default: myCache)"));
        schema.required("serviceName");
        return schema;
    }
}




