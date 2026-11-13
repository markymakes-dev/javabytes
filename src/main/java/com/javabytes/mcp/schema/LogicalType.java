package com.javabytes.mcp.schema;

import java.util.Locale;

/**
 * Logical column types shared across the conversion pipeline.
 *
 * <p>A logical type is the pipeline's contract for "what kind of value lives in
 * this column". A raw SQL type is mapped only when the logical type can carry
 * everything the raw type says; anything else maps to {@link #UNKNOWN} so the
 * raw type string stays authoritative and no information is lost.</p>
 */
public enum LogicalType {

    STRING,
    TEXT,
    INT,
    BIGINT,
    DECIMAL,
    BOOLEAN,
    DATE,
    TIMESTAMP,
    JSON,
    BLOB,
    UUID,

    /** The raw SQL type cannot be represented losslessly; the raw string rules. */
    UNKNOWN;

    /**
     * Map a raw SQL type such as {@code varchar(191)} to a logical type.
     *
     * <p>The mapping is deliberately lossless-or-nothing: types whose extra
     * semantics the model cannot represent (unsigned, zerofill, enum/set value
     * lists, float/double precision, spatial types) map to {@link #UNKNOWN}.
     * Never throws; returns {@link #UNKNOWN} for null or blank input.</p>
     *
     * @param rawType raw SQL type as reported by the source, may be null
     * @return the logical type, never null
     */
    public static LogicalType fromRaw(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return UNKNOWN;
        }
        String normalized = rawType.trim().toLowerCase(Locale.ROOT);
        int parentheses = normalized.indexOf('(');
        String base = parentheses >= 0 ? normalized.substring(0, parentheses).trim() : normalized;
        if (base.isEmpty() || base.contains(" ")) {
            // "int unsigned", "bigint zerofill": attributes the model cannot carry.
            return UNKNOWN;
        }
        return switch (base) {
            case "varchar", "char" -> STRING;
            case "tinytext", "text", "mediumtext", "longtext" -> TEXT;
            case "tinyint", "smallint", "mediumint", "int", "integer" -> INT;
            case "bigint" -> BIGINT;
            case "decimal", "numeric", "dec", "fixed" -> DECIMAL;
            case "boolean", "bool" -> BOOLEAN;
            case "date" -> DATE;
            case "datetime", "timestamp" -> TIMESTAMP;
            case "json" -> JSON;
            case "binary", "varbinary", "tinyblob", "blob", "mediumblob", "longblob" -> BLOB;
            case "uuid" -> UUID;
            default -> UNKNOWN;
        };
    }
}
