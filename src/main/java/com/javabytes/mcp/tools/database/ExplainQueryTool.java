package com.javabytes.mcp.tools.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.database.DatabaseService;
import com.javabytes.mcp.database.MyDatabaseClient;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

import java.util.Locale;
import java.util.regex.Pattern;

public class ExplainQueryTool implements Tool {

    private static final Pattern SAFE_INNER = Pattern.compile("(?is)^(SELECT|WITH|TABLE)\\b");

    private final DatabaseService db;

    public ExplainQueryTool() {
        this(new MyDatabaseClient());
    }

    /** Test-only construction with an explicit database service. */
    ExplainQueryTool(DatabaseService db) {
        this.db = db;
    }

    @Override
    public String getName() { return "explain_query"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "explain_query",
                "Explain Query Plan",
                "Run MySQL EXPLAIN (or EXPLAIN ANALYZE) on a read-only SELECT and return the plan.",
                "database",
                true,
                true,
                false,
                true,
                true);
    }

    @Override
    public JsonNode execute(JsonNode params) {
        String sql = params.has("sql") ? params.get("sql").asText().trim() : "";
        boolean analyze = params.has("analyze") && params.get("analyze").asBoolean(false);
        String jdbcUrl = params.has("jdbc_url") ? params.get("jdbc_url").asText() : null;

        if (sql.isEmpty()) {
            return ErrorHelper.error("MISSING_PARAM", "Parameter 'sql' is required");
        }
        String statement = sql.replaceAll(";\\s*$", "").trim();
        if (statement.indexOf(';') >= 0) {
            return ErrorHelper.error("multiple_statements", "Multiple SQL statements are not allowed");
        }
        if (!SAFE_INNER.matcher(statement).find()) {
            return ErrorHelper.error("read_only_violation",
                    "explain_query only accepts SELECT, WITH or TABLE statements");
        }

        String prefix = analyze ? "EXPLAIN ANALYZE " : "EXPLAIN ";
        try {
            JsonNode result = db.query(prefix + statement, jdbcUrl);
            if (result.isObject() && result.path("success").asBoolean(false)) {
                ObjectNode out = (ObjectNode) result;
                out.put("sql", statement);
                out.put("analyze", analyze);
                if (analyze) {
                    out.put("note", "EXPLAIN ANALYZE executes the SELECT while collecting timings.");
                }
            }
            return result;
        } catch (Exception e) {
            return ErrorHelper.error("DB_ERROR", "Error explaining query: " + e.getMessage());
        }
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("sql", new Tool.StringSchema()
                .description("SELECT, WITH or TABLE statement to explain"));
        schema.prop("analyze", new Tool.BoolSchema()
                .description("Use EXPLAIN ANALYZE and execute the SELECT to collect real timings (default false)"));
        schema.prop("jdbc_url", new Tool.StringSchema()
                .description("Optional JDBC URL to target a specific database (credentials embedded in URL); uses environment default when omitted"));
        schema.required("sql");
        return schema;
    }
}