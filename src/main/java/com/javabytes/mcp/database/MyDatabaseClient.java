package com.javabytes.mcp.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.config.AppConfig;
import com.javabytes.mcp.schema.Index;
import com.javabytes.mcp.schema.InformationSchemaReader;
import com.javabytes.mcp.schema.Relation;
import com.javabytes.mcp.schema.SchemaModel;
import com.javabytes.mcp.schema.Table;
import com.javabytes.mcp.utils.JsonUtils;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Production adapter for {@link DatabaseService} using MySQL via MysqlDataSource.
 *
 * <p>Read-only queries go through the shared guard (statement allowlist, row cap,
 * timeout, identifier validation). Write operations are only available through
 * {@link #executeWrite(String, String)} and require the explicit capability
 * flag in the tool spec (see ADR 0007).</p>
 */
public class MyDatabaseClient implements DatabaseService {

    private static final int MAX_ROWS = 1000;
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    /** Only read-only statements are accepted by {@link #query(String, String)}. */
    private static final Pattern READ_ONLY = Pattern.compile(
            "(?is)^(SELECT|SHOW|DESCRIBE|DESC|EXPLAIN|WITH|TABLE)\\b");
    private static final Pattern LEADING_COMMENTS = Pattern.compile(
            "^(?:\\s+|--[^\\n]*\\n?|#[^\\n]*\\n?|/\\*.*?\\*/)*", Pattern.DOTALL);

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

    private final DataSource defaultDataSource;
    private final ObjectMapper MAPPER = JsonUtils.MAPPER;

    public MyDatabaseClient() {
        this(AppConfig.DB_URL, AppConfig.DB_USERNAME, AppConfig.DB_PASSWORD);
    }

    public MyDatabaseClient(String jdbcUrl, String username, String password) {
        com.mysql.cj.jdbc.MysqlDataSource ds = new com.mysql.cj.jdbc.MysqlDataSource();
        ds.setUrl(jdbcUrl);
        ds.setUser(username);
        ds.setPassword(password);
        this.defaultDataSource = ds;
    }

    // --- DatabaseService interface (only custom implementations) ------------

    @Override
    public JsonNode query(String sql, String jdbcUrl) {
        String statement = sql == null ? "" : LEADING_COMMENTS.matcher(sql).replaceFirst("").trim();
        if (statement.isEmpty()) {
            return error("empty_query", "SQL query is empty");
        }
        if (!READ_ONLY.matcher(statement).find()) {
            return error("read_only_violation",
                    "Only SELECT, SHOW, DESCRIBE, EXPLAIN, WITH and TABLE statements are allowed");
        }
        if (statement.replaceAll(";\\s*$", "").indexOf(';') >= 0) {
            return error("multiple_statements", "Multiple SQL statements are not allowed");
        }

        DataSource ds = resolveDataSource(jdbcUrl);

        try (Connection conn = ds.getConnection()) {
            conn.setReadOnly(true);
            try (Statement stmt = conn.createStatement()) {
                stmt.setMaxRows(MAX_ROWS + 1);
                stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                try (ResultSet rs = stmt.executeQuery(statement)) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();

                    ArrayNode columns = JsonUtils.MAPPER.createArrayNode();
                    for (int i = 1; i <= columnCount; i++) {
                        columns.add(meta.getColumnLabel(i));
                    }

                    ArrayNode rows = JsonUtils.MAPPER.createArrayNode();
                    int rowCount = 0;
                    boolean truncated = false;
                    while (rs.next()) {
                        if (rowCount == MAX_ROWS) {
                            truncated = true;
                            break;
                        }
                        ObjectNode row = JsonUtils.MAPPER.createObjectNode();
                        for (int i = 1; i <= columnCount; i++) {
                            Object value = rs.getObject(i);
                            row.put(meta.getColumnLabel(i), value == null ? null : String.valueOf(value));
                        }
                        rows.add(row);
                        rowCount++;
                    }

                    ObjectNode result = JsonUtils.MAPPER.createObjectNode();
                    result.put("success", true);
                    result.set("columns", columns);
                    result.set("rows", rows);
                    result.put("row_count", rowCount);
                    result.put("truncated", truncated);
                    return result;
                }
            }
        } catch (SQLException e) {
            return error("database_error", e.getMessage());
        }
    }

    @Override
    public JsonNode executeWrite(String sql, String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return error("MISSING_PARAM", "jdbc_url is required for write operations");
        }
        DataSource ds = resolveDataSource(jdbcUrl);
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            int rows = stmt.executeUpdate(sql);
            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("statement", truncate(sql));
            result.put("rows_affected", rows);
            result.put("status", "executed");
            return result;
        } catch (SQLException e) {
            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", false);
            result.put("statement", truncate(sql));
            result.put("status", "failed");
            result.put("error", "database_error");
            result.put("message", e.getMessage());
            return result;
        }
    }

    @Override
    public Connection getConnection(String jdbcUrl) throws SQLException {
        return resolveDataSource(jdbcUrl).getConnection();
    }

    @Override
    public String quote(String identifier) {
        if (!isIdentifier(identifier)) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);
        }
        return "`" + identifier + "`";
    }

    @Override
    public boolean isIdentifier(String value) {
        return value != null && IDENTIFIER.matcher(value).matches();
    }

    @Override
    public SchemaModel readSchemaModel(String jdbcUrl) {
        try {
            return new InformationSchemaReader(this).read(jdbcUrl);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read schema model", e);
        }
    }

    // --- internal helpers ----------------------------------------------------

    private DataSource resolveDataSource(String jdbcUrl) {
        if (jdbcUrl != null && !jdbcUrl.isBlank()) {
            com.mysql.cj.jdbc.MysqlDataSource ds = new com.mysql.cj.jdbc.MysqlDataSource();
            ds.setUrl(jdbcUrl);
            return ds;
        }
        return defaultDataSource;
    }

    private ObjectNode error(String code, String message) {
        ObjectNode error = JsonUtils.MAPPER.createObjectNode();
        error.put("success", false);
        error.put("error", code);
        error.put("message", message);
        return error;
    }

    private static String truncate(String sql) {
        int max = 200;
        if (sql.length() <= max) {
            return sql;
        }
        return sql.substring(0, max - 3) + "...";
    }
}