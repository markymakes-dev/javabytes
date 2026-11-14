package com.javabytes.mcp.tools.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.database.MyDatabaseClient;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

import java.util.regex.Pattern;

/**
 * Runs MySQL {@code EXPLAIN} (optionally {@code EXPLAIN ANALYZE}) on a
 * read-only statement and returns the plan rows.
 */
public class ExplainQueryTool implements Tool {

    /** EXPLAIN is only allowed for statements that cannot write. */
    private static final Pattern SAFE_INNER = Pattern.compile("(?is)^(SELECT|WITH|TABLE)\\b");

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
            MyDatabaseClient db = new MyDatabaseClient();
            JsonNode result = db.query(prefix + statement);
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
        schema.required("sql");
        return schema;
    }
}
