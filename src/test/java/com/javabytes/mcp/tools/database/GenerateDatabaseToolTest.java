package com.javabytes.mcp.tools.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.database.DatabaseService;
import com.javabytes.mcp.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerateDatabaseToolTest {

    private static final GenerateDatabaseTool TOOL = new GenerateDatabaseTool();
    private static final ObjectMapper M = com.javabytes.mcp.utils.JsonUtils.MAPPER;

    // ------------------------------------------------------------------
    // Parameter validation (no database connection is opened)
    // ------------------------------------------------------------------

    @Test
    void rejectsMissingJdbcUrl() {
        JsonNode result = TOOL.execute(M.createObjectNode());
        assertFalse(result.get("success").asBoolean());
        assertEquals("MISSING_PARAM", result.get("error").asText());
        assertTrue(result.get("message").asText().contains("jdbc_url"));
    }

    @Test
    void urlIsAnAliasForJdbcUrl() {
        ObjectNode p = M.createObjectNode().put("url", "jdbc:mysql://localhost:3306/mydb").put("ddl", "CREATE TABLE `t` (id INT PRIMARY KEY);");
        JsonNode result = TOOL.execute(p);
        assertFalse(result.get("error").asText().equals("MISSING_PARAM"));
    }

    @Test
    void rejectsMissingDdlAndPath() {
        ObjectNode p = M.createObjectNode().put("jdbc_url", "jdbc:mysql://localhost:3306/mydb");
        JsonNode result = TOOL.execute(p);
        assertFalse(result.get("success").asBoolean());
        assertEquals("MISSING_PARAM", result.get("error").asText());
    }

    @Test
    void rejectsEmptyDdl() {
        ObjectNode p = M.createObjectNode().put("jdbc_url", "jdbc:mysql://localhost:3306/mydb").put("ddl", "");
        JsonNode result = TOOL.execute(p);
        assertFalse(result.get("success").asBoolean());
        assertEquals("MISSING_PARAM", result.get("error").asText());
    }

    @Test
    void pathOutsideSandboxIsRejected(@TempDir Path tempDir) {
        ObjectNode p = M.createObjectNode().put("jdbc_url", "jdbc:mysql://localhost:3306/mydb")
                .put("path", System.getProperty("os.name").startsWith("Windows")
                        ? "C:\\Windows\\System32\\drivers\\etc\\hosts"
                        : "/etc/passwd");
        JsonNode result = TOOL.execute(p);
        assertFalse(result.get("success").asBoolean());
        assertEquals("PATH_NOT_ALLOWED", result.get("error").asText());
    }

    @Test
    void pathNotFoundInsideSandbox(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("no-such-file.sql");
        ObjectNode p = M.createObjectNode().put("jdbc_url", "jdbc:mysql://localhost:3306/mydb").put("path", missing.toString());
        JsonNode result = TOOL.execute(p);
        assertFalse(result.get("success").asBoolean());
        assertEquals("PATH_NOT_FOUND", result.get("error").asText());
    }

    // ------------------------------------------------------------------
    // ADR 0005: existing-table refusal and confirmed creation
    // (connection injected so no live database is required)
    // ------------------------------------------------------------------

    @Test
    void refusesWhenTargetTableExistsWithoutConfirm(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("schema.sql");
        Files.writeString(file, "CREATE TABLE `users` (id INT PRIMARY KEY, name VARCHAR(100));");

        FakeDb db = new FakeDb();
        db.existingCount = 1; // `users` already exists
        DatabaseService fakeDb = new TestDatabaseService(db);
        GenerateDatabaseTool tool = new GenerateDatabaseTool(fakeDb);

        ObjectNode p = M.createObjectNode().put("jdbc_url", "jdbc:mysql://test/x").put("path", file.toString()).put("confirm", false);

        JsonNode result = tool.execute(p);
        assertFalse(result.get("success").asBoolean(), result.toPrettyString());
        assertEquals("TABLE_ALREADY_EXISTS", result.get("error").asText());
        assertTrue(result.get("refused").asBoolean());
        assertNotNull(result.get("existing_tables"));
        assertEquals(1, result.get("existing_tables").size());
        assertEquals("users", result.get("existing_tables").get(0).asText());
        assertTrue(result.get("message").asText().contains("confirm=true"));
    }

    @Test
    void proceedsWhenConfirmedEvenIfTableExists(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("schema.sql");
        Files.writeString(file, "CREATE TABLE `users` (id INT PRIMARY KEY, name VARCHAR(100));");

        FakeDb db = new FakeDb();
        db.existingCount = 1; // `users` already exists, but we are confirming
        DatabaseService fakeDb = new TestDatabaseService(db);
        GenerateDatabaseTool tool = new GenerateDatabaseTool(fakeDb);

        ObjectNode p = M.createObjectNode().put("jdbc_url", "jdbc:mysql://test/x").put("path", file.toString()).put("confirm", true);

        JsonNode result = tool.execute(p);
        assertTrue(result.get("success").asBoolean(), result.toPrettyString());
        assertEquals(1, result.get("statements_executed").asInt());
        assertEquals(1, result.get("tables_created").asInt());
        assertEquals("users", result.get("created_tables").get(0).asText());
    }

    @Test
    void createsTablesWhenNoneExist(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("schema.sql");
        Files.writeString(file, "CREATE TABLE `users` (id INT PRIMARY KEY, name VARCHAR(100) NOT NULL);\n"
                + "CREATE TABLE `order_items` (id INT PRIMARY KEY, order_id INT);");

        FakeDb db = new FakeDb();
        DatabaseService fakeDb = new TestDatabaseService(db);
        GenerateDatabaseTool tool = new GenerateDatabaseTool(fakeDb);

        ObjectNode p = M.createObjectNode().put("jdbc_url", "jdbc:mysql://test/x").put("path", file.toString()).put("confirm", true);

        JsonNode result = tool.execute(p);
        assertTrue(result.get("success").asBoolean(), result.toPrettyString());
        assertEquals(2, result.get("statements_executed").asInt());
        assertEquals(2, result.get("tables_created").asInt());
        assertNotNull(result.get("created_tables"));
        boolean hasUsers = false, hasOrderItems = false;
        for (JsonNode t : (ArrayNode) result.get("created_tables")) {
            if ("users".equals(t.asText())) hasUsers = true;
            if ("order_items".equals(t.asText())) hasOrderItems = true;
        }
        assertTrue(hasUsers);
        assertTrue(hasOrderItems);
    }

    @Test
    void failedStatementIsRecordedButExecutionContinues(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("schema.sql");
        Files.writeString(file, "CREATE TABLE `a` (id INT PRIMARY KEY);\n"
                + "CREATE TABLE `b` (id INT PRIMARY KEY);");

        FakeDb db = new FakeDb();
        db.failOnSecondUpdate = true;
        DatabaseService fakeDb = new TestDatabaseService(db);
        GenerateDatabaseTool tool = new GenerateDatabaseTool(fakeDb);

        ObjectNode p = M.createObjectNode().put("jdbc_url", "jdbc:mysql://test/x").put("path", file.toString()).put("confirm", true);

        JsonNode result = tool.execute(p);
        assertFalse(result.get("success").asBoolean(), result.toPrettyString());
        assertEquals("DDL_EXECUTION_ERROR", result.get("error").asText());
        ArrayNode results = (ArrayNode) result.get("results");
        assertEquals(2, results.size());
        assertEquals("executed", results.get(0).get("status").asText());
        assertEquals("failed", results.get(1).get("status").asText());
        assertEquals("database_error", results.get(1).get("error").asText());
    }

    @Test
    void semicolonsInsideStringLiteralsAreNotStatementSeparators() throws Exception {
        String ddl = "CREATE TABLE `t` (id INT PRIMARY KEY, note VARCHAR(50) DEFAULT 'a;b;c');\n"
                + "CREATE TABLE `u` (id INT PRIMARY KEY);";

        FakeDb db = new FakeDb();
        DatabaseService fakeDb = new TestDatabaseService(db);
        GenerateDatabaseTool tool = new GenerateDatabaseTool(fakeDb);

        ObjectNode p = M.createObjectNode().put("jdbc_url", "jdbc:mysql://test/x").put("ddl", ddl).put("confirm", true);

        JsonNode result = tool.execute(p);
        assertTrue(result.get("success").asBoolean(), result.toPrettyString());
        assertEquals(2, result.get("statements_executed").asInt());
    }

    // ------------------------------------------------------------------
    // Test doubles
    // ------------------------------------------------------------------

    /** Mutable state shared by the fake DatabaseService. */
    private static class FakeDb {
        int existingCount = 0;   // COUNT(*) returned by the existence check
        int updateCount = 0;
        boolean failOnSecondUpdate = false;
        boolean nextCalled = false;
    }

    /**
     * A fake {@link DatabaseService} that uses dynamic proxies for the
     * connection/statement/resultset seam — exactly like the original test.
     * No real database is involved; all calls dispatch on method name and share the {@link FakeDb} state.
     */
    private static class TestDatabaseService implements DatabaseService {
        private final FakeDb db;

        TestDatabaseService(FakeDb db) {
            this.db = db;
        }

        @Override
        public JsonNode query(String sql, String jdbcUrl) {
            String upper = sql.toUpperCase(java.util.Locale.ROOT);
            // Existence check: SELECT COUNT(*) FROM information_schema.TABLES WHERE ...
            if (upper.contains("INFORMATION_SCHEMA.TABLES") && upper.contains("COUNT(*)")) {
                ArrayNode rows = JsonUtils.MAPPER.createArrayNode();
                ObjectNode row = JsonUtils.MAPPER.createObjectNode();
                row.put("n", db.existingCount);
                rows.add(row);
                ObjectNode result = JsonUtils.MAPPER.createObjectNode();
                result.put("success", true);
                result.set("columns", JsonUtils.MAPPER.createArrayNode());
                result.set("rows", rows);
                result.put("row_count", 1);
                result.put("truncated", false);
                return result;
            }
            // Default: empty result
            return successResult();
        }

        @Override
        public JsonNode executeWrite(String sql, String jdbcUrl) {
            db.updateCount++;
            if (db.failOnSecondUpdate && db.updateCount >= 2) {
                ObjectNode result = JsonUtils.MAPPER.createObjectNode();
                result.put("success", false);
                result.put("statement", truncate(sql));
                result.put("status", "failed");
                result.put("error", "database_error");
                result.put("message", "Simulated failure on second statement");
                return result;
            }
            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("statement", truncate(sql));
            result.put("rows_affected", 0);
            result.put("status", "executed");
            return result;
        }

        @Override
        public Connection getConnection(String jdbcUrl) throws SQLException {
            // Dynamic proxy for Connection -> Statement -> ResultSet (existence check)
            FakeDb state = db;
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("createStatement".equals(name)) {
                            return Proxy.newProxyInstance(
                                    Statement.class.getClassLoader(),
                                    new Class<?>[]{Statement.class},
                                    (p2, m2, a2) -> {
                                        String n2 = m2.getName();
                                        if ("executeQuery".equals(n2)) {
                                            String sql = (String) (a2 == null ? new Object[0] : a2)[0];
                                            String upper2 = sql.toUpperCase(java.util.Locale.ROOT);
                                            if (upper2.contains("INFORMATION_SCHEMA.TABLES") && upper2.contains("COUNT(*)")) {
                                                return Proxy.newProxyInstance(
                                                        ResultSet.class.getClassLoader(),
                                                        new Class<?>[]{ResultSet.class},
                                                        (p3, m3, a3) -> {
                                                            String n3 = m3.getName();
                                                            if ("next".equals(n3)) {
                                                                if (!state.nextCalled) {
                                                                    state.nextCalled = true;
                                                                    return true;
                                                                }
                                                                return false;
                                                            }
                                                            if ("getInt".equals(n3) && "n".equals(String.valueOf(((Object[]) (a3 == null ? new Object[0] : a3))[0]))) {
                                                                return state.existingCount;
                                                            }
                                                            if ("close".equals(n3) || "isClosed".equals(n3)) {
                                                                return false;
                                                            }
                                                            throw new UnsupportedOperationException("ResultSet." + n3);
                                                        });
                                            }
                                            throw new UnsupportedOperationException("Statement.executeQuery: " + sql);
                                        }
                                        if ("close".equals(n2) || "isClosed".equals(n2)) {
                                            return null;
                                        }
                                        throw new UnsupportedOperationException("Statement." + n2);
                                    });
                        }
                        if ("close".equals(name) || "isClosed".equals(name)) {
                            return null;
                        }
                        throw new UnsupportedOperationException("Connection." + name);
                    });
        }

        @Override
        public String quote(String identifier) {
            return "`" + identifier + "`";
        }

        @Override
        public boolean isIdentifier(String value) {
            return value != null && value.matches("[A-Za-z_][A-Za-z0-9_$]*");
        }

        private JsonNode successResult() {
            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.set("columns", JsonUtils.MAPPER.createArrayNode());
            result.set("rows", JsonUtils.MAPPER.createArrayNode());
            result.put("row_count", 0);
            result.put("truncated", false);
            return result;
        }

        private static String truncate(String sql) {
            int max = 200;
            if (sql.length() <= max) return sql;
            return sql.substring(0, max - 3) + "...";
        }
    }
}