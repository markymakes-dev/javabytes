package com.javabytes.mcp.tools.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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
        ObjectNode p = M.createObjectNode();
        p.put("url", "jdbc:mysql://localhost:3306/mydb");
        p.put("ddl", "CREATE TABLE `t` (id INT PRIMARY KEY);");
        // No live DB: the refusal/execution path opens a connection, which
        // fails with a database_error rather than a param error.
        JsonNode result = TOOL.execute(p);
        assertFalse(result.get("error").asText().equals("MISSING_PARAM"));
    }

    @Test
    void rejectsMissingDdlAndPath() {
        ObjectNode p = M.createObjectNode();
        p.put("jdbc_url", "jdbc:mysql://localhost:3306/mydb");
        JsonNode result = TOOL.execute(p);
        assertFalse(result.get("success").asBoolean());
        assertEquals("MISSING_PARAM", result.get("error").asText());
    }

    @Test
    void rejectsEmptyDdl() {
        ObjectNode p = M.createObjectNode();
        p.put("jdbc_url", "jdbc:mysql://localhost:3306/mydb");
        p.put("ddl", "");
        JsonNode result = TOOL.execute(p);
        assertFalse(result.get("success").asBoolean());
        assertEquals("MISSING_PARAM", result.get("error").asText());
    }

    @Test
    void pathOutsideSandboxIsRejected(@TempDir Path tempDir) {
        ObjectNode p = M.createObjectNode();
        p.put("jdbc_url", "jdbc:mysql://localhost:3306/mydb");
        p.put("path", System.getProperty("os.name").startsWith("Windows")
                ? "C:\\Windows\\System32\\drivers\\etc\\hosts"
                : "/etc/passwd");
        JsonNode result = TOOL.execute(p);
        assertFalse(result.get("success").asBoolean());
        assertEquals("PATH_NOT_ALLOWED", result.get("error").asText());
    }

    @Test
    void pathNotFoundInsideSandbox(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("no-such-file.sql");
        ObjectNode p = M.createObjectNode();
        p.put("jdbc_url", "jdbc:mysql://localhost:3306/mydb");
        p.put("path", missing.toString());
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

        Db db = new Db();
        db.existingCount = 1; // `users` already exists
        GenerateDatabaseTool tool = new GenerateDatabaseTool(u -> fakeConnection(db));

        ObjectNode p = M.createObjectNode();
        p.put("jdbc_url", "jdbc:mysql://test/x");
        p.put("path", file.toString());
        p.put("confirm", false);

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

        Db db = new Db();
        db.existingCount = 1; // `users` already exists, but we are confirming
        GenerateDatabaseTool tool = new GenerateDatabaseTool(u -> fakeConnection(db));

        ObjectNode p = M.createObjectNode();
        p.put("jdbc_url", "jdbc:mysql://test/x");
        p.put("path", file.toString());
        p.put("confirm", true);

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

        Db db = new Db();
        GenerateDatabaseTool tool = new GenerateDatabaseTool(u -> fakeConnection(db));

        ObjectNode p = M.createObjectNode();
        p.put("jdbc_url", "jdbc:mysql://test/x");
        p.put("path", file.toString());
        p.put("confirm", true);

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

        Db db = new Db();
        db.failOnSecondUpdate = true;
        GenerateDatabaseTool tool = new GenerateDatabaseTool(u -> fakeConnection(db));

        ObjectNode p = M.createObjectNode();
        p.put("jdbc_url", "jdbc:mysql://test/x");
        p.put("path", file.toString());
        p.put("confirm", true);

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

        Db db = new Db();
        GenerateDatabaseTool tool = new GenerateDatabaseTool(u -> fakeConnection(db));

        ObjectNode p = M.createObjectNode();
        p.put("jdbc_url", "jdbc:mysql://test/x");
        p.put("ddl", ddl);
        p.put("confirm", true);

        JsonNode result = tool.execute(p);
        assertTrue(result.get("success").asBoolean(), result.toPrettyString());
        assertEquals(2, result.get("statements_executed").asInt());
    }

    // ------------------------------------------------------------------
    // Test doubles
    // ------------------------------------------------------------------

    /** Mutable state shared by the fake Connection / Statement / ResultSet. */
    private static class Db {
        int existingCount = 0;   // COUNT(*) returned by the existence check
        int updateCount = 0;
        boolean nextCalled = false;
        boolean failOnSecondUpdate = false;
    }

    /**
     * A fake {@link Connection} (and its Statement/ResultSet) built from JDK
     * dynamic proxies. No real database is involved; all calls dispatch on
     * method name and share the {@link Db} state.
     */
    private static Connection fakeConnection(Db db) {
        InvocationHandler resultSetHandler = (proxy, method, args) -> {
            String name = method.getName();
            Object[] a = args == null ? new Object[0] : args;
            switch (name) {
                case "next":
                    if (!db.nextCalled) {
                        db.nextCalled = true;
                        return true;
                    }
                    return false;
                case "getInt":
                    if ("n".equals(String.valueOf(a[0]))) {
                        return db.existingCount;
                    }
                    return 0;
                case "close":
                case "isClosed":
                    return false;
                case "isValid":
                    return true;
                default:
                    throw new UnsupportedOperationException("unsupported: " + name);
            }
        };

        InvocationHandler statementHandler = (proxy, method, args) -> {
            String name = method.getName();
            Object[] a = args == null ? new Object[0] : args;
            switch (name) {
                case "executeQuery": {
                    db.nextCalled = false; // fresh result set for each query
                    ResultSet rs = (ResultSet) Proxy.newProxyInstance(
                            GenerateDatabaseToolTest.class.getClassLoader(),
                            new Class<?>[]{ResultSet.class}, resultSetHandler);
                    return rs;
                }
                case "executeUpdate":
                    db.updateCount++;
                    if (db.failOnSecondUpdate && db.updateCount == 2) {
                        throw new SQLException("simulated update failure");
                    }
                    return 0;
                case "close":
                case "isClosed":
                    return false;
                case "isValid":
                    return true;
                default:
                    throw new UnsupportedOperationException("unsupported: " + name);
            }
        };

        InvocationHandler connectionHandler = (proxy, method, args) -> {
            String name = method.getName();
            switch (name) {
                case "createStatement": {
                    Statement stmt = (Statement) Proxy.newProxyInstance(
                            GenerateDatabaseToolTest.class.getClassLoader(),
                            new Class<?>[]{Statement.class}, statementHandler);
                    return stmt;
                }
                case "close":
                case "isClosed":
                    return false;
                case "isValid":
                    return true;
                default:
                    throw new UnsupportedOperationException("unsupported: " + name);
            }
        };

        return (Connection) Proxy.newProxyInstance(
                GenerateDatabaseToolTest.class.getClassLoader(),
                new Class<?>[]{Connection.class}, connectionHandler);
    }
}
