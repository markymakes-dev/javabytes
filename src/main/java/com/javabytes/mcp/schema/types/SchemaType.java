package com.javabytes.mcp.schema.types;

/**
 * JSON Schema types for MCP tool input schemas.
 * Replaces the instanceof chain in McpServerMain.toolDescriptor (lines 423-439).
 * Single source of truth for wire format rendering.
 */
public enum SchemaType {
    STRING("string"),
    BOOLEAN("boolean"),
    INTEGER("integer"),
    NUMBER("number"),
    ARRAY("array"),
    OBJECT("object");

    private final String wireName;

    SchemaType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static SchemaType fromToolSchema(Object schema) {
        if (schema instanceof com.javabytes.mcp.tools.Tool.StringSchema) {
            return STRING;
        } else if (schema instanceof com.javabytes.mcp.tools.Tool.BoolSchema) {
            return BOOLEAN;
        } else if (schema instanceof com.javabytes.mcp.tools.Tool.NumberSchema ns) {
            return ns.isInteger() ? INTEGER : NUMBER;
        } else {
            // Unknown schema type - default to string for safety
            return STRING;
        }
    }
}