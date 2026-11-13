package com.javabytes.mcp.tools.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.javabytes.mcp.database.MyDatabaseClient;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DescribeTableTool implements Tool {

    /** Plain SQL identifiers only; this prevents injection through table_name. */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

    @Override
    public String getName() { return "describe_table"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String tableName = params.has("table_name") ? params.get("table_name").asText().trim() : "";
        if (tableName.isEmpty()) {
            return ErrorHelper.error("MISSING_PARAM", "Parameter 'table_name' is required");
        }
        String[] parts = tableName.split("\\.", -1);
        if (parts.length > 2 || Arrays.stream(parts).anyMatch(p -> !IDENTIFIER.matcher(p).matches())) {
            return ErrorHelper.error("INVALID_IDENTIFIER",
                    "table_name must be a plain identifier or schema.table: " + tableName);
        }
        // Quote every validated segment with MySQL backticks.
        String qualified = Arrays.stream(parts)
                .map(p -> "`" + p + "`")
                .collect(Collectors.joining("."));
        try {
            MyDatabaseClient db = new MyDatabaseClient();
            return db.query("DESCRIBE " + qualified);
        } catch (Exception e) {
            return ErrorHelper.error("DB_ERROR", "Error describing table: " + e.getMessage());
        }
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("table_name", new Tool.StringSchema().description("Table to describe, optionally schema-qualified"));
        schema.required("table_name");
        return schema;
    }
}
