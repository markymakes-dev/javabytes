package com.javabytes.mcp.skills.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.utils.JsonUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live MySQL checks for the schema-analysis tools.
 *
 * <p>Disabled unless {@code JAVABYTES_RUN_DB_TESTS=1} and the standard
 * {@code JAVABYTES_DB_*} variables are set. The test creates the
 * {@code javabytes_it} schema and never touches another database.</p>
 */
class DatabaseSkillsIntegrationTest {

    private static final String DATABASE = "javabytes_it";
    private static final String URL = System.getenv("JAVABYTES_DB_URL");
    private static final String USER = System.getenv("JAVABYTES_DB_USER");
    private static final String PASSWORD = System.getenv("JAVABYTES_DB_PASSWORD");

    @BeforeAll
    static void setUp() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("JAVABYTES_RUN_DB_TESTS")),
                "Set JAVABYTES_RUN_DB_TESTS=1 to run live MySQL tests");
        Assumptions.assumeTrue(URL != null && USER != null && PASSWORD != null,
                "JAVABYTES_DB_URL/USER/PASSWORD are required");

        try (Connection connection = DriverManager.getConnection(serverUrl(URL), USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS " + DATABASE
                    + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }

        try (Connection connection = DriverManager.getConnection(testUrl(), USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS order_items");
            statement.execute("DROP TABLE IF EXISTS orders");
            statement.execute("DROP TABLE IF EXISTS articles");
            statement.execute("DROP TABLE IF EXISTS customers");
            statement.execute("DROP TABLE IF EXISTS products");

            statement.execute("""
                    CREATE TABLE products (
                      id INT PRIMARY KEY,
                      name VARCHAR(80) NOT NULL
                    ) ENGINE=InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE customers (
                      id INT PRIMARY KEY,
                      email VARCHAR(120) NOT NULL UNIQUE,
                      sales_rep_id INT,
                      sales_rep_region VARCHAR(60)
                    ) ENGINE=InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE orders (
                      id INT PRIMARY KEY,
                      customer_id INT NOT NULL,
                      order_date DATE NOT NULL,
                      KEY idx_orders_customer (customer_id),
                      CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id)
                        REFERENCES customers (id)
                    ) ENGINE=InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE order_items (
                      order_id INT NOT NULL,
                      product_id INT NOT NULL,
                      product_name VARCHAR(80),
                      quantity INT,
                      PRIMARY KEY (order_id, product_id),
                      KEY idx_items_product (product_id),
                      CONSTRAINT fk_items_order FOREIGN KEY (order_id)
                        REFERENCES orders (id) ON DELETE CASCADE,
                      CONSTRAINT fk_items_product FOREIGN KEY (product_id)
                        REFERENCES products (id)
                    ) ENGINE=InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE articles (
                      id INT PRIMARY KEY,
                      title VARCHAR(120),
                      tags VARCHAR(255)
                    ) ENGINE=InnoDB
                    """);

            statement.execute("INSERT INTO products VALUES (1, 'Keyboard'), (2, 'Mouse')");
            statement.execute("INSERT INTO customers VALUES "
                    + "(1, 'a@example.com', 10, 'North'), (2, 'b@example.com', 10, 'North')");
            statement.execute("INSERT INTO orders VALUES "
                    + "(1, 1, '2026-01-01'), (2, 2, '2026-01-02')");
            statement.execute("INSERT INTO order_items VALUES "
                    + "(1, 1, 'Keyboard', 2), (1, 2, 'Mouse', 1)");
            statement.execute("INSERT INTO articles VALUES "
                    + "(1, 'Intro', 'java, mcp, sql'), (2, 'Clean', 'java')");
        }
    }

    @AfterAll
    static void tearDown() {
        // The schema is intentionally left in place for manual inspection; the
        // next run recreates every table from scratch.
    }

    @Test
    void listsCompositeIndexesInColumnOrder() {
        ObjectNode params = params();
        params.put("table_name", "order_items");

        JsonNode result = new ListIndexesSkill().execute(params);

        assertTrue(result.path("success").asBoolean(), result.toString());
        JsonNode primary = findIndex(result.path("indexes"), "PRIMARY");
        assertNotNull(primary, result.toString());
        assertEquals("order_id", primary.path("columns").get(0).asText());
        assertEquals("product_id", primary.path("columns").get(1).asText());
        assertTrue(primary.path("unique").asBoolean());
        assertNotNull(findIndex(result.path("indexes"), "idx_items_product"));
    }

    @Test
    void listsForeignKeysWithDeleteRules() {
        ObjectNode params = params();
        params.put("table_name", "order_items");

        JsonNode result = new ListForeignKeysSkill().execute(params);

        assertTrue(result.path("success").asBoolean(), result.toString());
        assertEquals(2, result.path("count").asInt(), result.toString());
        JsonNode orderKey = findForeignKey(result.path("foreign_keys"), "fk_items_order");
        assertNotNull(orderKey, result.toString());
        assertEquals("orders", orderKey.path("referenced_table").asText());
        assertEquals("CASCADE", orderKey.path("on_delete").asText());
        assertEquals("order_id", orderKey.path("columns").get(0).asText());
    }

    @Test
    void explainsSelectAndRejectsWrites() {
        ObjectNode params = params();
        params.put("sql", "SELECT o.id, c.email FROM orders o JOIN customers c "
                + "ON c.id = o.customer_id WHERE o.id = 1");

        JsonNode result = new ExplainQuerySkill().execute(params);

        assertTrue(result.path("success").asBoolean(), result.toString());
        assertTrue(result.path("row_count").asInt() >= 1, result.toString());
        assertFalse(result.path("analyze").asBoolean());

        ObjectNode write = params();
        write.put("sql", "DELETE FROM orders");
        JsonNode rejected = new ExplainQuerySkill().execute(write);
        assertFalse(rejected.path("success").asBoolean(true));
        assertEquals("read_only_violation", rejected.path("error").asText());
    }

    @Test
    void exportsSchemaDdlWithoutDataSourceCredentials() {
        JsonNode result = new ExportSchemaDdlSkill().execute(params());

        assertTrue(result.path("success").asBoolean(), result.toString());
        assertEquals(5, result.path("table_count").asInt(), result.toString());
        String ddl = result.path("ddl").asText();
        assertTrue(ddl.contains("CREATE TABLE `order_items`"), ddl);
        assertTrue(ddl.contains("PRIMARY KEY"), ddl);
        assertTrue(ddl.contains("`order_id`") && ddl.contains("`product_id`"), ddl);
        assertFalse(ddl.toLowerCase().contains("password"));
    }

    @Test
    void analyzesDeclaredFunctionalDependenciesAgainstLiveCatalog() {
        ObjectNode params = params();
        params.put("sample_rows", 10);
        params.put("include_samples", true);
        params.put("functional_dependencies", """
                order_items: product_id -> product_name
                customers: sales_rep_id -> sales_rep_region
                """);

        JsonNode result = new AnalyzeSchemaNormalizationSkill().execute(params);

        assertTrue(result.path("success").asBoolean(), result.toString());
        assertEquals(5, result.path("tables_analyzed").asInt(), result.toString());

        JsonNode dependencyAnalysis = result.path("functional_dependency_analysis");
        assertTrue(dependencyAnalysis.path("provided").asBoolean());
        assertEquals(2, dependencyAnalysis.path("declared_count").asInt());
        assertEquals(2, dependencyAnalysis.path("closures").size());
        assertEquals(2, dependencyAnalysis.path("violations").size(),
                dependencyAnalysis.toString());

        assertTrue(hasFinding(result.path("findings"), "2NF", "order_items"),
                result.toString());
        assertTrue(hasFinding(result.path("findings"), "3NF", "customers"),
                result.toString());
        assertTrue(hasFinding(result.path("findings"), "1NF", "articles"),
                result.toString());

        JsonNode forms = result.path("normal_forms");
        assertEquals("review_required", forms.path("2NF").path("status").asText());
        assertEquals("review_required", forms.path("3NF").path("status").asText());
        assertTrue(forms.has("BCNF"));
        assertTrue(result.path("limitations").toString().contains("alternate candidate keys"));
    }

    @Test
    void rejectsMalformedDependenciesAndUnsafeIdentifiers() {
        ObjectNode malformed = params();
        malformed.put("functional_dependencies", "order_items product_id -> product_name");
        JsonNode dependencyError = new AnalyzeSchemaNormalizationSkill().execute(malformed);
        assertFalse(dependencyError.path("success").asBoolean(true));
        assertEquals("INVALID_FUNCTIONAL_DEPENDENCIES", dependencyError.path("error").asText());

        ObjectNode unsafe = params();
        unsafe.put("table_name", "order_items; DROP TABLE products");
        JsonNode identifierError = new ListIndexesSkill().execute(unsafe);
        assertFalse(identifierError.path("success").asBoolean(true));
        assertEquals("INVALID_IDENTIFIER", identifierError.path("error").asText());
    }

    private static boolean hasFinding(JsonNode findings, String form, String table) {
        for (JsonNode finding : findings) {
            if (form.equals(finding.path("normal_form").asText())
                    && table.equals(finding.path("table").asText())) {
                return true;
            }
        }
        return false;
    }

    private static JsonNode findIndex(JsonNode indexes, String name) {
        for (JsonNode index : indexes) {
            if (name.equals(index.path("index").asText())) {
                return index;
            }
        }
        return null;
    }

    private static JsonNode findForeignKey(JsonNode keys, String constraint) {
        for (JsonNode key : keys) {
            if (constraint.equals(key.path("constraint").asText())) {
                return key;
            }
        }
        return null;
    }

    private static ObjectNode params() {
        return JsonUtils.MAPPER.createObjectNode();
    }

    private static String testUrl() {
        int scheme = URL.indexOf("//");
        int pathStart = URL.indexOf('/', scheme + 2);
        int query = URL.indexOf('?');
        String parameters = query >= 0 ? URL.substring(query)
                : "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        return serverUrl(URL) + "/" + DATABASE + parameters;
    }

    private static String serverUrl(String url) {
        int scheme = url.indexOf("//");
        int pathStart = url.indexOf('/', scheme + 2);
        return pathStart > 0 ? url.substring(0, pathStart) : url;
    }
}
