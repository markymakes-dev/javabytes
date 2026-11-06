package com.javabytes.mcp.skills.diagnostics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.config.AppConfig;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.PathValidator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Generates a self-contained MySQL JDBC helper for a user project.
 *
 * <p>The generated class has no dependency on JavaBytes or Jackson, reads
 * credentials from environment variables, and never embeds a password in
 * source code.</p>
 */
public class CreateMyDatabaseClientSkill implements Skill {

    private static final Pattern PACKAGE = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");

    @Override
    public String getName() { return "create_mysql_client"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String projectPath = params.has("projectPath") ? params.get("projectPath").asText() : ".";
        String packageName = params.has("packageName") ? params.get("packageName").asText() : "com.example.database";
        String jdbcUrl = params.has("jdbcUrl") ? params.get("jdbcUrl").asText() : "jdbc:mysql://localhost:3306/appdb";
        String username = params.has("username") ? params.get("username").asText() : "app";

        try {
            if (!PACKAGE.matcher(packageName).matches()) {
                return ErrorHelper.error("INVALID_PACKAGE", "Invalid Java package name: " + packageName);
            }
            Path projectDir = Path.of(projectPath);
            if (!PathValidator.isAllowed(projectDir)) {
                return ErrorHelper.error("PATH_NOT_ALLOWED",
                        "Path not allowed: " + projectPath + ". Allowed roots: "
                                + AppConfig.describeAllowedDirectories());
            }

            Path targetDir = projectDir.resolve("src/main/java").resolve(packageName.replace('.', '/'));
            Files.createDirectories(targetDir);
            Path clientFile = targetDir.resolve("MyDatabaseClient.java");

            if (Files.exists(clientFile)) {
                ObjectNode existing = JsonUtils.MAPPER.createObjectNode();
                existing.put("success", true);
                existing.put("alreadyExists", true);
                existing.put("filePath", clientFile.toAbsolutePath().toString());
                existing.put("message", "MyDatabaseClient.java already exists; not overwritten");
                return existing;
            }

            String code = generateMyDatabaseClient(packageName, jdbcUrl, username);
            Files.writeString(clientFile, code);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("alreadyExists", false);
            result.put("filePath", clientFile.toAbsolutePath().toString());
            result.put("packageName", packageName);
            result.put("jdbcUrl", jdbcUrl);
            result.put("message", "MyDatabaseClient.java created. Credentials are read from DB_URL, DB_USER and DB_PASSWORD.");
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("CREATE_CLIENT_ERROR", "Error creating MyDatabaseClient: " + e.getMessage());
        }
    }

    private String generateMyDatabaseClient(String packageName, String jdbcUrl, String username) {
        String url = javaString(jdbcUrl);
        String user = javaString(username);
        return """
                package %s;

                import com.mysql.cj.jdbc.MysqlDataSource;

                import javax.sql.DataSource;
                import java.sql.Connection;
                import java.sql.ResultSet;
                import java.sql.ResultSetMetaData;
                import java.sql.SQLException;
                import java.sql.Statement;
                import java.util.ArrayList;
                import java.util.LinkedHashMap;
                import java.util.List;
                import java.util.Map;

                /**
                 * Minimal read-only MySQL client.
                 *
                 * <p>Connection settings come from the DB_URL, DB_USER and DB_PASSWORD
                 * environment variables. Never commit credentials to source control.</p>
                 */
                public class MyDatabaseClient {

                    private static final int MAX_ROWS = 1_000;
                    private static final int QUERY_TIMEOUT_SECONDS = 30;

                    private final DataSource dataSource;

                    public MyDatabaseClient(String jdbcUrl, String username, String password) {
                        MysqlDataSource dataSource = new MysqlDataSource();
                        dataSource.setURL(jdbcUrl);
                        dataSource.setUser(username);
                        dataSource.setPassword(password);
                        this.dataSource = dataSource;
                    }

                    /** Build a client from environment variables. */
                    public static MyDatabaseClient fromEnvironment() {
                        String url = System.getenv().getOrDefault("DB_URL", %s);
                        String user = System.getenv().getOrDefault("DB_USER", %s);
                        String password = System.getenv().getOrDefault("DB_PASSWORD", "");
                        return new MyDatabaseClient(url, user, password);
                    }

                    /** Run a read-only query and return rows as ordered maps. */
                    public List<Map<String, Object>> query(String sql) throws SQLException {
                        try (Connection connection = dataSource.getConnection()) {
                            connection.setReadOnly(true);
                            try (Statement statement = connection.createStatement()) {
                                statement.setMaxRows(MAX_ROWS);
                                statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                                try (ResultSet resultSet = statement.executeQuery(sql)) {
                                    ResultSetMetaData meta = resultSet.getMetaData();
                                    int columnCount = meta.getColumnCount();
                                    List<Map<String, Object>> rows = new ArrayList<>();
                                    while (resultSet.next()) {
                                        Map<String, Object> row = new LinkedHashMap<>();
                                        for (int i = 1; i <= columnCount; i++) {
                                            row.put(meta.getColumnLabel(i), resultSet.getObject(i));
                                        }
                                        rows.add(row);
                                    }
                                    return rows;
                                }
                            }
                        }
                    }

                    /** Execute an INSERT/UPDATE/DELETE and return the affected row count. */
                    public int update(String sql) throws SQLException {
                        try (Connection connection = dataSource.getConnection();
                             Statement statement = connection.createStatement()) {
                            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                            return statement.executeUpdate(sql);
                        }
                    }

                    /** Check that the database is reachable. */
                    public boolean ping() {
                        try (Connection connection = dataSource.getConnection()) {
                            return connection.isValid(5);
                        } catch (SQLException e) {
                            return false;
                        }
                    }
                }
                """.formatted(packageName, url, user);
    }

    private String javaString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.prop("projectPath", new Skill.StringSchema().description("Path to the project directory"));
        schema.prop("packageName", new Skill.StringSchema().description("Package for the generated class"));
        schema.prop("jdbcUrl", new Skill.StringSchema().description("Default MySQL JDBC URL (overridable via DB_URL)"));
        schema.prop("username", new Skill.StringSchema().description("Default MySQL user (overridable via DB_USER)"));
        return schema;
    }
}
