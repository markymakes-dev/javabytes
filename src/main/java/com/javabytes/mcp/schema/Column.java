package com.javabytes.mcp.schema;

/**
 * One column of a {@link Table}.
 *
 * @param name column name
 * @param logicalType pipeline-level type; {@link LogicalType#UNKNOWN} when the
 *        raw type cannot be mapped losslessly
 * @param rawType raw SQL type exactly as the source reported it, for example
 *        {@code varchar(191)}; never rewritten, so no information is lost
 * @param nullable whether the column accepts NULL
 * @param defaultValue default value as the source reported it, or null
 * @param unique whether the column is subject to a single-column unique
 *        constraint
 * @param maxLength maximum character length the source reported, or null when
 *        not applicable
 */
public record Column(String name, LogicalType logicalType, String rawType, boolean nullable,
                     String defaultValue, boolean unique, Long maxLength) {
}
