package com.javabytes.mcp.skills.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.javabytes.mcp.database.MyDatabaseClient;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class QueryDatabaseSkill implements Skill {

    @Override
    public String getName() { return "query_database"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String sql = params.has("sql") ? params.get("sql").asText() : "";
        if (sql.isEmpty()) {
            return ErrorHelper.error("MISSING_PARAM", "Parameter 'sql' is required");
        }
        try {
            MyDatabaseClient db = new MyDatabaseClient();
            return db.query(sql);
        } catch (Exception e) {
            return ErrorHelper.error("DB_ERROR", "Database error: " + e.getMessage());
        }
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.prop("sql", new Skill.StringSchema().description("SQL query to execute"));
        schema.required("sql");
        return schema;
    }
}
