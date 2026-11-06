package com.javabytes.mcp.skills.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.javabytes.mcp.database.MyDatabaseClient;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class ListTablesSkill implements Skill {

    @Override
    public String getName() { return "list_tables"; }

    @Override
    public JsonNode execute(JsonNode params) {
        try {
            MyDatabaseClient db = new MyDatabaseClient();
            return db.query("SHOW TABLES");
        } catch (Exception e) {
            return ErrorHelper.error("DB_ERROR", "Error listing tables: " + e.getMessage());
        }
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        return new Skill.JsonSchema();
    }
}
