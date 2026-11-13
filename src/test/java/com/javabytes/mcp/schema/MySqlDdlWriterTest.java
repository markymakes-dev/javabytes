package com.javabytes.mcp.schema;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlDdlWriterTest {

    @Test
    void rendersCreateTableWithTypesConstraintsAndMarker() {
        SchemaModel model = new SchemaModel("shop",
                List.of(new Table("users",
                        List.of(
                                new Column("id", LogicalType.INT, "int", false, null, false, null),
                                new Column("email", LogicalType.STRING, "varchar(191)", false,
                                        null, true, 191L),
                                new Column("handle", LogicalType.STRING, "varchar(40)", true,
                                        null, true, 40L),
                                new Column("bio", LogicalType.TEXT, "text", true, null, false, null),
                                new Column("active", LogicalType.BOOLEAN, "tinyint(1)", true,
                                        "1", false, null),
                                new Column("created_at", LogicalType.TIMESTAMP, "timestamp", false,
                                        "CURRENT_TIMESTAMP", false, null),
                                new Column("score", LogicalType.DECIMAL, "decimal(10,2)", true,
                                        null, false, null),
                                new Column("token", LogicalType.UUID, "uuid", false, null, false, null)),
                        List.of("id"),
                        List.of(new Index("PRIMARY", List.of("id"), true),
                                new Index("idx_users_email", List.of("email"), true)))),
                List.of());

        String ddl = MySqlDdlWriter.write(model);

        assertTrue(ddl.contains("-- proposed — verify before executing"), ddl);
        assertTrue(ddl.contains("CREATE TABLE `users` ("), ddl);
        assertTrue(ddl.contains("`id` INT NOT NULL"), ddl);
        assertTrue(ddl.contains("`email` VARCHAR(191) NOT NULL,"), ddl);
        assertFalse(ddl.contains("NOT NULL UNIQUE"), ddl);
        assertTrue(ddl.contains("`handle` VARCHAR(40) UNIQUE"), ddl);
        assertTrue(ddl.contains("`bio` TEXT"), ddl);
        assertTrue(ddl.contains("`active` BOOLEAN DEFAULT 1"), ddl);
        assertTrue(ddl.contains("`created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"), ddl);
        assertTrue(ddl.contains("`score` DECIMAL(10,2)"), ddl);
        assertTrue(ddl.contains("`token` CHAR(36) NOT NULL"), ddl);
        assertTrue(ddl.contains("PRIMARY KEY (`id`)"), ddl);
        assertTrue(ddl.contains("UNIQUE KEY `idx_users_email` (`email`)"), ddl);
        assertFalse(ddl.contains("UNIQUE KEY `PRIMARY`"), ddl);
        assertTrue(ddl.endsWith(";\n\n"), ddl);
    }

    @Test
    void passesUnknownRawTypesThroughVerbatim() {
        SchemaModel model = new SchemaModel(null,
                List.of(new Table("shapes",
                        List.of(
                                new Column("outline", LogicalType.UNKNOWN, "geometry", true, null, false, null),
                                new Column("name", LogicalType.STRING, "varchar(255)", true, null, false, null)),
                        List.of(), List.of())),
                List.of());

        String ddl = MySqlDdlWriter.write(model);

        assertTrue(ddl.contains("`outline` geometry"), ddl);
        assertTrue(ddl.contains("`name` VARCHAR(255)"), ddl);
    }

    @Test
    void quotesAndEscapesDefaultValues() {
        SchemaModel model = new SchemaModel(null,
                List.of(new Table("users",
                        List.of(
                                new Column("nickname", LogicalType.STRING, "varchar(40)", true,
                                        "O'Brien", false, 40L),
                                new Column("updated_at", LogicalType.TIMESTAMP, "timestamp", false,
                                        "current_timestamp()", false, null)),
                        List.of(), List.of())),
                List.of());

        String ddl = MySqlDdlWriter.write(model);

        assertTrue(ddl.contains("`nickname` VARCHAR(40) DEFAULT 'O''Brien'"), ddl);
        assertTrue(ddl.contains("DEFAULT current_timestamp()"), ddl);
    }

    @Test
    void rendersForeignKeysAsAlterStatementsWithRules() {
        SchemaModel model = new SchemaModel("shop",
                List.of(new Table("order_items",
                                List.of(new Column("order_id", LogicalType.INT, "int", false, null, false, null)),
                                List.of(), List.of()),
                        new Table("orders",
                                List.of(new Column("id", LogicalType.INT, "int", false, null, false, null)),
                                List.of(), List.of())),
                List.of(new Relation("order_items", "order_id", "orders", "id", "CASCADE", null)));

        String ddl = MySqlDdlWriter.write(model);

        assertTrue(ddl.contains("ALTER TABLE `order_items`\n"
                + "  ADD CONSTRAINT `fk_order_items_order_id` FOREIGN KEY (`order_id`) "
                + "REFERENCES `orders` (`id`) ON DELETE CASCADE;\n"), ddl);
        assertFalse(ddl.contains("ON UPDATE"), ddl);
    }

    @Test
    void deduplicatesGeneratedConstraintNames() {
        SchemaModel model = new SchemaModel(null,
                List.of(new Table("order_items",
                                List.of(new Column("order_id", LogicalType.INT, "int", false, null, false, null)),
                                List.of(), List.of())),
                List.of(new Relation("order_items", "order_id", "orders", "id", null, null),
                        new Relation("order_items", "order_id", "legacy_orders", "id", null, null)));

        String ddl = MySqlDdlWriter.write(model);

        assertTrue(ddl.contains("ADD CONSTRAINT `fk_order_items_order_id` "), ddl);
        assertTrue(ddl.contains("ADD CONSTRAINT `fk_order_items_order_id_2` "), ddl);
    }

    @Test
    void rejectsUnsafeIdentifiersAndMissingRawTypes() {
        SchemaModel badTableName = new SchemaModel(null,
                List.of(new Table("bad name", List.of(), List.of(), List.of())), List.of());
        assertThrows(IllegalArgumentException.class, () -> MySqlDdlWriter.write(badTableName));

        SchemaModel unknownWithoutRaw = new SchemaModel(null,
                List.of(new Table("shapes",
                        List.of(new Column("outline", LogicalType.UNKNOWN, "  ", true, null, false, null)),
                        List.of(), List.of())), List.of());
        assertThrows(IllegalArgumentException.class, () -> MySqlDdlWriter.write(unknownWithoutRaw));
    }

    @Test
    void rendersEmptyModelAsEmptyScript() {
        assertEquals("", MySqlDdlWriter.write(new SchemaModel("shop", List.of(), List.of())));
    }
}
