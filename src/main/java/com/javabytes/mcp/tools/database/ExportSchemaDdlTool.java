package com.javabytes.mcp.tools.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.database.DatabaseService;
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

public class ExportSchemaDdlTool implements Tool {

    private static final int MAX_TABLES = 200;

    private final DatabaseService db;

    public ExportSchemaDdlTool() {
        this(new MyDatabaseClient());
    }

    /** Test-only construction with an explicit database service. */
    ExportSchemaDdlTool(DatabaseService db) {
        this.db = db;
    }

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
        String jdbcUrl = params.has("jdbc_url") ? params.get("jdbc_url").asText() : null;
        if (!requested.isEmpty() && !SchemaCatalog.isIdentifier(requested)) {
            return ErrorHelper.error("INVALID_IDENTIFIER",
                    "table_name must be a plain identifier: " + requested);
        }

        try {
            String database = currentDatabase(jdbcUrl);

            List<String> tables = new ArrayList<>();
            if (requested.isEmpty()) {
                tables.addAll(tableNames(jdbcUrl));
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
                JsonNode result = db.query("SHOW CREATE TABLE " + SchemaCatalog.quote(table), jdbcUrl);
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

    private String currentDatabase(String jdbcUrl) {
        try {
            JsonNode result = db.query("SELECT DATABASE() AS db", jdbcUrl);
            if (result != null && result.path("success").asBoolean(false)) {
                ArrayNode rows = (ArrayNode) result.get("rows");
                if (rows != null && !rows.isEmpty()) {
                    JsonNode value = rows.get(0).get("db");
                    if (value != null && !value.isNull()) {
                        String db = value.asText();
                        return db.isBlank() ? null : db;
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private List<String> tableNames(String jdbcUrl) {
        List<String> names = new ArrayList<>();
        try {
            JsonNode result = db.query(
                    "SELECT TABLE_NAME AS name FROM information_schema.TABLES "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE' "
                    + "ORDER BY TABLE_NAME", jdbcUrl);
            if (result != null && result.path("success").asBoolean(false)) {
                ArrayNode rows = (ArrayNode) result.get("rows");
                if (rows != null) {
                    for (JsonNode row : rows) {
                        names.add(row.path("name").asText());
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return names;
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
        schema.prop("jdbc_url", new Tool.StringSchema()
                .description("Optional JDBC URL to target a specific database (credentials embedded in URL); uses environment default when omitted"));
        return schema;
    }
}