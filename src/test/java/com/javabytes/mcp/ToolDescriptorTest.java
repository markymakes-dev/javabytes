package com.javabytes.mcp;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.skills.system.GetVersionSkill;
import com.javabytes.mcp.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolDescriptorTest {

    @Test
    void descriptorCarriesTitleDescriptionSchemaAndAnnotations() {
        ObjectNode descriptor = McpServerMain.toolDescriptor(new GetVersionSkill());

        assertEquals("get_version", descriptor.get("name").asText());
        assertEquals("Get Server Version", descriptor.get("title").asText());
        assertTrue(descriptor.get("description").asText().contains("version"));
        assertEquals("object", descriptor.at("/inputSchema/type").asText());

        assertNotNull(descriptor.get("annotations"));
        assertEquals("Get Server Version", descriptor.at("/annotations/title").asText());
        assertTrue(descriptor.at("/annotations/readOnlyHint").asBoolean());
        assertFalse(descriptor.at("/annotations/destructiveHint").asBoolean());
        assertTrue(descriptor.at("/annotations/idempotentHint").asBoolean());
        assertFalse(descriptor.at("/annotations/openWorldHint").asBoolean());
    }

    @Test
    void generatorDescriptorIsReadOnlyAndClosedByDefault() {
        ObjectNode descriptor = McpServerMain.toolDescriptor(
                new com.javabytes.mcp.skills.springboot.GenerateSpringBootEntitySkill());

        assertEquals("Generate JPA Entity", descriptor.get("title").asText());
        assertTrue(descriptor.at("/annotations/readOnlyHint").asBoolean());
        assertFalse(descriptor.at("/annotations/openWorldHint").asBoolean());
        assertTrue(descriptor.at("/annotations/idempotentHint").asBoolean());
    }

    @Test
    void disabledToolProducesActionableMessage() {
        String message = McpServerMain.unknownToolMessage("springboot_generate_entity");
        assertTrue(message.contains("disabled"));
        assertTrue(message.contains("JAVABYTES_ENABLE=springboot"));

        String unknown = McpServerMain.unknownToolMessage("no_such_tool");
        assertTrue(unknown.contains("Unknown tool"));
    }
}
