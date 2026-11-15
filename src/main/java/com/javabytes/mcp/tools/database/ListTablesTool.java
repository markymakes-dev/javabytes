package com.javabytes.mcp.tools.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.javabytes.mcp.database.DatabaseService;
import com.javabytes.mcp.database.MyDatabaseClient;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class ListTablesTool implements Tool {

    private final DatabaseService db;

    public ListTablesTool() {
        this(new MyDatabaseClient());
    }

    /** Test-only construction with an explicit database service. */
    ListTablesTool(DatabaseService db) {
        this.db = db;
    }

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
        String jdbcUrl = params.has("jdbc_url") ? params.get("jdbc_url").asText() : null;
        try {
            return db.query("SHOW TABLES", jdbcUrl);
        } catch (Exception e) {
            return ErrorHelper.error("DB_ERROR", "Error listing tables: " + e.getMessage());
        }
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("jdbc_url", new Tool.StringSchema()
                .description("Optional JDBC URL to target a specific database (credentials embedded in URL); uses environment default when omitted"));
        return schema;
    }
}