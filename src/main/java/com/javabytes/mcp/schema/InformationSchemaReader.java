package com.javabytes.mcp.schema;

import com.javabytes.mcp.config.AppConfig;
import com.mysql.cj.jdbc.MysqlDataSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the current MySQL schema into a {@link SchemaModel}.
 *
 * <p>Everything comes from {@code information_schema}: columns (with raw types
 * preserved verbatim), primary keys, indexes and foreign keys with their
 * ON DELETE / ON UPDATE rules. Tables are returned in
 * {@code TABLE_NAME, ORDINAL_POSITION} order, so column order is the physical
 * column order. The reader is read-only and performs no truncation.</p>
 *
 * <p>Connections are obtained through a {@link ConnectionSupplier}, which is
 * the seam that lets tests supply their own connections. The default
 * constructor wires the environment-driven {@link AppConfig} configuration
 * exactly like {@link com.javabytes.mcp.database.MyDatabaseClient}.</p>
 */
public final class InformationSchemaReader {

    /**
     * Source of JDBC connections; declared as its own interface so
     * implementations (and {@link java.sql.DataSource#getConnection()}-style
     * methods) may throw {@link SQLException}.
     */
    @FunctionalInterface
    public interface ConnectionSupplier {
        Connection get() throws SQLException;
    }

    private static final String DATABASE_SQL = "SELECT DATABASE() AS db";

    private static final String COLUMNS_SQL =
            "SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, "
                    + "COLUMN_KEY, CHARACTER_MAXIMUM_LENGTH "
                    + "FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = DATABASE() "
                    + "ORDER BY TABLE_NAME, ORDINAL_POSITION";

    private static final String INDEXES_SQL =
            "SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, COLUMN_NAME "
                    + "FROM information_schema.STATISTICS "
                    + "WHERE TABLE_SCHEMA = DATABASE() "
                    + "ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX";

    private static final String RELATIONS_SQL =
            "SELECT k.TABLE_NAME, k.COLUMN_NAME, k.REFERENCED_TABLE_NAME, "
                    + "k.REFERENCED_COLUMN_NAME, r.DELETE_RULE, r.UPDATE_RULE "
                    + "FROM information_schema.KEY_COLUMN_USAGE k "
                    + "JOIN information_schema.REFERENTIAL_CONSTRAINTS r "
                    + "  ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA "
                    + " AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME "
                    + "WHERE k.TABLE_SCHEMA = DATABASE() AND k.REFERENCED_TABLE_NAME IS NOT NULL "
                    + "ORDER BY k.TABLE_NAME, k.CONSTRAINT_NAME, k.ORDINAL_POSITION";

    private final ConnectionSupplier connections;

    /** Environment-driven construction, mirroring MyDatabaseClient's defaults. */
    public InformationSchemaReader() {
        this(() -> defaultDataSource().getConnection());
    }

    /** Construction with an explicit connection source (the test seam). */
    public InformationSchemaReader(ConnectionSupplier connections) {
        this.connections = connections;
    }

    private static MysqlDataSource defaultDataSource() {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUrl(AppConfig.DB_URL);
        dataSource.setUser(AppConfig.DB_USERNAME);
        dataSource.setPassword(AppConfig.DB_PASSWORD);
        return dataSource;
    }

    /**
     * Read the current database's tables, columns, indexes and foreign keys.
     *
     * @throws SQLException when the connection or any catalog query fails
     */
    public SchemaModel read() throws SQLException {
        try (Connection connection = connections.get()) {
            return new SchemaModel(
                    readDatabaseName(connection),
                    readTables(connection),
                    readRelations(connection));
        }
    }

    private String readDatabaseName(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(DATABASE_SQL)) {
            if (!rows.next()) {
                return null;
            }
            String database = rows.getString("db");
            return database == null || database.isBlank() ? null : database;
        }
    }

    private List<Table> readTables(Connection connection) throws SQLException {
        Map<String, TableBuilder> tables = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(COLUMNS_SQL)) {
            while (rows.next()) {
                tables.computeIfAbsent(rows.getString("TABLE_NAME"), TableBuilder::new)
                        .addColumn(
                                rows.getString("COLUMN_NAME"),
                                rows.getString("COLUMN_TYPE"),
                                "YES".equalsIgnoreCase(rows.getString("IS_NULLABLE")),
                                rows.getString("COLUMN_DEFAULT"),
                                rows.getString("COLUMN_KEY"),
                                rows.getString("CHARACTER_MAXIMUM_LENGTH"));
            }
        }
        readIndexes(connection, tables);
        List<Table> result = new ArrayList<>(tables.size());
        for (TableBuilder table : tables.values()) {
            result.add(table.build());
        }
        return result;
    }

    private void readIndexes(Connection connection, Map<String, TableBuilder> tables)
            throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(INDEXES_SQL)) {
            while (rows.next()) {
                // Every indexed table has columns, so the builder always exists;
                // skip defensively rather than inventing a table for an orphan index.
                TableBuilder table = tables.get(rows.getString("TABLE_NAME"));
                if (table != null) {
                    table.addIndex(
                            rows.getString("INDEX_NAME"),
                            rows.getString("COLUMN_NAME"),
                            rows.getString("NON_UNIQUE"));
                }
            }
        }
    }

    private List<Relation> readRelations(Connection connection) throws SQLException {
        List<Relation> relations = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(RELATIONS_SQL)) {
            while (rows.next()) {
                relations.add(new Relation(
                        rows.getString("TABLE_NAME"),
                        rows.getString("COLUMN_NAME"),
                        rows.getString("REFERENCED_TABLE_NAME"),
                        rows.getString("REFERENCED_COLUMN_NAME"),
                        rows.getString("DELETE_RULE"),
                        rows.getString("UPDATE_RULE")));
            }
        }
        return relations;
    }

    /** Accumulates one table's columns, primary key and indexes from ordered rows. */
    private static final class TableBuilder {

        private final String name;
        private final List<Column> columns = new ArrayList<>();
        private final List<String> primaryKeys = new ArrayList<>();
        private final Map<String, List<String>> indexColumns = new LinkedHashMap<>();
        private final Map<String, Boolean> indexUnique = new LinkedHashMap<>();

        TableBuilder(String name) {
            this.name = name;
        }

        void addColumn(String columnName, String rawType, boolean nullable, String defaultValue,
                       String columnKey, String characterMaximumLength) {
            if ("PRI".equalsIgnoreCase(columnKey)) {
                primaryKeys.add(columnName);
            }
            columns.add(new Column(
                    columnName,
                    LogicalType.fromRaw(rawType),
                    rawType,
                    nullable,
                    defaultValue,
                    "UNI".equalsIgnoreCase(columnKey),
                    characterMaximumLength == null ? null : Long.valueOf(characterMaximumLength)));
        }

        void addIndex(String indexName, String columnName, String nonUnique) {
            indexColumns.computeIfAbsent(indexName, key -> new ArrayList<>()).add(columnName);
            indexUnique.put(indexName, !"1".equals(nonUnique));
        }

        Table build() {
            List<Index> indexes = new ArrayList<>(indexColumns.size());
            for (Map.Entry<String, List<String>> index : indexColumns.entrySet()) {
                indexes.add(new Index(index.getKey(), index.getValue(),
                        indexUnique.getOrDefault(index.getKey(), false)));
            }
            return new Table(name, columns, primaryKeys, indexes);
        }
    }
}
