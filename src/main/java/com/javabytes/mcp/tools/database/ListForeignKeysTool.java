package com.javabytes.mcp.tools.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.database.MyDatabaseClient;
import com.javabytes.mcp.database.SchemaCatalog;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lists foreign keys with their referenced table, rules and column order.
 */
public class ListForeignKeysTool implements Tool {

    @Override
    public String getName() { return "list_foreign_keys"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String tableName = params.has("table_name") ? params.get("table_name").asText().trim() : "";
        if (!tableName.isEmpty() && !SchemaCatalog.isIdentifier(tableName)) {
            return ErrorHelper.error("INVALID_IDENTIFIER",
                    "table_name must be a plain identifier: " + tableName);
        }

        String filter = tableName.isEmpty() ? "" : " AND k.TABLE_NAME = " + SchemaCatalog.literal(tableName);
        String sql = "SELECT k.CONSTRAINT_NAME, k.TABLE_NAME, k.COLUMN_NAME, "
                + "k.REFERENCED_TABLE_NAME, k.REFERENCED_COLUMN_NAME, "
                + "r.DELETE_RULE, r.UPDATE_RULE "
                + "FROM information_schema.KEY_COLUMN_USAGE k "
                + "JOIN information_schema.REFERENTIAL_CONSTRAINTS r "
                + "  ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA "
                + " AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME "
                + "WHERE k.TABLE_SCHEMA = DATABASE() AND k.REFERENCED_TABLE_NAME IS NOT NULL"
                + filter + " "
                + "ORDER BY k.TABLE_NAME, k.CONSTRAINT_NAME, k.ORDINAL_POSITION";

        try {
            MyDatabaseClient db = new MyDatabaseClient();
            ArrayNode rows = SchemaCatalog.rows(db.query(sql));

            Map<String, ObjectNode> byKey = new LinkedHashMap<>();
            for (JsonNode row : rows) {
                String table = SchemaCatalog.text(row, "TABLE_NAME");
                String constraint = SchemaCatalog.text(row, "CONSTRAINT_NAME");
                String key = table + "\u0000" + constraint;
                ObjectNode entry = byKey.get(key);
                if (entry == null) {
                    entry = JsonUtils.MAPPER.createObjectNode();
                    entry.put("table", table);
                    entry.put("constraint", constraint);
                    entry.put("referenced_table", SchemaCatalog.text(row, "REFERENCED_TABLE_NAME"));
                    entry.put("on_delete", SchemaCatalog.text(row, "DELETE_RULE"));
                    entry.put("on_update", SchemaCatalog.text(row, "UPDATE_RULE"));
                    entry.set("columns", JsonUtils.MAPPER.createArrayNode());
                    entry.set("referenced_columns", JsonUtils.MAPPER.createArrayNode());
                    byKey.put(key, entry);
                }
                ((ArrayNode) entry.get("columns")).add(SchemaCatalog.text(row, "COLUMN_NAME"));
                ((ArrayNode) entry.get("referenced_columns"))
                        .add(SchemaCatalog.text(row, "REFERENCED_COLUMN_NAME"));
            }

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            ArrayNode foreignKeys = JsonUtils.MAPPER.createArrayNode();
            byKey.values().forEach(foreignKeys::add);
            result.set("foreign_keys", foreignKeys);
            result.put("count", foreignKeys.size());
            if (!tableName.isEmpty()) {
                result.put("table", tableName);
            }
            return result;
        } catch (Exception e) {
            return ErrorHelper.error("DB_ERROR", "Error listing foreign keys: " + e.getMessage());
        }
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("table_name", new Tool.StringSchema()
                .description("Optional plain table name; omit to list every foreign key"));
        return schema;
    }
}
