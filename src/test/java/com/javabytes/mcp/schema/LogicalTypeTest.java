package com.javabytes.mcp.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogicalTypeTest {

    @Test
    void mapsStringTypesToString() {
        assertEquals(LogicalType.STRING, LogicalType.fromRaw("varchar(191)"));
        assertEquals(LogicalType.STRING, LogicalType.fromRaw("VARCHAR(80)"));
        assertEquals(LogicalType.STRING, LogicalType.fromRaw("char(2)"));
    }

    @Test
    void mapsIntegerTypes() {
        assertEquals(LogicalType.INT, LogicalType.fromRaw("int"));
        assertEquals(LogicalType.INT, LogicalType.fromRaw("integer"));
        assertEquals(LogicalType.INT, LogicalType.fromRaw("smallint"));
        assertEquals(LogicalType.INT, LogicalType.fromRaw("mediumint"));
        assertEquals(LogicalType.BIGINT, LogicalType.fromRaw("bigint"));
    }

    @Test
    void mapsDecimalBooleanAndTemporalTypes() {
        assertEquals(LogicalType.DECIMAL, LogicalType.fromRaw("decimal(10,2)"));
        assertEquals(LogicalType.DECIMAL, LogicalType.fromRaw("numeric(8,3)"));
        assertEquals(LogicalType.BOOLEAN, LogicalType.fromRaw("boolean"));
        assertEquals(LogicalType.BOOLEAN, LogicalType.fromRaw("bool"));
        assertEquals(LogicalType.DATE, LogicalType.fromRaw("date"));
        assertEquals(LogicalType.TIMESTAMP, LogicalType.fromRaw("datetime"));
        assertEquals(LogicalType.TIMESTAMP, LogicalType.fromRaw("timestamp"));
    }

    @Test
    void mapsTextJsonBinaryAndUuidTypes() {
        assertEquals(LogicalType.TEXT, LogicalType.fromRaw("text"));
        assertEquals(LogicalType.TEXT, LogicalType.fromRaw("longtext"));
        assertEquals(LogicalType.JSON, LogicalType.fromRaw("json"));
        assertEquals(LogicalType.BLOB, LogicalType.fromRaw("blob"));
        assertEquals(LogicalType.BLOB, LogicalType.fromRaw("varbinary(16)"));
        assertEquals(LogicalType.UUID, LogicalType.fromRaw("uuid"));
    }

    @Test
    void keepsUnmappableRawTypesUnknown() {
        assertEquals(LogicalType.UNKNOWN, LogicalType.fromRaw("geometry"));
        assertEquals(LogicalType.UNKNOWN, LogicalType.fromRaw("enum('a','b')"));
        assertEquals(LogicalType.UNKNOWN, LogicalType.fromRaw("set('a','b')"));
        assertEquals(LogicalType.UNKNOWN, LogicalType.fromRaw("float"));
        assertEquals(LogicalType.UNKNOWN, LogicalType.fromRaw("double"));
    }

    @Test
    void keepsTypesWithUnrepresentableAttributesUnknown() {
        assertEquals(LogicalType.UNKNOWN, LogicalType.fromRaw("int unsigned"));
        assertEquals(LogicalType.UNKNOWN, LogicalType.fromRaw("bigint zerofill"));
    }

    @Test
    void mapsBlankAndNullToUnknown() {
        assertEquals(LogicalType.UNKNOWN, LogicalType.fromRaw(null));
        assertEquals(LogicalType.UNKNOWN, LogicalType.fromRaw(""));
        assertEquals(LogicalType.UNKNOWN, LogicalType.fromRaw("   "));
    }
}
