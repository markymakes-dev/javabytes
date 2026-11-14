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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Exports {@code SHOW CREATE TABLE} DDL for one table or the whole schema.
 */
public class ExportSchemaDdlTool implements Tool {

    private static final int MAX_TABLES = 200;

    @Override
    public String getName() { return "export_schema_ddl"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "export_schema_ddl",
                "Export Schema DDL",
                "Export SHOW CREATE TABLE DDL for one table or every base table.",
                "database",
                true,
                true,
                false,
                true,
                true);
    }

    @Override
    public JsonNode execute(JsonNode params) {
        String requested = params.has("table_name") ? params.get("table_name").asText().trim() : "";
        if (!requested.isEmpty() && !SchemaCatalog.isIdentifier(requested)) {
            return ErrorHelper.error("INVALID_IDENTIFIER",
                    "table_name must be a plain identifier: " + requested);
        }

        try {
            MyDatabaseClient db = new MyDatabaseClient();
            String database = SchemaCatalog.currentDatabase(db);

            List<String> tables = new ArrayList<>();
            if (requested.isEmpty()) {
                tables.addAll(SchemaCatalog.tableNames(db));
            } else {
                tables.add(requested);
            }
            boolean truncated = tables.size() > MAX_TABLES;
            if (truncated) {
                tables = tables.subList(0, MAX_TABLES);
            }

            ArrayNode entries = JsonUtils.MAPPER.createArrayNode();
            StringBuilder all = new StringBuilder();
            for (String table : tables) {
                JsonNode result = db.query("SHOW CREATE TABLE " + SchemaCatalog.quote(table));
                ArrayNode rows = SchemaCatalog.rows(result);
                if (rows.isEmpty()) {
                    continue;
                }
                JsonNode row = rows.get(0);
                String ddl = createStatement(row);
                if (ddl.isEmpty()) {
                    continue;
                }
                ObjectNode entry = JsonUtils.MAPPER.createObjectNode();
                entry.put("table", table);
                entry.put("ddl", ddl);
                entries.add(entry);
                if (!all.isEmpty()) {
                    all.append("\n\n");
                }
                all.append(ddl).append(';');
            }

            ObjectNode out = JsonUtils.MAPPER.createObjectNode();
            out.put("success", true);
            if (database != null) {
                out.put("database", database);
            }
            out.set("tables", entries);
            out.put("table_count", entries.size());
            out.put("ddl", all.toString());
            out.put("truncated", truncated);
            return out;
        } catch (Exception e) {
            return ErrorHelper.error("DB_ERROR", "Error exporting schema DDL: " + e.getMessage());
        }
    }

    /** SHOW CREATE TABLE returns columns named "Table" and "Create Table". */
    private static String createStatement(JsonNode row) {
        Iterator<Map.Entry<String, JsonNode>> fields = row.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getKey().toLowerCase(java.util.Locale.ROOT).startsWith("create")) {
                return field.getValue().asText();
            }
        }
        return "";
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("table_name", new Tool.StringSchema()
                .description("Optional plain table name; omit to export every base table"));
        return schema;
    }
}
