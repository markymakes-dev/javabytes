package com.javabytes.mcp.tools.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.javabytes.mcp.database.MyDatabaseClient;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class ListTablesTool implements Tool {

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
    public Tool.JsonSchema getInputSchema() {
        return new Tool.JsonSchema();
    }
}
