package com.javabytes.mcp.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.config.AppConfig;
import com.javabytes.mcp.utils.JsonUtils;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Locale;
import java.util.regex.Pattern;

public class MyDatabaseClient {

    private static final int MAX_ROWS = 1000;
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    /** Only read-only statements are accepted by {@link #query(String)}. */
    private static final Pattern READ_ONLY = Pattern.compile(
            "(?is)^(SELECT|SHOW|DESCRIBE|DESC|EXPLAIN|WITH|TABLE)\\b");
    private static final Pattern LEADING_COMMENTS = Pattern.compile(
            "^(?:\\s+|--[^\\n]*\\n?|#[^\\n]*\\n?|/\\*.*?\\*/)*", Pattern.DOTALL);

    private final DataSource dataSource;
    private final ObjectMapper MAPPER = JsonUtils.MAPPER;

    public MyDatabaseClient(String jdbcUrl, String username, String password) {
        com.mysql.cj.jdbc.MysqlDataSource ds = new com.mysql.cj.jdbc.MysqlDataSource();
        ds.setUrl(jdbcUrl);
        ds.setUser(username);
        ds.setPassword(password);
        this.dataSource = ds;
    }

    public MyDatabaseClient() {
        this(AppConfig.DB_URL, AppConfig.DB_USERNAME, AppConfig.DB_PASSWORD);
    }

    /**
     * Execute a read-only query. Returns
     * {@code {success, columns, rows, row_count, truncated}} on success and
     * {@code {success:false, error, message}} on failure.
     */
    public JsonNode query(String sql) {
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

        try (Connection conn = dataSource.getConnection()) {
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

    public JsonNode update(String sql) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            int rowsAffected = stmt.executeUpdate(sql);
            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("rows_affected", rowsAffected);
            result.put("sql", sql);
            return result;
        } catch (SQLException e) {
            return error("database_error", e.getMessage());
        }
    }

    public JsonNode ping() {
        try (Connection conn = dataSource.getConnection()) {
            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("message", "Database connection OK");
            return result;
        } catch (SQLException e) {
            return error("database_error", e.getMessage());
        }
    }

    private ObjectNode error(String code, String message) {
        ObjectNode error = JsonUtils.MAPPER.createObjectNode();
        error.put("success", false);
        error.put("error", code);
        error.put("message", message);
        return error;
    }
}
