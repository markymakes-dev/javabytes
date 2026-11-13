package com.javabytes.mcp.schema;

import java.util.List;

/**
 * One index defined on a {@link Table}.
 *
 * @param name index name as the source reported it, for example {@code PRIMARY}
 * @param columns indexed column names in index order
 * @param unique whether the index enforces uniqueness
 */
public record Index(String name, List<String> columns, boolean unique) {

    public Index {
        columns = List.copyOf(columns);
    }
}
