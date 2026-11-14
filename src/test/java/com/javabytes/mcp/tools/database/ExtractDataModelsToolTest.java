package com.javabytes.mcp.tools.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractDataModelsToolTest {

    private static final ExtractDataModelsTool TOOL = new ExtractDataModelsTool();

    // --- helpers ---------------------------------------------------------

    private static JsonNode run(String code, String sourceType) {
        ObjectNode p = com.javabytes.mcp.utils.JsonUtils.MAPPER.createObjectNode();
        p.put("code", code);
        if (sourceType != null) {
            p.put("source_type", sourceType);
        }
        return TOOL.execute(p);
    }

    private static JsonNode entity(JsonNode result, int index) {
        return result.get("entities").get(index);
    }

    private static JsonNode field(JsonNode entity, int index) {
        return entity.get("fields").get(index);
    }

    // --- mongoose --------------------------------------------------------

    @Test
    void parsesMongooseModelWithTypesAndConstraints() {
        String code = """
                const mongoose = require('mongoose');
                module.exports = mongoose.model('User', {
                  name: { type: String, required: true },
                  email: { type: String, required: true, unique: true },
                  age: { type: Number },
                  active: { type: Boolean, default: true },
                  createdAt: { type: Date }
                });
                """;
        JsonNode result = run(code, "mongoose");
        assertTrue(result.get("success").asBoolean(), result.toPrettyString());
        assertEquals("mongoose", result.get("source_type").asText());
        assertEquals(1, result.get("entity_count").asInt());

        JsonNode user = entity(result, 0);
        assertEquals("User", user.get("name").asText());
        assertEquals("users", user.get("table").asText());
        assertEquals(5, user.get("field_count").asInt());

        JsonNode name = field(user, 0);
        assertEquals("name", name.get("name").asText());
        assertEquals("STRING", name.get("type").asText());
        assertEquals("String", name.get("raw_type").asText());
        assertFalse(name.get("nullable").asBoolean());
        assertEquals("high", name.get("confidence").asText());

        JsonNode email = field(user, 1);
        assertTrue(email.get("unique").asBoolean());
        assertEquals("high", email.get("confidence").asText());

        JsonNode age = field(user, 2);
        assertEquals("INT", age.get("type").asText());
        assertTrue(age.get("nullable").asBoolean());
        assertEquals("medium", age.get("confidence").asText());

        JsonNode active = field(user, 3);
        assertEquals("BOOLEAN", active.get("type").asText());
        assertEquals("true", active.get("default_value").asText());
    }

    @Test
    void mongooseMixedTypeBecomesUnknownWithRawPreserved() {
        String code = """
                mongoose.model('Item', new mongoose.Schema({
                  data: { type: Mixed }
                }));
                """;
        JsonNode result = run(code, "mongoose");
        JsonNode f = field(entity(result, 0), 0);
        assertEquals(LogicalTypeOf.UNKNOWN.name(), f.get("type").asText());
        assertEquals("Mixed", f.get("raw_type").asText());
        assertEquals("low", f.get("confidence").asText());
        assertTrue(f.get("limitations").size() > 0);
    }

    // --- prisma ----------------------------------------------------------

    @Test
    void parsesPrismaSchemaWithScalarAndArrayFields() {
        String code = """
                model User {
                  id    Int     @id @default(autoincrement())
                  email String  @unique
                  name  String?
                  roles String[]
                  createdAt DateTime
                }
                """;
        JsonNode result = run(code, "prisma");
        assertTrue(result.get("success").asBoolean(), result.toPrettyString());
        assertEquals("prisma", result.get("source_type").asText());
        JsonNode user = entity(result, 0);
        assertEquals(5, user.get("field_count").asInt());

        JsonNode id = field(user, 0);
        assertEquals("INT", id.get("type").asText());
        assertEquals("high", id.get("confidence").asText());
        assertTrue(id.get("unique").asBoolean());

        JsonNode name = field(user, 2);
        assertEquals("STRING", name.get("type").asText());
        assertTrue(name.get("nullable").asBoolean());
        assertEquals("medium", name.get("confidence").asText());

        JsonNode roles = field(user, 3);
        assertEquals("STRING", roles.get("type").asText());
        assertEquals("medium", roles.get("confidence").asText());
        assertTrue(roles.get("limitations").size() > 0);
    }

    @Test
    void prismaUnknownTypeIsLowConfidence() {
        String code = """
                model Thing {
                  value UnknownType
                }
                """;
        JsonNode result = run(code, "prisma");
        JsonNode f = field(entity(result, 0), 0);
        assertEquals(LogicalTypeOf.UNKNOWN.name(), f.get("type").asText());
        assertEquals("UnknownType", f.get("raw_type").asText());
        assertEquals("low", f.get("confidence").asText());
    }

    // --- sequelize -------------------------------------------------------

    @Test
    void parsesSequelizeModelWithDataTypes() {
        String code = """
                const { DataTypes } = require('sequelize');
                module.exports = (sequelize) => sequelize.define('Product', {
                  sku: { type: DataTypes.STRING, allowNull: false, unique: true },
                  price: { type: DataTypes.DECIMAL(10, 2), allowNull: false },
                  inStock: { type: DataTypes.BOOLEAN, defaultValue: true },
                  tags: { type: DataTypes.JSON }
                });
                """;
        JsonNode result = run(code, "sequelize");
        assertTrue(result.get("success").asBoolean(), result.toPrettyString());
        JsonNode product = entity(result, 0);
        assertEquals("Product", product.get("name").asText());
        assertEquals(4, product.get("field_count").asInt());

        JsonNode sku = field(product, 0);
        assertEquals("STRING", sku.get("type").asText());
        assertFalse(sku.get("nullable").asBoolean());
        assertTrue(sku.get("unique").asBoolean());
        assertEquals("high", sku.get("confidence").asText());

        JsonNode price = field(product, 1);
        assertEquals("DECIMAL", price.get("type").asText());
        assertFalse(price.get("nullable").asBoolean());

        JsonNode tags = field(product, 3);
        assertEquals("JSON", tags.get("type").asText());
    }

    @Test
    void sequelizeUnknownTypeIsLowConfidence() {
        String code = """
                sequelize.define('Odd', { weird: { type: DataTypes.WeirdType } });
                """;
        JsonNode result = run(code, "sequelize");
        JsonNode f = field(entity(result, 0), 0);
        assertEquals(LogicalTypeOf.UNKNOWN.name(), f.get("type").asText());
        assertEquals("WeirdType", f.get("raw_type").asText());
        assertEquals("low", f.get("confidence").asText());
    }

    // --- sql -------------------------------------------------------------

    @Test
    void parsesSqlCreateTableWithConstraints() {
        String code = """
                CREATE TABLE orders (
                  id INT NOT NULL PRIMARY KEY,
                  customer_id INT NOT NULL,
                  total DECIMAL(10,2) NOT NULL,
                  status VARCHAR(20) DEFAULT 'pending',
                  notes TEXT
                );
                """;
        JsonNode result = run(code, "sql");
        assertTrue(result.get("success").asBoolean(), result.toPrettyString());
        assertEquals("sql", result.get("source_type").asText());
        JsonNode orders = entity(result, 0);
        assertEquals("orders", orders.get("name").asText());
        assertEquals("orders", orders.get("table").asText());
        assertEquals(5, orders.get("field_count").asInt());

        JsonNode id = field(orders, 0);
        assertEquals("INT", id.get("type").asText());
        assertEquals("INT", id.get("raw_type").asText());
        assertFalse(id.get("nullable").asBoolean());
        assertEquals("high", id.get("confidence").asText());

        JsonNode total = field(orders, 2);
        assertEquals("DECIMAL", total.get("type").asText());
        assertEquals("DECIMAL(10,2)", total.get("raw_type").asText());
        assertEquals(10L, total.get("max_length").asLong());

        JsonNode status = field(orders, 3);
        assertEquals("STRING", status.get("type").asText());
        assertEquals("'pending'", status.get("default_value").asText());

        JsonNode notes = field(orders, 4);
        assertEquals("TEXT", notes.get("type").asText());
        assertTrue(notes.get("nullable").asBoolean());
        assertEquals("medium", notes.get("confidence").asText());
    }

    @Test
    void sqlUnknownTypePreservesRawType() {
        String code = """
                CREATE TABLE shapes (
                  outline GEOMETRY,
                  label VARCHAR(50)
                );
                """;
        JsonNode result = run(code, "sql");
        JsonNode outline = field(entity(result, 0), 0);
        assertEquals(LogicalTypeOf.UNKNOWN.name(), outline.get("type").asText());
        assertEquals("GEOMETRY", outline.get("raw_type").asText());
        assertEquals("low", outline.get("confidence").asText());

        JsonNode label = field(entity(result, 0), 1);
        assertEquals("STRING", label.get("type").asText());
        assertEquals("VARCHAR(50)", label.get("raw_type").asText());
    }

    // --- json ------------------------------------------------------------

    @Test
    void parsesJsonStoreWithMixedValueTypes() {
        String code = """
                {
                  "id": 1,
                  "name": "widget",
                  "active": true,
                  "price": 9.99,
                  "metadata": null,
                  "tags": ["a", "b"],
                  "profile": {}
                }
                """;
        JsonNode result = run(code, "json");
        assertTrue(result.get("success").asBoolean(), result.toPrettyString());
        assertEquals("json", result.get("source_type").asText());
        JsonNode root = entity(result, 0);
        assertEquals("root", root.get("name").asText());
        assertEquals(7, root.get("field_count").asInt());

        JsonNode id = field(root, 0);
        assertEquals("INT", id.get("type").asText());
        assertEquals("high", id.get("confidence").asText());

        JsonNode name = field(root, 1);
        assertEquals("STRING", name.get("type").asText());

        JsonNode price = field(root, 3);
        assertEquals("DECIMAL", price.get("type").asText());

        JsonNode metadata = field(root, 4);
        assertEquals(LogicalTypeOf.UNKNOWN.name(), metadata.get("type").asText());
        assertEquals("null", metadata.get("raw_type").asText());
        assertEquals("low", metadata.get("confidence").asText());
        assertTrue(metadata.get("limitations").size() > 0);

        JsonNode tags = field(root, 5);
        assertEquals("JSON", tags.get("type").asText());
    }

    // --- auto-detection and errors ---------------------------------------

    @Test
    void autoDetectsSourceTypeFromCreateTable() {
        JsonNode result = run("CREATE TABLE foo (id INT);", null);
        assertEquals("sql", result.get("source_type").asText());
        assertEquals(1, result.get("entity_count").asInt());
    }

    @Test
    void missingPathAndCodeReturnsMissingParam() {
        JsonNode result = TOOL.execute(com.javabytes.mcp.utils.JsonUtils.MAPPER.createObjectNode());
        assertFalse(result.get("success").asBoolean());
        assertEquals("MISSING_PARAM", result.get("error").asText());
    }

    @Test
    void unsupportedSourceTypeReturnsError() {
        JsonNode result = run("CREATE TABLE foo (id INT);", "xml");
        assertFalse(result.get("success").asBoolean());
        assertEquals("UNSUPPORTED_SOURCE", result.get("error").asText());
    }

    @Test
    void pathOutsideSandboxIsRejected() {
        ObjectNode p = com.javabytes.mcp.utils.JsonUtils.MAPPER.createObjectNode();
        p.put("path", "C:/Windows/System32/drivers/etc/hosts");
        JsonNode result = TOOL.execute(p);
        assertFalse(result.get("success").asBoolean());
        assertEquals("PATH_NOT_ALLOWED", result.get("error").asText());
    }

    // --- logical type mirror ---------------------------------------------

    /** Mirrors the schema LogicalType enum so tests stay decoupled from imports. */
    private enum LogicalTypeOf {
        UNKNOWN
    }
}
