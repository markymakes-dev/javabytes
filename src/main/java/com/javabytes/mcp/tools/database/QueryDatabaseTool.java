package com.javabytes.mcp.tools.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.javabytes.mcp.database.DatabaseService;
import com.javabytes.mcp.database.MyDatabaseClient;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class QueryDatabaseTool implements Tool {

    private final DatabaseService db;

    public QueryDatabaseTool() {
        this(new MyDatabaseClient());
    }

    /** Test-only construction with an explicit database service. */
    QueryDatabaseTool(DatabaseService db) {
        this.db = db;
    }

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
        String jdbcUrl = params.has("jdbc_url") ? params.get("jdbc_url").asText() : null;
        if (sql.isEmpty()) {
            return ErrorHelper.error("MISSING_PARAM", "Parameter 'sql' is required");
        }
        try {
            return db.query(sql, jdbcUrl);
        } catch (Exception e) {
            return ErrorHelper.error("DB_ERROR", "Database error: " + e.getMessage());
        }
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("sql", new Tool.StringSchema().description("SQL query to execute"));
        schema.prop("jdbc_url", new Tool.StringSchema()
                .description("Optional JDBC URL to target a specific database (credentials embedded in URL); uses environment default when omitted"));
        schema.required("sql");
        return schema;
    }
}