package com.javabytes.mcp.tools.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.javabytes.mcp.database.MyDatabaseClient;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class ListTablesTool implements Tool {

    @Override
    public String getName() { return "list_tables"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "list_tables",
                "List Database Tables",
                "List the tables and views in the configured MySQL database.",
                "database",
                true,
                true,
                false,
                true,
                true);
    }

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
    public Tool.JsonSchema getInputSchema() {
        return new Tool.JsonSchema();
    }
}
