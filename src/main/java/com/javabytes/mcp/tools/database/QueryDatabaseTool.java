package com.javabytes.mcp.tools.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.javabytes.mcp.database.MyDatabaseClient;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class QueryDatabaseTool implements Tool {

    @Override
    public String getName() { return "query_database"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "query_database",
                "Query Database",
                "Run a read-only SQL query against the configured MySQL database and return rows.",
                "database",
                true,
                true,
                false,
                true,
                true);
    }

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
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("sql", new Tool.StringSchema().description("SQL query to execute"));
        schema.required("sql");
        return schema;
    }
}
