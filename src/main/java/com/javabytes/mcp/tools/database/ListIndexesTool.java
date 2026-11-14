package com.javabytes.mcp.tools.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.database.MyDatabaseClient;
import com.javabytes.mcp.database.SchemaCatalog;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Lists indexes (including the composite column order) from information_schema.
 */
public class ListIndexesTool implements Tool {

    @Override
    public String getName() { return "list_indexes"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "list_indexes",
                "List Indexes",
                "List indexes, uniqueness and column order for a table or the whole schema.",
                "database",
                true,
                true,
                false,
                true,
                true);
    }

    @Override
    public JsonNode execute(JsonNode params) {
        String tableName = params.has("table_name") ? params.get("table_name").asText().trim() : "";
        if (!tableName.isEmpty() && !SchemaCatalog.isIdentifier(tableName)) {
            return ErrorHelper.error("INVALID_IDENTIFIER",
                    "table_name must be a plain identifier: " + tableName);
        }

        String filter = tableName.isEmpty() ? "" : " AND TABLE_NAME = " + SchemaCatalog.literal(tableName);
        String sql = "SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME, INDEX_TYPE "
                + "FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA = DATABASE()" + filter + " "
                + "ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX";

        try {
            MyDatabaseClient db = new MyDatabaseClient();
            ArrayNode rows = SchemaCatalog.rows(db.query(sql));

            Map<String, ObjectNode> byKey = new LinkedHashMap<>();
            for (JsonNode row : rows) {
                String table = SchemaCatalog.text(row, "TABLE_NAME");
                String index = SchemaCatalog.text(row, "INDEX_NAME");
                String key = table + "\u0000" + index;
                ObjectNode entry = byKey.get(key);
                if (entry == null) {
                    entry = JsonUtils.MAPPER.createObjectNode();
                    entry.put("table", table);
                    entry.put("index", index);
                    entry.put("unique", !SchemaCatalog.bool(row, "NON_UNIQUE"));
                    entry.put("type", SchemaCatalog.text(row, "INDEX_TYPE"));
                    entry.set("columns", JsonUtils.MAPPER.createArrayNode());
                    byKey.put(key, entry);
                }
                ((ArrayNode) entry.get("columns")).add(SchemaCatalog.text(row, "COLUMN_NAME"));
            }

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            ArrayNode indexes = JsonUtils.MAPPER.createArrayNode();
            byKey.values().forEach(indexes::add);
            result.set("indexes", indexes);
            result.put("count", indexes.size());
            if (!tableName.isEmpty()) {
                result.put("table", tableName);
            }
            return result;
        } catch (Exception e) {
            return ErrorHelper.error("DB_ERROR", "Error listing indexes: " + e.getMessage());
        }
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("table_name", new Tool.StringSchema()
                .description("Optional plain table name; omit to list indexes for every table"));
        return schema;
    }
}
