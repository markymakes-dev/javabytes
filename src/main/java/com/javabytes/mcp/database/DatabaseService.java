package com.javabytes.mcp.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.javabytes.mcp.schema.Index;
import com.javabytes.mcp.schema.InformationSchemaReader;
import com.javabytes.mcp.schema.Relation;
import com.javabytes.mcp.schema.SchemaModel;
import com.javabytes.mcp.schema.Table;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The database seam (ADR 0007): all database access goes through this interface.
 *
 * <p>The production adapter is {@link MyDatabaseClient}; tests supply a fake
 * in-memory adapter. Every tool that reads or writes a database receives a
 * {@code DatabaseService} (or a request-scoped connection factory) instead of
 * constructing its own client from static environment variables.</p>
 *
 * <p>Per-request targeting: callers may supply a {@code jdbcUrl} (credentials
 * embedded in the URL) to target a specific database; when absent, the
 * environment-configured database is used exactly as before.</p>
 */
public interface DatabaseService {

    /**
     * Execute a read-only query against the target database.
     *
     * @param sql      the SQL statement (must be read-only per the guard)
     * @param jdbcUrl  optional JDBC URL; when null/blank, the environment default is used
     * @return {success, columns, rows, row_count, truncated} on success;
     *         {success:false, error, message} on failure
     */
    JsonNode query(String sql, String jdbcUrl);

    /**
     * Execute a read-only query using the environment default database.
     * Convenience overload for tools that do not accept a jdbcUrl param.
     */
    default JsonNode query(String sql) {
        return query(sql, null);
    }

    /**
     * Execute DDL/DML statements (the write path). Only {@code generate_database}
     * may call this; it is guarded by an explicit capability flag in the tool spec.
     *
     * @param sql      the SQL statement to execute
     * @param jdbcUrl  the target database (required for write operations)
     * @return per-statement result {success, statement, rows_affected, status}
     * @throws UnsupportedOperationException when called without the capability
     */
    JsonNode executeWrite(String sql, String jdbcUrl);

    /**
     * Get a raw connection to the target database (for catalog readers).
     */
    Connection getConnection(String jdbcUrl) throws SQLException;

    /**
     * Get a connection using the environment default.
     */
    default Connection getConnection() throws SQLException {
        return getConnection(null);
    }

    /**
     * The current database/schema name, or null when no default.
     */
    default String currentDatabase(String jdbcUrl) {
        try {
            JsonNode result = query("SELECT DATABASE() AS db", jdbcUrl);
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
            // ignore, return null
        }
        return null;
    }

    /**
     * Base table names in the current schema.
     */
    default List<String> tableNames(String jdbcUrl) {
        List<String> names = new ArrayList<>();
        try {
            JsonNode result = query(
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
            // ignore, return empty list
        }
        return names;
    }

    /**
     * Read the full schema model (tables, columns, indexes, foreign keys).
     */
    default SchemaModel readSchemaModel(String jdbcUrl) {
        try (Connection connection = getConnection(jdbcUrl)) {
            return new InformationSchemaReader(this).read(jdbcUrl);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read schema model", e);
        }
    }

    /**
     * Read a single table's columns, indexes, and primary key.
     */
    default Table readTable(String jdbcUrl, String tableName) {
        try {
            SchemaModel model = readSchemaModel(jdbcUrl);
            for (Table table : model.tables()) {
                if (table.name().equals(tableName)) {
                    return table;
                }
            }
        } catch (Exception e) {
            // ignore, return null
        }
        return null;
    }

    /**
     * Read all foreign keys for the current schema (or filtered by table).
     */
    default List<Relation> readRelations(String jdbcUrl, String tableName) {
        try {
            SchemaModel model = readSchemaModel(jdbcUrl);
            List<Relation> all = model.relations();
            if (tableName == null || tableName.isEmpty()) {
                return all;
            }
            List<Relation> filtered = new ArrayList<>();
            for (Relation r : all) {
                if (r.sourceTable().equals(tableName)) {
                    filtered.add(r);
                }
            }
            return filtered;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Read all indexes for the current schema (or filtered by table).
     */
    default List<Index> readIndexes(String jdbcUrl, String tableName) {
        try {
            SchemaModel model = readSchemaModel(jdbcUrl);
            for (Table table : model.tables()) {
                if (table.name().equals(tableName)) {
                    return table.indexes();
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return List.of();
    }

    /**
     * Quote a validated identifier with MySQL backticks.
     */
    String quote(String identifier);

    /**
     * Validate a plain SQL identifier.
     */
    boolean isIdentifier(String value);
}