package com.javabytes.mcp.tools.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.config.AppConfig;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.PathValidator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates a schema from proposed normalized DDL against a user-supplied JDBC
 * URL.
 *
 * <p>This tool executes (creates only — it never migrates) one or more DDL
 * statements. It is deliberately destructive: tables are created but never
 * altered or dropped. Before executing it checks whether any target table
 * already exists in the target database; without an explicit {@code confirm}
 * flag it refuses rather than risk silently colliding with an existing
 * schema (see {@code docs/adr/0005-generate-database-creates-does-not-migrate.md}).</p>
 *
 * <p>Pure Java — no new dependencies. Reuses {@link MyDatabaseClient} for the
 * write path and {@link PathValidator} for sandbox-validated file reads when
 * DDL comes from a {@code path} param.</p>
 */
public class GenerateDatabaseTool implements Tool {

    private static final String NAME = "generate_database";

    /** Error returned when a target table already exists and confirm=false. */
    private static final String TABLE_EXISTS_ERROR_CODE = "TABLE_ALREADY_EXISTS";

    /** Source of JDBC connections; injectable so tests can run without a live DB. */
    @FunctionalInterface
    public interface ConnectionFactory {
        Connection create(String url) throws SQLException;
    }

    private final ConnectionFactory connectionFactory;

    public GenerateDatabaseTool() {
        this(url -> DriverManager.getConnection(url, null, null));
    }

    /** Test-only construction with an explicit connection source. */
    GenerateDatabaseTool(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public JsonNode execute(JsonNode params) {
        String jdbcUrl = param(params, "jdbc_url", "url");
        String ddl = param(params, "ddl", null);
        String pathParam = param(params, "path", null);
        boolean confirm = params.has("confirm") && !params.get("confirm").isNull()
                && params.get("confirm").asBoolean();

        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return ErrorHelper.error("MISSING_PARAM",
                    "Provide 'jdbc_url' (or 'url'): the JDBC URL of the target database");
        }

        try {
            // Resolve DDL: inline 'ddl' wins over 'path'.
            if (ddl == null || ddl.isBlank()) {
                if (pathParam == null || pathParam.isBlank()) {
                    return ErrorHelper.error("MISSING_PARAM",
                            "Provide either 'ddl' (proposed DDL) or 'path' "
                                    + "(file inside an allowed root containing DDL)");
                }
                Path path = Path.of(pathParam);
                if (!PathValidator.isAllowed(path)) {
                    return ErrorHelper.error("PATH_NOT_ALLOWED",
                            "Path not allowed: " + pathParam + ". Allowed roots: "
                                    + AppConfig.describeAllowedDirectories());
                }
                if (!Files.exists(path) || !Files.isRegularFile(path)) {
                    return ErrorHelper.error("PATH_NOT_FOUND", "Path not found: " + pathParam);
                }
                ddl = Files.readString(path);
            }

            if (ddl == null || ddl.isBlank()) {
                return ErrorHelper.error("INVALID_INPUT", "DDL is empty");
            }

            List<String> statements = splitStatements(ddl);

            // ADR 0005: refuse if any target table already exists unless confirmed.
            List<String> targetTables = targetTables(statements);
            if (!targetTables.isEmpty() && !confirm) {
                List<String> existing = existingTables(jdbcUrl, targetTables);
                if (!existing.isEmpty()) {
                    return buildRefusal(existing);
                }
            }

            // Execute each statement, reporting per-statement results.
            ArrayNode results = JsonUtils.MAPPER.createArrayNode();
            List<String> created = new ArrayList<>();
            boolean allOk = true;
            for (String sql : statements) {
                String trimmed = sql.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                ObjectNode statementResult = executeStatement(jdbcUrl, trimmed);
                results.add(statementResult);
                if (!statementResult.get("success").asBoolean(false)) {
                    allOk = false;
                } else {
                    String createdTable = createdTable(trimmed);
                    if (createdTable != null) {
                        created.add(createdTable);
                    }
                }
            }

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", allOk);
            result.put("tool", NAME);
            result.put("jdbc_url", jdbcUrl);
            result.put("statements_executed", results.size());
            result.set("results", results);
            result.put("tables_created", created.size());
            ArrayNode createdNode = JsonUtils.MAPPER.createArrayNode();
            for (String t : created) createdNode.add(t);
            result.set("created_tables", createdNode);
            if (!allOk) {
                result.put("error", "DDL_EXECUTION_ERROR");
                result.put("message", "One or more DDL statements failed; see results");
            }
            return result;
        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_DATABASE_ERROR",
                    "Error executing DDL: " + e.getMessage());
        }
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("jdbc_url", new Tool.StringSchema()
                .description("JDBC URL of the target database (required)"));
        schema.prop("url", new Tool.StringSchema()
                .description("Alias for jdbc_url"));
        schema.prop("ddl", new Tool.StringSchema()
                .description("Proposed DDL to execute (one or more statements), alternative to path"));
        schema.prop("path", new Tool.StringSchema()
                .description("File inside an allowed root containing DDL (alternative to ddl)"));
        schema.prop("confirm", new Tool.BoolSchema()
                .description("Explicit consent to create tables when a target table already exists (default false)"));
        schema.required("jdbc_url");
        schema.required("ddl");
        return schema;
    }

    // --- param resolution --------------------------------------------------

    private static String param(JsonNode params, String primary, String alias) {
        if (params.has(primary) && !params.get(primary).isNull()) {
            return params.get(primary).asText().trim();
        }
        if (alias != null && params.has(alias) && !params.get(alias).isNull()) {
            return params.get(alias).asText().trim();
        }
        return null;
    }

    // --- statement splitting -----------------------------------------------

    /** Split a DDL script into individual statements on top-level semicolons. */
    private static List<String> splitStatements(String ddl) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        char stringQuote = 0;
        for (int i = 0; i < ddl.length(); i++) {
            char c = ddl.charAt(i);
            if (inString) {
                current.append(c);
                if (c == stringQuote && (i == 0 || ddl.charAt(i - 1) != '\\')) {
                    inString = false;
                }
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                inString = true;
                stringQuote = c;
                current.append(c);
                continue;
            }
            if (c == ';') {
                statements.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (!current.toString().trim().isEmpty()) {
            statements.add(current.toString());
        }
        return statements;
    }

    // --- target-table detection --------------------------------------------

    private static final java.util.regex.Pattern CREATE_TABLE =
            java.util.regex.Pattern.compile("(?is)^\\s*CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?"
                    + "([A-Za-z_][A-Za-z0-9_$]*)`?", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** Table names that a statement would create (for the collision check). */
    private List<String> targetTables(List<String> statements) {
        List<String> targets = new ArrayList<>();
        for (String sql : statements) {
            var matcher = CREATE_TABLE.matcher(sql);
            if (matcher.find()) {
                targets.add(matcher.group(1));
            }
        }
        return targets;
    }

    /** Which of the target tables already exist in the current database. */
    private List<String> existingTables(String jdbcUrl, List<String> targets) throws SQLException {
        List<String> existing = new ArrayList<>();
        try (Connection conn = connectionFactory.create(jdbcUrl);
             Statement stmt = conn.createStatement()) {
            for (String table : targets) {
                String check = "SELECT COUNT(*) AS n FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + escapeSql(table) + "'";
                try (java.sql.ResultSet rs = stmt.executeQuery(check)) {
                    if (rs.next() && rs.getInt("n") > 0) {
                        existing.add(table);
                    }
                }
            }
        }
        return existing;
    }

    /** Escape single quotes for a SQL string literal. */
    private static String escapeSql(String value) {
        return value.replace("'", "''");
    }

    // --- statement execution -----------------------------------------------

    private ObjectNode executeStatement(String jdbcUrl, String sql) throws Exception {
        try (Connection conn = connectionFactory.create(jdbcUrl);
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

    /** Extract the table name a CREATE TABLE statement creates, or null. */
    private static String createdTable(String sql) {
        var matcher = CREATE_TABLE.matcher(sql);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Truncate long SQL for display in results. */
    private static String truncate(String sql) {
        int max = 200;
        if (sql.length() <= max) {
            return sql;
        }
        return sql.substring(0, max - 3) + "...";
    }

    /** Build the refusal result when a target table already exists. */
    private ObjectNode buildRefusal(List<String> existing) {
        ObjectNode result = ErrorHelper.error(TABLE_EXISTS_ERROR_CODE,
                "Refusing to create tables that already exist: " + String.join(", ", existing)
                        + ". Use confirm=true to proceed.");
        result.put("tool", NAME);
        result.put("refused", true);
        ArrayNode node = JsonUtils.MAPPER.createArrayNode();
        for (String t : existing) node.add(t);
        result.set("existing_tables", node);
        return result;
    }
}
