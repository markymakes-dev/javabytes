package com.javabytes.mcp.schema;

import java.util.List;

/**
 * One table of a {@link SchemaModel}.
 *
 * @param name table name
 * @param columns columns in the order the source reported them (ordinal
 *        position)
 * @param primaryKeys primary-key column names in ordinal order; empty when the
 *        table has no primary key
 * @param indexes indexes defined on the table
 */
public record Table(String name, List<Column> columns, List<String> primaryKeys,
                    List<Index> indexes) {

    public Table {
        columns = List.copyOf(columns);
        primaryKeys = List.copyOf(primaryKeys);
        indexes = List.copyOf(indexes);
    }
}
