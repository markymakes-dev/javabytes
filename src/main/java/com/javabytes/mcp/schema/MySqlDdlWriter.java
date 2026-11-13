package com.javabytes.mcp.schema;

import com.javabytes.mcp.database.SchemaCatalog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Renders a {@link SchemaModel} as proposed MySQL DDL.
 *
 * <p>The output is normalized, not faithful: identifiers are backtick-quoted,
 * types derive from each column's logical type ({@link LogicalType#UNKNOWN}
 * columns pass their raw type through verbatim), and foreign keys are emitted
 * as ALTER TABLE statements so creation never depends on table order. Indexes
 * named PRIMARY are not rendered again because the primary key is rendered
 * from the model, and a column marked unique is not given an inline
 * {@code UNIQUE} when a single-column unique index already covers it.</p>
 *
 * <p>Every statement is prefixed with a "proposed — verify" comment: this DDL
 * is generated from a model that cannot carry all server-side details. The job
 * of reporting a live database's own DDL verbatim stays with the export tools
 * (see {@code docs/adr/0003-schema-export-is-faithful.md}).</p>
 *
 * <p>Identifiers outside the plain {@code [A-Za-z_][A-Za-z0-9_$]*} shape are
 * rejected rather than quoted loosely, reusing {@link SchemaCatalog}.</p>
 */
public final class MySqlDdlWriter {

    private static final String PROPOSED_MARKER = "-- proposed — verify before executing";

    private static final int DEFAULT_VARCHAR_LENGTH = 255;
    private static final int DEFAULT_UUID_LENGTH = 36;
    private static final int DEFAULT_DECIMAL_PRECISION = 10;
    private static final int DEFAULT_DECIMAL_SCALE = 2;
    private static final int MAX_CONSTRAINT_NAME_LENGTH = 64;
    /** Room reserved at the end of a constraint name for a "_2"-style suffix. */
    private static final int CONSTRAINT_SUFFIX_RESERVE = 4;

    private static final Pattern NUMERIC_LITERAL = Pattern.compile("-?\\d+(\\.\\d+)?");

    private MySqlDdlWriter() {
    }

    /**
     * Render the model as an executable MySQL script: one CREATE TABLE per
     * table followed by one ALTER TABLE per foreign key, statements separated
     * by blank lines. An empty model renders as an empty string.
     */
    public static String write(SchemaModel model) {
        StringBuilder ddl = new StringBuilder();
        Set<String> usedConstraintNames = new HashSet<>();
        for (Table table : model.tables()) {
            appendCreateTable(ddl, table);
        }
        for (Relation relation : model.relations()) {
            appendForeignKey(ddl, relation, usedConstraintNames);
        }
        return ddl.toString();
    }

    private static void appendCreateTable(StringBuilder ddl, Table table) {
        List<String> lines = new ArrayList<>();
        for (Column column : table.columns()) {
            lines.add("  " + columnDefinition(column, table));
        }
        if (!table.primaryKeys().isEmpty()) {
            lines.add("  PRIMARY KEY (" + quotedList(table.primaryKeys()) + ")");
        }
        for (Index index : table.indexes()) {
            if ("PRIMARY".equalsIgnoreCase(index.name())) {
                // Already rendered from the model's primary keys.
                continue;
            }
            lines.add("  " + (index.unique() ? "UNIQUE KEY " : "KEY ")
                    + SchemaCatalog.quote(index.name())
                    + " (" + quotedList(index.columns()) + ")");
        }
        ddl.append(PROPOSED_MARKER).append('\n')
                .append("CREATE TABLE ").append(SchemaCatalog.quote(table.name())).append(" (\n")
                .append(String.join(",\n", lines))
                .append("\n);\n\n");
    }

    private static String columnDefinition(Column column, Table table) {
        StringBuilder definition = new StringBuilder();
        definition.append(SchemaCatalog.quote(column.name()))
                .append(' ')
                .append(sqlType(column));
        if (!column.nullable()) {
            definition.append(" NOT NULL");
        }
        if (column.defaultValue() != null) {
            definition.append(" DEFAULT ").append(defaultValueLiteral(column.defaultValue()));
        }
        if (column.unique() && !coveredBySingleColumnUniqueIndex(column.name(), table)) {
            definition.append(" UNIQUE");
        }
        return definition.toString();
    }

    private static boolean coveredBySingleColumnUniqueIndex(String columnName, Table table) {
        return table.indexes().stream().anyMatch(index -> index.unique()
                && index.columns().size() == 1
                && index.columns().get(0).equals(columnName));
    }

    private static String sqlType(Column column) {
        return switch (column.logicalType()) {
            case STRING -> "VARCHAR(" + varcharLength(column) + ")";
            case TEXT -> "TEXT";
            case INT -> "INT";
            case BIGINT -> "BIGINT";
            // No precision is carried on the model, so a canonical default is proposed.
            case DECIMAL -> "DECIMAL(" + DEFAULT_DECIMAL_PRECISION + "," + DEFAULT_DECIMAL_SCALE + ")";
            case BOOLEAN -> "BOOLEAN";
            case DATE -> "DATE";
            case TIMESTAMP -> "TIMESTAMP";
            case JSON -> "JSON";
            case BLOB -> "BLOB";
            case UUID -> "CHAR(" + DEFAULT_UUID_LENGTH + ")";
            case UNKNOWN -> {
                if (column.rawType() == null || column.rawType().isBlank()) {
                    throw new IllegalArgumentException("Column '" + column.name()
                            + "' has logical type UNKNOWN but no raw type to pass through");
                }
                yield column.rawType().trim();
            }
        };
    }

    private static int varcharLength(Column column) {
        if (column.maxLength() != null && column.maxLength() > 0) {
            return column.maxLength().intValue();
        }
        return DEFAULT_VARCHAR_LENGTH;
    }

    private static String defaultValueLiteral(String value) {
        if (NUMERIC_LITERAL.matcher(value).matches()
                || "CURRENT_TIMESTAMP".equalsIgnoreCase(value)
                || "current_timestamp()".equalsIgnoreCase(value)) {
            return value;
        }
        return "'" + value.replace("'", "''") + "'";
    }

    private static void appendForeignKey(StringBuilder ddl, Relation relation,
                                         Set<String> usedConstraintNames) {
        ddl.append(PROPOSED_MARKER).append('\n')
                .append("ALTER TABLE ").append(SchemaCatalog.quote(relation.sourceTable()))
                .append("\n  ADD CONSTRAINT ")
                .append(SchemaCatalog.quote(constraintName(relation, usedConstraintNames)))
                .append(" FOREIGN KEY (").append(SchemaCatalog.quote(relation.sourceColumn()))
                .append(") REFERENCES ").append(SchemaCatalog.quote(relation.targetTable()))
                .append(" (").append(SchemaCatalog.quote(relation.targetColumn())).append(')');
        if (relation.onDelete() != null && !relation.onDelete().isBlank()) {
            ddl.append(" ON DELETE ").append(relation.onDelete().trim());
        }
        if (relation.onUpdate() != null && !relation.onUpdate().isBlank()) {
            ddl.append(" ON UPDATE ").append(relation.onUpdate().trim());
        }
        ddl.append(";\n\n");
    }

    private static String constraintName(Relation relation, Set<String> usedConstraintNames) {
        String base = "fk_" + relation.sourceTable() + "_" + relation.sourceColumn();
        int maxBaseLength = MAX_CONSTRAINT_NAME_LENGTH - CONSTRAINT_SUFFIX_RESERVE;
        if (base.length() > maxBaseLength) {
            base = base.substring(0, maxBaseLength);
        }
        String candidate = base;
        int suffix = 2;
        while (!usedConstraintNames.add(candidate)) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private static String quotedList(List<String> identifiers) {
        List<String> quoted = new ArrayList<>(identifiers.size());
        for (String identifier : identifiers) {
            quoted.add(SchemaCatalog.quote(identifier));
        }
        return String.join(", ", quoted);
    }
}
