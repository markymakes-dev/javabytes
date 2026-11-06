package com.javabytes.mcp.database;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaCatalogTest {

    @Test
    void acceptsPlainMySqlIdentifiers() {
        assertTrue(SchemaCatalog.isIdentifier("users"));
        assertTrue(SchemaCatalog.isIdentifier("user_2"));
        assertTrue(SchemaCatalog.isIdentifier("_private"));
        assertTrue(SchemaCatalog.isIdentifier("columns$"));
    }

    @Test
    void rejectsInjectionAndQuotedIdentifiers() {
        assertFalse(SchemaCatalog.isIdentifier(null));
        assertFalse(SchemaCatalog.isIdentifier(""));
        assertFalse(SchemaCatalog.isIdentifier("2users"));
        assertFalse(SchemaCatalog.isIdentifier("users; DROP TABLE users"));
        assertFalse(SchemaCatalog.isIdentifier("users--"));
        assertFalse(SchemaCatalog.isIdentifier("`users`"));
        assertFalse(SchemaCatalog.isIdentifier("users name"));
        assertFalse(SchemaCatalog.isIdentifier("users\n"));
    }

    @Test
    void quotesValidatedIdentifiers() {
        assertEquals("`users`", SchemaCatalog.quote("users"));
        assertEquals("'users'", SchemaCatalog.literal("users"));
        assertThrows(IllegalArgumentException.class, () -> SchemaCatalog.quote("bad name"));
        assertThrows(IllegalArgumentException.class, () -> SchemaCatalog.literal("a`b"));
    }
}
