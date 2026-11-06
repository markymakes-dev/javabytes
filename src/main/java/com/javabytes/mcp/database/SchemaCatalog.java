package com.javabytes.mcp.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.javabytes.mcp.utils.JsonUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Small helpers for querying MySQL's {@code information_schema}.
 *
 * <p>Identifiers are validated and backtick-quoted before interpolation because
 * {@link MyDatabaseClient} only accepts raw SQL, not prepared statements.</p>
 */
public final class SchemaCatalog {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

    private SchemaCatalog() {
    }

    public static boolean isIdentifier(String value) {
        return value != null && IDENTIFIER.matcher(value).matches();
    }

    /** Quote a validated identifier, or throw. */
    public static String quote(String identifier) {
        if (!isIdentifier(identifier)) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);
        }
        return "`" + identifier + "`";
    }

    /** Quote a validated identifier as a SQL string literal, or throw. */
    public static String literal(String identifier) {
        if (!isIdentifier(identifier)) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);
        }
        return "'" + identifier + "'";
    }

    /** Extract the rows array from a {@link MyDatabaseClient} result or throw. */
    public static ArrayNode rows(JsonNode result) {
        if (result == null) {
            throw new IllegalStateException("Database returned no result");
        }
        if (!result.path("success").asBoolean(false)) {
            throw new IllegalStateException(result.path("message").asText("Database query failed"));
        }
        JsonNode rows = result.get("rows");
        return rows instanceof ArrayNode array ? array : JsonUtils.MAPPER.createArrayNode();
    }

    /** Current schema name, or null when the connection has no default schema. */
    public static String currentDatabase(MyDatabaseClient db) {
        JsonNode result = db.query("SELECT DATABASE() AS db");
        ArrayNode rows = rows(result);
        if (rows.isEmpty()) {
            return null;
        }
        JsonNode value = rows.get(0).get("db");
        return value == null || value.isNull() ? null : value.asText();
    }

    /** Base tables in the current schema, in name order. */
    public static List<String> tableNames(MyDatabaseClient db) {
        JsonNode result = db.query(
                "SELECT TABLE_NAME AS name FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE' "
                        + "ORDER BY TABLE_NAME");
        List<String> names = new ArrayList<>();
        for (JsonNode row : rows(result)) {
            names.add(row.path("name").asText());
        }
        return names;
    }

    public static String text(JsonNode row, String field) {
        JsonNode value = row.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    public static boolean bool(JsonNode row, String field) {
        return row.path(field).asBoolean(false);
    }
}
