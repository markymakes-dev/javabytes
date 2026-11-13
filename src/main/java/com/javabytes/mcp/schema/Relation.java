package com.javabytes.mcp.schema;

/**
 * One foreign-key row: a single (source column, target column) pair.
 *
 * <p>This mirrors one row of MySQL's {@code information_schema.KEY_COLUMN_USAGE}
 * joined to {@code REFERENTIAL_CONSTRAINTS}, which is exactly what the
 * database reports and what the FD analyzer consumes. Composite foreign keys
 * appear as several relations that share their source table and rule values.</p>
 *
 * @param sourceTable table containing the foreign key
 * @param sourceColumn foreign-key column
 * @param targetTable referenced table
 * @param targetColumn referenced column
 * @param onDelete ON DELETE rule such as {@code CASCADE}, or null when unknown
 * @param onUpdate ON UPDATE rule, or null when unknown
 */
public record Relation(String sourceTable, String sourceColumn, String targetTable,
                       String targetColumn, String onDelete, String onUpdate) {
}
