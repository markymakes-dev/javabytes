package com.javabytes.mcp.skills.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

import java.util.Locale;
import java.util.Map;

/**
 * Reads environment variables.
 *
 * <p>Values whose names look like credentials (password, secret, token, key,
 * auth, credential) are masked by default so that a model cannot exfiltrate
 * secrets just by listing the environment. Callers can opt in to the raw value
 * with {@code reveal=true} for a specific variable.</p>
 */
public class EnvironmentVariablesSkill implements Skill {

    private static final int MAX_LISTED = 200;
    private static final String MASK = "********";

    @Override
    public String getName() { return "environment_variables"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String action = params.has("action") ? params.get("action").asText() : "list";
        String name = params.has("name") ? params.get("name").asText() : null;
        String value = params.has("value") ? params.get("value").asText() : null;
        boolean reveal = params.has("reveal") && params.get("reveal").asBoolean();
        try {
            Map<String, String> env = System.getenv();
            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("action", action);
            if ("list".equals(action)) {
                ObjectNode vars = JsonUtils.MAPPER.createObjectNode();
                int count = 0;
                int masked = 0;
                for (Map.Entry<String, String> e : env.entrySet()) {
                    if (count >= MAX_LISTED) {
                        break;
                    }
                    boolean sensitive = isSensitive(e.getKey());
                    vars.put(e.getKey(), sensitive ? MASK : e.getValue());
                    count++;
                    if (sensitive) {
                        masked++;
                    }
                }
                result.set("vars", vars);
                result.put("count", count);
                result.put("masked", masked);
                result.put("truncated", env.size() > count);
            } else if ("get".equals(action)) {
                if (name == null || name.isBlank()) {
                    return ErrorHelper.error("MISSING_PARAM", "Parameter 'name' required");
                }
                String val = env.get(name);
                boolean sensitive = isSensitive(name);
                result.put("name", name);
                result.put("found", val != null);
                result.put("masked", sensitive && !reveal);
                result.put("value", val == null ? "not found" : (sensitive && !reveal ? MASK : val));
            } else if ("set".equals(action)) {
                if (name == null || value == null) {
                    return ErrorHelper.error("MISSING_PARAM", "Parameters 'name' and 'value' required");
                }
                result.put("name", name);
                result.put("value", isSensitive(name) ? MASK : value);
                result.put("message", "Java cannot modify the process environment; set the variable before launching the server");
                result.put("set", false);
            } else {
                return ErrorHelper.error("INVALID_ACTION", "Unknown action: " + action);
            }
            return result;
        } catch (Exception e) {
            return ErrorHelper.error("ENV_ERROR", "Error: " + e.getMessage());
        }
    }

    static boolean isSensitive(String name) {
        String key = name.toUpperCase(Locale.ROOT);
        return key.contains("PASSWORD")
                || key.contains("PASSWD")
                || key.contains("SECRET")
                || key.contains("TOKEN")
                || key.contains("CREDENTIAL")
                || key.contains("PRIVATE")
                || key.contains("API_KEY")
                || key.endsWith("_KEY")
                || key.contains("AUTH");
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.prop("action", new Skill.StringSchema().description("list, get, or set"));
        schema.prop("name", new Skill.StringSchema().description("Variable name"));
        schema.prop("value", new Skill.StringSchema().description("Variable value (set is not supported by the JVM)"));
        schema.prop("reveal", new Skill.BoolSchema().description("Return secret-like values unmasked for a single get"));
        return schema;
    }
}
