package com.javabytes.mcp.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.schema.Column;
import com.javabytes.mcp.schema.Index;
import com.javabytes.mcp.schema.LogicalType;
import com.javabytes.mcp.schema.Relation;
import com.javabytes.mcp.schema.SchemaModel;
import com.javabytes.mcp.schema.Table;
import com.javabytes.mcp.utils.JsonUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fake in-memory {@link DatabaseService} for unit tests.
 *
 * <p>Supports a minimal schema definition so that catalog loaders and the
 * read-only guard can be tested without a live MySQL.</p>
 */
public final class FakeDatabaseService implements DatabaseService {

    private final Map<String, FakeTable> tables = new LinkedHashMap<>();
    private final String defaultDatabase;

    public FakeDatabaseService() {
        this("testdb");
    }

    public FakeDatabaseService(String defaultDatabase) {
        this.defaultDatabase = defaultDatabase;
    }

    /** Add a table definition for testing. */
    public FakeDatabaseService withTable(FakeTable table) {
        tables.put(table.name().toLowerCase(Locale.ROOT), table);
        return this;
    }

    @Override
    public JsonNode query(String sql, String jdbcUrl) {
        String statement = sql == null ? "" : sql.trim();
        if (statement.isEmpty()) {
            return error("empty_query", "SQL query is empty");
        }
        String upper = statement.toUpperCase(Locale.ROOT);
        if (!upper.matches("(?s)^(SELECT|SHOW|DESCRIBE|DESC|EXPLAIN|WITH|TABLE)\\b.*")) {
            return error("read_only_violation",
                    "Only SELECT, SHOW, DESCRIBE, EXPLAIN, WITH and TABLE statements are allowed");
        }
        if (statement.replaceAll(";\\s*$", "").indexOf(';') >= 0) {
            return error("multiple_statements", "Multiple SQL statements are not allowed");
        }

        try {
            if (upper.startsWith("SELECT DATABASE()")) {
                return dbResult("db", defaultDatabase);
            }
            if (upper.startsWith("SHOW TABLES")) {
                ArrayNode rows = JsonUtils.MAPPER.createArrayNode();
                for (FakeTable t : tables.values()) {
                    ObjectNode row = JsonUtils.MAPPER.createObjectNode();
                    row.put("name", t.name());
                    rows.add(row);
                }
                return successResult(rows);
            }
            if (upper.startsWith("DESCRIBE ")) {
                String tableName = statement.substring(9).trim().replace("`", "");
                FakeTable table = tables.get(tableName.toLowerCase(Locale.ROOT));
                if (table == null) {
                    return error("database_error", "Table not found: " + tableName);
                }
                ArrayNode rows = JsonUtils.MAPPER.createArrayNode();
                for (FakeColumn c : table.columns()) {
                    ObjectNode row = JsonUtils.MAPPER.createObjectNode();
                    row.put("Field", c.name());
                    row.put("Type", c.type());
                    row.put("Null", c.nullable() ? "YES" : "NO");
                    row.put("Key", c.primaryKey() ? "PRI" : "");
                    row.put("Default", c.defaultValue() == null ? "" : c.defaultValue());
                    row.put("Extra", "");
                    rows.add(row);
                }
                return successResult(rows);
            }
            if (upper.contains("INFORMATION_SCHEMA.COLUMNS")) {
                return handleColumnsQuery();
            }
            if (upper.contains("INFORMATION_SCHEMA.STATISTICS")) {
                return handleStatisticsQuery();
            }
            if (upper.contains("INFORMATION_SCHEMA.KEY_COLUMN_USAGE")
                    && upper.contains("REFERENTIAL_CONSTRAINTS")) {
                return handleRelationsQuery();
            }
            if (upper.contains("INFORMATION_SCHEMA.TABLES")) {
                return handleTablesQuery();
            }
            if (upper.startsWith("SHOW CREATE TABLE ")) {
                String tableName = statement.substring(18).trim().replace("`", "");
                FakeTable table = tables.get(tableName.toLowerCase(Locale.ROOT));
                if (table == null) {
                    return error("database_error", "Table not found: " + tableName);
                }
                return successResult(buildShowCreateTable(table));
            }
        } catch (Exception e) {
            return error("database_error", e.getMessage());
        }
        return error("database_error", "Unsupported query: " + sql);
    }

    @Override
    public JsonNode executeWrite(String sql, String jdbcUrl) {
        return error("unsupported", "executeWrite not implemented in fake");
    }

    @Override
    public Connection getConnection(String jdbcUrl) throws SQLException {
        throw new UnsupportedOperationException("getConnection not implemented in fake");
    }

    @Override
    public String currentDatabase(String jdbcUrl) {
        return defaultDatabase;
    }

    @Override
    public List<String> tableNames(String jdbcUrl) {
        return new ArrayList<>(tables.keySet());
    }

    @Override
    public SchemaModel readSchemaModel(String jdbcUrl) {
        List<Table> result = new ArrayList<>();
        for (FakeTable ft : tables.values()) {
            List<Column> cols = new ArrayList<>();
            for (FakeColumn fc : ft.columns()) {
                cols.add(new Column(
                        fc.name(),
                        LogicalType.fromRaw(fc.type()),
                        fc.type(),
                        fc.nullable(),
                        fc.defaultValue(),
                        fc.primaryKey() || fc.unique(),
                        fc.maxLength()));
            }
            List<Index> indexes = new ArrayList<>();
            if (ft.primaryKeyColumns().size() > 1) {
                indexes.add(new Index("PRIMARY", ft.primaryKeyColumns(), true));
            } else if (!ft.primaryKeyColumns().isEmpty()) {
                indexes.add(new Index("PRIMARY", ft.primaryKeyColumns(), true));
            }
            for (String uk : ft.uniqueColumns()) {
                indexes.add(new Index(uk, List.of(uk), true));
            }
            result.add(new Table(ft.name(), cols, ft.primaryKeyColumns(), indexes));
        }
        List<Relation> relations = new ArrayList<>();
        for (FakeTable ft : tables.values()) {
            for (FakeForeignKey fk : ft.foreignKeys()) {
                relations.add(new Relation(
                        ft.name(), fk.column(), fk.referencedTable(), fk.referencedColumn(),
                        fk.onDelete(), fk.onUpdate()));
            }
        }
        return new SchemaModel(defaultDatabase, result, relations);
    }

    @Override
    public Table readTable(String jdbcUrl, String tableName) {
        SchemaModel model = readSchemaModel(jdbcUrl);
        for (Table t : model.tables()) {
            if (t.name().equals(tableName)) {
                return t;
            }
        }
        return null;
    }

    @Override
    public List<Relation> readRelations(String jdbcUrl, String tableName) {
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
    }

    @Override
    public List<Index> readIndexes(String jdbcUrl, String tableName) {
        SchemaModel model = readSchemaModel(jdbcUrl);
        for (Table t : model.tables()) {
            if (t.name().equals(tableName)) {
                return t.indexes();
            }
        }
        return List.of();
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
        return value != null && value.matches("[A-Za-z_][A-Za-z0-9_$]*");
    }

    // --- query handlers ------------------------------------------------------

    private JsonNode handleColumnsQuery() {
        ArrayNode rows = JsonUtils.MAPPER.createArrayNode();
        for (FakeTable table : tables.values()) {
            for (FakeColumn col : table.columns()) {
                ObjectNode row = JsonUtils.MAPPER.createObjectNode();
                row.put("TABLE_NAME", table.name());
                row.put("COLUMN_NAME", col.name());
                row.put("COLUMN_TYPE", col.type());
                row.put("IS_NULLABLE", col.nullable() ? "YES" : "NO");
                row.put("COLUMN_DEFAULT", col.defaultValue() == null ? "" : col.defaultValue());
                row.put("COLUMN_KEY", col.primaryKey() ? "PRI" : (col.unique() ? "UNI" : ""));
                if (col.maxLength() != null) {
                    row.put("CHARACTER_MAXIMUM_LENGTH", col.maxLength());
                } else {
                    row.putNull("CHARACTER_MAXIMUM_LENGTH");
                }
                rows.add(row);
            }
        }
        return successResult(rows);
    }

    private JsonNode handleStatisticsQuery() {
        ArrayNode rows = JsonUtils.MAPPER.createArrayNode();
        for (FakeTable table : tables.values()) {
            if (!table.primaryKeyColumns().isEmpty()) {
                for (int i = 0; i < table.primaryKeyColumns().size(); i++) {
                    ObjectNode row = JsonUtils.MAPPER.createObjectNode();
                    row.put("TABLE_NAME", table.name());
                    row.put("INDEX_NAME", "PRIMARY");
                    row.put("NON_UNIQUE", "0");
                    row.put("COLUMN_NAME", table.primaryKeyColumns().get(i));
                    row.put("SEQ_IN_INDEX", i + 1);
                    row.put("INDEX_TYPE", "BTREE");
                    rows.add(row);
                }
            }
            for (String uk : table.uniqueColumns()) {
                ObjectNode row = JsonUtils.MAPPER.createObjectNode();
                row.put("TABLE_NAME", table.name());
                row.put("INDEX_NAME", uk);
                row.put("NON_UNIQUE", "0");
                row.put("COLUMN_NAME", uk);
                row.put("SEQ_IN_INDEX", 1);
                row.put("INDEX_TYPE", "BTREE");
                rows.add(row);
            }
        }
        return successResult(rows);
    }

    private JsonNode handleRelationsQuery() {
        ArrayNode rows = JsonUtils.MAPPER.createArrayNode();
        for (FakeTable table : tables.values()) {
            for (FakeForeignKey fk : table.foreignKeys()) {
                ObjectNode row = JsonUtils.MAPPER.createObjectNode();
                row.put("CONSTRAINT_NAME", "fk_" + table.name() + "_" + fk.column());
                row.put("TABLE_NAME", table.name());
                row.put("COLUMN_NAME", fk.column());
                row.put("REFERENCED_TABLE_NAME", fk.referencedTable());
                row.put("REFERENCED_COLUMN_NAME", fk.referencedColumn());
                row.put("DELETE_RULE", fk.onDelete());
                row.put("UPDATE_RULE", fk.onUpdate());
                rows.add(row);
            }
        }
        return successResult(rows);
    }

    private JsonNode handleTablesQuery() {
        ArrayNode rows = JsonUtils.MAPPER.createArrayNode();
        for (FakeTable table : tables.values()) {
            ObjectNode row = JsonUtils.MAPPER.createObjectNode();
            row.put("name", table.name());
            rows.add(row);
        }
        return successResult(rows);
    }

    private ArrayNode buildShowCreateTable(FakeTable table) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE `").append(table.name()).append("` (");
        List<String> colDefs = new ArrayList<>();
        for (FakeColumn col : table.columns()) {
            StringBuilder cd = new StringBuilder();
            cd.append("`").append(col.name()).append("` ").append(col.type());
            if (!col.nullable()) cd.append(" NOT NULL");
            if (col.defaultValue() != null) cd.append(" DEFAULT '").append(col.defaultValue()).append("'");
            if (col.primaryKey()) cd.append(" PRIMARY KEY");
            colDefs.add(cd.toString());
        }
        if (!table.primaryKeyColumns().isEmpty()) {
            colDefs.add("PRIMARY KEY (`" + String.join("`, `", table.primaryKeyColumns()) + "`)");
        }
        sb.append(String.join(", ", colDefs));
        sb.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        ArrayNode rows = JsonUtils.MAPPER.createArrayNode();
        ObjectNode row = JsonUtils.MAPPER.createObjectNode();
        row.put("Table", table.name());
        row.put("Create Table", sb.toString());
        rows.add(row);
        return rows;
    }

    private JsonNode successResult(ArrayNode rows) {
        ObjectNode result = JsonUtils.MAPPER.createObjectNode();
        result.put("success", true);
        result.set("columns", JsonUtils.MAPPER.createArrayNode());
        result.set("rows", rows);
        result.put("row_count", rows.size());
        result.put("truncated", false);
        return result;
    }

    private JsonNode dbResult(String col, String value) {
        ArrayNode rows = JsonUtils.MAPPER.createArrayNode();
        ObjectNode row = JsonUtils.MAPPER.createObjectNode();
        row.put(col, value);
        rows.add(row);
        return successResult(rows);
    }

    private ObjectNode error(String code, String message) {
        ObjectNode error = JsonUtils.MAPPER.createObjectNode();
        error.put("success", false);
        error.put("error", code);
        error.put("message", message);
        return error;
    }

    // --- Fake schema model ---------------------------------------------------

    public static final class FakeTable {
        private final String name;
        private final List<FakeColumn> columns = new ArrayList<>();
        private final List<String> primaryKeys = new ArrayList<>();
        private final Set<String> uniqueColumns = new LinkedHashSet<>();
        private final List<FakeForeignKey> foreignKeys = new ArrayList<>();

        public FakeTable(String name) {
            this.name = name;
        }

        public String name() { return name; }
        public List<FakeColumn> columns() { return List.copyOf(columns); }
        public List<String> primaryKeyColumns() { return List.copyOf(primaryKeys); }
        public Set<String> uniqueColumns() { return Set.copyOf(uniqueColumns); }
        public List<FakeForeignKey> foreignKeys() { return List.copyOf(foreignKeys); }

        public FakeTable addColumn(FakeColumn column) {
            columns.add(column);
            if (column.primaryKey()) primaryKeys.add(column.name());
            if (column.unique()) uniqueColumns.add(column.name());
            return this;
        }

        public FakeTable addForeignKey(FakeForeignKey fk) {
            foreignKeys.add(fk);
            return this;
        }
    }

    public static final class FakeColumn {
        private final String name;
        private final String type;
        private final boolean nullable;
        private final String defaultValue;
        private final boolean primaryKey;
        private final boolean unique;
        private final Long maxLength;

        public FakeColumn(String name, String type, boolean nullable, String defaultValue,
                          boolean primaryKey, boolean unique, Long maxLength) {
            this.name = name; this.type = type; this.nullable = nullable; this.defaultValue = defaultValue;
            this.primaryKey = primaryKey; this.unique = unique; this.maxLength = maxLength;
        }

        public String name() { return name; }
        public String type() { return type; }
        public boolean nullable() { return nullable; }
        public String defaultValue() { return defaultValue; }
        public boolean primaryKey() { return primaryKey; }
        public boolean unique() { return unique; }
        public Long maxLength() { return maxLength; }

        public static FakeColumn pk(String name, String type) {
            return new FakeColumn(name, type, false, null, true, false, null);
        }
        public static FakeColumn col(String name, String type) {
            return new FakeColumn(name, type, true, null, false, false, null);
        }
        public static FakeColumn col(String name, String type, Long maxLength) {
            return new FakeColumn(name, type, true, null, false, false, maxLength);
        }
        public static FakeColumn col(String name, String type, String defaultValue) {
            return new FakeColumn(name, type, true, defaultValue, false, false, null);
        }
        public static FakeColumn uniq(String name, String type) {
            return new FakeColumn(name, type, true, null, false, true, null);
        }
    }

    public static final class FakeForeignKey {
        private final String column;
        private final String referencedTable;
        private final String referencedColumn;
        private final String onDelete;
        private final String onUpdate;

        public FakeForeignKey(String column, String referencedTable, String referencedColumn,
                              String onDelete, String onUpdate) {
            this.column = column; this.referencedTable = referencedTable;
            this.referencedColumn = referencedColumn; this.onDelete = onDelete; this.onUpdate = onUpdate;
        }

        public String column() { return column; }
        public String referencedTable() { return referencedTable; }
        public String referencedColumn() { return referencedColumn; }
        public String onDelete() { return onDelete; }
        public String onUpdate() { return onUpdate; }
    }
}