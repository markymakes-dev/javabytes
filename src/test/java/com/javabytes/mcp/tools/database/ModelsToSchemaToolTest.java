package com.javabytes.mcp.tools.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelsToSchemaToolTest {

    private static final ModelsToSchemaTool TOOL = new ModelsToSchemaTool();
    private static final ObjectMapper M = com.javabytes.mcp.utils.JsonUtils.MAPPER;

    // --- fixtures ---------------------------------------------------------

    /** Two normalized tables: users (id -> name, email) and order_items. */
    private static ObjectNode usersEntity() {
        ObjectNode e = M.createObjectNode();
        e.put("name", "User");
        e.put("table", "users");
        ArrayNode f = M.createArrayNode();
        f.add(field("id", "id", "INT", "int", false, null, true, null, "high"));
        f.add(field("name", "name", "STRING", "varchar(100)", false, null, false, null, "high"));
        f.add(field("email", "email", "STRING", "varchar(191)", false, null, true, 191L, "high"));
        e.set("fields", f);
        return e;
    }

    private static ObjectNode orderItemsEntity() {
        ObjectNode e = M.createObjectNode();
        e.put("name", "OrderItem");
        e.put("table", "order_items");
        ArrayNode f = M.createArrayNode();
        f.add(field("order_id", "order_id", "INT", "int", false, null, true, null, "high"));
        f.add(field("product_id", "product_id", "INT", "int", false, null, true, null, "high"));
        f.add(field("product_name", "product_name", "STRING", "varchar(100)", true, null, false, null, "medium"));
        f.add(field("quantity", "quantity", "INT", "int", true, null, false, null, "medium"));
        e.set("fields", f);
        return e;
    }

    private static ObjectNode field(String name, String column, String type, String rawType,
                                    boolean nullable, String defaultValue, boolean unique,
                                    Long maxLength, String confidence, String... limitations) {
        ObjectNode f = M.createObjectNode();
        f.put("name", name);
        f.put("column", column);
        f.put("type", type);
        f.put("raw_type", rawType);
        f.put("nullable", nullable);
        if (defaultValue != null) f.put("default_value", defaultValue);
        f.put("unique", unique);
        if (maxLength != null) f.put("max_length", maxLength);
        f.put("confidence", confidence);
        ArrayNode lim = M.createArrayNode();
        for (String l : limitations) lim.add(l);
        f.set("limitations", lim);
        return f;
    }

    private static ObjectNode paramsWithEntities(ObjectNode... entities) {
        ObjectNode p = M.createObjectNode();
        ArrayNode a = M.createArrayNode();
        for (ObjectNode e : entities) a.add(e);
        p.set("entities", a);
        return p;
    }

    // --- proposed DDL when FDs are not declared --------------------------

    @Test
    void proposesDdlForParsedEntities() {
        JsonNode result = TOOL.execute(paramsWithEntities(usersEntity()));
        assertTrue(result.get("success").asBoolean(), result.toPrettyString());
        assertFalse(result.get("pass_through").asBoolean());
        assertTrue(result.get("pass_through_message").isNull());
        assertFalse(result.get("proposed_ddl").asText().isBlank());
        assertTrue(result.get("proposed_ddl").asText().contains("CREATE TABLE `users`"));
        assertTrue(result.get("proposed_ddl").asText().contains("-- proposed — verify before executing"));
        assertEquals(1, result.get("tables").size());
        assertEquals("users", result.get("tables").get(0).get("table").asText());
        assertEquals(1, result.get("entity_count").asInt());
        assertEquals(3, result.get("field_count").asInt());
    }

    @Test
    void noFunctionalDependenciesMeansUnanalyzedVerdict() {
        JsonNode result = TOOL.execute(paramsWithEntities(orderItemsEntity()));
        assertTrue(result.get("success").asBoolean());
        assertFalse(result.get("pass_through").asBoolean());
        assertFalse(result.get("proposed_ddl").asText().isBlank());
        JsonNode table = result.get("tables").get(0);
        assertEquals("unanalyzed", table.get("normal_form").asText());
        assertTrue(table.get("verdict").asText().contains("not proven"));
    }

    @Test
    void emptyEntitiesReturnsEmptyResult() {
        ObjectNode params = M.createObjectNode();
        params.set("entities", M.createArrayNode());
        JsonNode result = TOOL.execute(params);
        assertTrue(result.get("success").asBoolean());
        assertEquals(0, result.get("entity_count").asInt());
        assertEquals(0, result.get("tables").size());
        assertEquals(0, result.get("verification_list").size());
        assertFalse(result.get("pass_through").asBoolean());
    }

    // --- pass-through rule ------------------------------------------------

    @Test
    void passThroughWhenDeclaredFdsShowAlreadyNormalized() {
        ObjectNode p = paramsWithEntities(usersEntity());
        p.put("functional_dependencies", "users: id -> name, email");
        JsonNode result = TOOL.execute(p);
        assertTrue(result.get("success").asBoolean(), result.toPrettyString());
        assertTrue(result.get("pass_through").asBoolean());
        assertEquals("already in strongest applicable form — no changes proposed",
                result.get("pass_through_message").asText());
        assertEquals("", result.get("proposed_ddl").asText());
        JsonNode table = result.get("tables").get(0);
        assertEquals("BCNF", table.get("normal_form").asText());
        assertTrue(table.get("verdict").asText().contains("no changes proposed"));
    }

    @Test
    void declaredFdsWithViolationProposeDdlInsteadOfPassThrough() {
        ObjectNode p = paramsWithEntities(orderItemsEntity());
        p.put("functional_dependencies",
                "order_items: product_id -> product_name");
        JsonNode result = TOOL.execute(p);
        assertTrue(result.get("success").asBoolean(), result.toPrettyString());
        assertFalse(result.get("pass_through").asBoolean());
        assertFalse(result.get("proposed_ddl").asText().isBlank());
        JsonNode table = result.get("tables").get(0);
        assertEquals("2NF", table.get("normal_form").asText());
        assertTrue(table.get("verdict").asText().contains("Needs normalization"));
    }

    // --- verification list ------------------------------------------------

    @Test
    void verificationListFlagsMediumAndLowButNotHigh() {
        ObjectNode mixed = M.createObjectNode();
        mixed.put("name", "Item");
        mixed.put("table", "items");
        ArrayNode f = M.createArrayNode();
        f.add(field("id", "id", "INT", "int", false, null, true, null, "high"));
        f.add(field("name", "name", "STRING", "varchar(100)", true, null, false, null, "medium"));
        f.add(field("note", "note", "UnknownType", "weird", true, null, false, null, "low",
                "type 'weird' could not be mapped to a logical type"));
        mixed.set("fields", f);

        JsonNode result = TOOL.execute(paramsWithEntities(mixed));
        assertTrue(result.get("success").asBoolean());
        ArrayNode verification = (ArrayNode) result.get("verification_list");
        assertEquals(2, verification.size());

        assertEquals("name", verification.get(0).get("column").asText());
        assertEquals("medium", verification.get(0).get("confidence").asText());
        assertEquals("items", verification.get(0).get("table").asText());

        assertEquals("note", verification.get(1).get("column").asText());
        assertEquals("low", verification.get(1).get("confidence").asText());
        assertTrue(verification.get(1).get("limitations").size() > 0);
    }

    @Test
    void highConfidenceFieldsPassSilently() {
        JsonNode result = TOOL.execute(paramsWithEntities(usersEntity()));
        assertTrue(result.get("success").asBoolean());
        assertEquals(0, result.get("verification_list").size());
    }

    // --- sandbox-validated file reads ------------------------------------

    @Test
    void readsExtractedModelsFromAllowedPath(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("extracted.json");
        ObjectNode payload = M.createObjectNode();
        payload.put("source_type", "sql");
        ArrayNode entities = M.createArrayNode();
        entities.add(usersEntity());
        payload.set("entities", entities);
        Files.writeString(file, com.javabytes.mcp.utils.JsonUtils.MAPPER.writeValueAsString(payload));

        ObjectNode p = M.createObjectNode();
        p.put("path", file.toString());
        JsonNode result = TOOL.execute(p);
        assertTrue(result.get("success").asBoolean(), result.toPrettyString());
        assertEquals("sql", result.get("source_type").asText());
        assertEquals(1, result.get("entity_count").asInt());
        assertFalse(result.get("proposed_ddl").asText().isBlank());
    }

    @Test
    void pathOutsideSandboxIsRejected() {
        ObjectNode p = M.createObjectNode();
        p.put("path", System.getProperty("os.name").startsWith("Windows")
                ? "C:\\Windows\\System32\\drivers\\etc\\hosts"
                : "/etc/passwd");
        JsonNode result = TOOL.execute(p);
        assertFalse(result.get("success").asBoolean());
        assertEquals("PATH_NOT_ALLOWED", result.get("error").asText());
    }

    @Test
    void missingEntitiesAndPathReturnsMissingParam() {
        JsonNode result = TOOL.execute(M.createObjectNode());
        assertFalse(result.get("success").asBoolean());
        assertEquals("MISSING_PARAM", result.get("error").asText());
    }

    @Test
    void pathWithoutEntitiesArrayIsInvalidInput(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("not-models.json");
        Files.writeString(file, "{\"tables\": []}");
        ObjectNode p = M.createObjectNode();
        p.put("path", file.toString());
        JsonNode result = TOOL.execute(p);
        assertFalse(result.get("success").asBoolean());
        assertEquals("INVALID_INPUT", result.get("error").asText());
    }
}
