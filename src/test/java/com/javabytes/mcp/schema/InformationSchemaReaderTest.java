package com.javabytes.mcp.schema;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link InformationSchemaReader} against a hand-rolled fake
 * {@link Connection} — the connection-supplier seam exists exactly so these
 * tests need no database. The fakes are reflection proxies that implement only
 * the JDBC surface the reader uses; any other call fails the test loudly.
 */
class InformationSchemaReaderTest {

    @Test
    void readsColumnsWithLogicalAndRawTypes() throws SQLException {
        SchemaModel model = reader(routes(
                "SELECT DATABASE() AS db", List.of(row("db", "javabytes_it")),
                "information_schema.COLUMNS", List.of(
                        row("TABLE_NAME", "users", "COLUMN_NAME", "id", "COLUMN_TYPE", "int",
                                "IS_NULLABLE", "NO", "COLUMN_KEY", "PRI"),
                        row("TABLE_NAME", "users", "COLUMN_NAME", "email", "COLUMN_TYPE", "varchar(191)",
                                "IS_NULLABLE", "NO", "COLUMN_KEY", "UNI",
                                "CHARACTER_MAXIMUM_LENGTH", "191"),
                        row("TABLE_NAME", "users", "COLUMN_NAME", "nickname", "COLUMN_TYPE", "varchar(80)",
                                "IS_NULLABLE", "YES", "COLUMN_DEFAULT", "anon"),
                        row("TABLE_NAME", "users", "COLUMN_NAME", "bio", "COLUMN_TYPE", "text",
                                "IS_NULLABLE", "YES", "CHARACTER_MAXIMUM_LENGTH", "65535"),
                        row("TABLE_NAME", "users", "COLUMN_NAME", "avatar", "COLUMN_TYPE", "longblob",
                                "IS_NULLABLE", "NO", "CHARACTER_MAXIMUM_LENGTH", "4294967295"),
                        row("TABLE_NAME", "users", "COLUMN_NAME", "flags", "COLUMN_TYPE", "set('a','b')",
                                "IS_NULLABLE", "YES")))).read();

        assertEquals("javabytes_it", model.database());
        assertEquals(1, model.tables().size());
        Table users = model.tables().get(0);
        assertEquals("users", users.name());
        assertEquals(6, users.columns().size());
        assertEquals(List.of("id"), users.primaryKeys());

        Column id = users.columns().get(0);
        assertEquals("id", id.name());
        assertEquals(LogicalType.INT, id.logicalType());
        assertEquals("int", id.rawType());
        assertFalse(id.nullable());
        assertNull(id.defaultValue());
        assertFalse(id.unique());
        assertNull(id.maxLength());

        Column email = users.columns().get(1);
        assertEquals(LogicalType.STRING, email.logicalType());
        assertEquals("varchar(191)", email.rawType());
        assertTrue(email.unique());
        assertEquals(191L, email.maxLength());

        Column nickname = users.columns().get(2);
        assertTrue(nickname.nullable());
        assertEquals("anon", nickname.defaultValue());
        assertFalse(nickname.unique());

        Column bio = users.columns().get(3);
        assertEquals(LogicalType.TEXT, bio.logicalType());
        assertEquals("text", bio.rawType());
        assertEquals(65535L, bio.maxLength());

        Column avatar = users.columns().get(4);
        assertEquals(LogicalType.BLOB, avatar.logicalType());
        assertEquals("longblob", avatar.rawType());
        assertEquals(4294967295L, avatar.maxLength());

        Column flags = users.columns().get(5);
        assertEquals(LogicalType.UNKNOWN, flags.logicalType());
        assertEquals("set('a','b')", flags.rawType());
    }

    @Test
    void readsForeignKeysWithRules() throws SQLException {
        SchemaModel model = reader(routes(
                "SELECT DATABASE() AS db", List.of(row("db", "javabytes_it")),
                "information_schema.COLUMNS", List.of(
                        row("TABLE_NAME", "order_items", "COLUMN_NAME", "order_id",
                                "COLUMN_TYPE", "int", "IS_NULLABLE", "NO", "COLUMN_KEY", "PRI"),
                        row("TABLE_NAME", "products", "COLUMN_NAME", "id",
                                "COLUMN_TYPE", "int", "IS_NULLABLE", "NO", "COLUMN_KEY", "PRI")),
                "KEY_COLUMN_USAGE", List.of(
                        row("TABLE_NAME", "order_items", "COLUMN_NAME", "order_id",
                                "REFERENCED_TABLE_NAME", "orders", "REFERENCED_COLUMN_NAME", "id",
                                "DELETE_RULE", "CASCADE", "UPDATE_RULE", "RESTRICT"),
                        row("TABLE_NAME", "order_items", "COLUMN_NAME", "product_id",
                                "REFERENCED_TABLE_NAME", "products", "REFERENCED_COLUMN_NAME", "id",
                                "DELETE_RULE", "NO ACTION")))).read();

        assertEquals(2, model.relations().size());
        Relation order = model.relations().get(0);
        assertEquals("order_items", order.sourceTable());
        assertEquals("order_id", order.sourceColumn());
        assertEquals("orders", order.targetTable());
        assertEquals("id", order.targetColumn());
        assertEquals("CASCADE", order.onDelete());
        assertEquals("RESTRICT", order.onUpdate());

        Relation product = model.relations().get(1);
        assertEquals("products", product.targetTable());
        assertEquals("NO ACTION", product.onDelete());
        assertNull(product.onUpdate());
    }

    @Test
    void readsIndexesInColumnOrder() throws SQLException {
        SchemaModel model = reader(routes(
                "SELECT DATABASE() AS db", List.of(row("db", "javabytes_it")),
                "information_schema.COLUMNS", List.of(
                        row("TABLE_NAME", "order_items", "COLUMN_NAME", "order_id",
                                "COLUMN_TYPE", "int", "IS_NULLABLE", "NO", "COLUMN_KEY", "PRI"),
                        row("TABLE_NAME", "order_items", "COLUMN_NAME", "product_id",
                                "COLUMN_TYPE", "int", "IS_NULLABLE", "NO", "COLUMN_KEY", "PRI")),
                "STATISTICS", List.of(
                        row("TABLE_NAME", "order_items", "INDEX_NAME", "PRIMARY",
                                "NON_UNIQUE", "0", "COLUMN_NAME", "order_id"),
                        row("TABLE_NAME", "order_items", "INDEX_NAME", "PRIMARY",
                                "NON_UNIQUE", "0", "COLUMN_NAME", "product_id"),
                        row("TABLE_NAME", "order_items", "INDEX_NAME", "idx_items_product",
                                "NON_UNIQUE", "1", "COLUMN_NAME", "product_id")))).read();

        Table orderItems = model.tables().get(0);
        assertEquals(2, orderItems.indexes().size());
        Index primary = orderItems.indexes().get(0);
        assertEquals("PRIMARY", primary.name());
        assertEquals(List.of("order_id", "product_id"), primary.columns());
        assertTrue(primary.unique());
        Index product = orderItems.indexes().get(1);
        assertEquals("idx_items_product", product.name());
        assertEquals(List.of("product_id"), product.columns());
        assertFalse(product.unique());
    }

    @Test
    void returnsNullDatabaseWhenNoSchemaSelected() throws SQLException {
        SchemaModel model = reader(routes(
                "SELECT DATABASE() AS db", List.of(row("db", (String) null)),
                "information_schema.COLUMNS", List.of())).read();

        assertNull(model.database());
        assertTrue(model.tables().isEmpty());
        assertTrue(model.relations().isEmpty());
    }

    @Test
    void closesTheConnectionAfterReading() throws SQLException {
        boolean[] closed = {false};
        InformationSchemaReader reader = new InformationSchemaReader(
                () -> fakeConnection(withDefaults(routes(
                        "SELECT DATABASE() AS db", List.of(row("db", "javabytes_it")),
                        "information_schema.COLUMNS", List.of())), closed));

        reader.read();

        assertTrue(closed[0]);
    }

    @Test
    void propagatesConnectionFailures() {
        InformationSchemaReader reader = new InformationSchemaReader(() -> {
            throw new SQLException("connection refused");
        });

        SQLException thrown = assertThrows(SQLException.class, reader::read);
        assertEquals("connection refused", thrown.getMessage());
    }

    // --- fakes -------------------------------------------------------------

    /**
     * A reader over a fake connection with sensible default routes for the
     * four catalog queries; test-supplied routes override them, and any SQL
     * matching none of the known fragments still fails the test.
     */
    private static InformationSchemaReader reader(Map<String, List<Map<String, String>>> routes) {
        return new InformationSchemaReader(() -> fakeConnection(withDefaults(routes), new boolean[1]));
    }

    private static Map<String, List<Map<String, String>>> withDefaults(
            Map<String, List<Map<String, String>>> routes) {
        Map<String, List<Map<String, String>>> merged = new LinkedHashMap<>();
        merged.put("SELECT DATABASE() AS db", List.of(row("db", "javabytes_it")));
        merged.put("information_schema.COLUMNS", List.of());
        merged.put("STATISTICS", List.of());
        merged.put("KEY_COLUMN_USAGE", List.of());
        merged.putAll(routes);
        return merged;
    }

    /** Rows per query, routed by a SQL fragment unique to that query. */
    private static Map<String, List<Map<String, String>>> routes(Object... fragmentRowsPairs) {
        Map<String, List<Map<String, String>>> routes = new LinkedHashMap<>();
        for (int i = 0; i < fragmentRowsPairs.length; i += 2) {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> rows = (List<Map<String, String>>) fragmentRowsPairs[i + 1];
            routes.put((String) fragmentRowsPairs[i], rows);
        }
        return routes;
    }

    private static Map<String, String> row(String... labelValuePairs) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < labelValuePairs.length; i += 2) {
            row.put(labelValuePairs[i], labelValuePairs[i + 1]);
        }
        return row;
    }

    private static Connection fakeConnection(Map<String, List<Map<String, String>>> routes,
                                             boolean[] closed) {
        return proxy(Connection.class, (proxy, method, args) -> {
            if ("createStatement".equals(method.getName())) {
                return fakeStatement(routes);
            }
            if ("close".equals(method.getName())) {
                closed[0] = true;
                return null;
            }
            if ("isClosed".equals(method.getName())) {
                return closed[0];
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private static Statement fakeStatement(Map<String, List<Map<String, String>>> routes) {
        return proxy(Statement.class, (proxy, method, args) -> {
            if ("executeQuery".equals(method.getName())) {
                return fakeResultSet(findRows(routes, (String) args[0]));
            }
            if ("close".equals(method.getName())) {
                return null;
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private static List<Map<String, String>> findRows(Map<String, List<Map<String, String>>> routes,
                                                      String sql) throws SQLException {
        for (Map.Entry<String, List<Map<String, String>>> route : routes.entrySet()) {
            if (sql.contains(route.getKey())) {
                return route.getValue();
            }
        }
        throw new SQLException("Unexpected query: " + sql);
    }

    private static ResultSet fakeResultSet(List<Map<String, String>> rows) {
        int[] cursor = {-1};
        return proxy(ResultSet.class, (proxy, method, args) -> {
            if ("next".equals(method.getName())) {
                cursor[0]++;
                return cursor[0] < rows.size();
            }
            if ("getString".equals(method.getName())) {
                return rows.get(cursor[0]).get((String) args[0]);
            }
            if ("close".equals(method.getName())) {
                return null;
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
