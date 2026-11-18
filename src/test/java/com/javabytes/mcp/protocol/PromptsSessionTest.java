package com.javabytes.mcp.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.javabytes.mcp.McpServerRegistry;
import com.javabytes.mcp.config.FeatureConfiguration;
import com.javabytes.mcp.prompts.CrewPrompts;
import com.javabytes.mcp.prompts.PromptRegistry;
import com.javabytes.mcp.schema.types.SchemaRenderer;
import com.javabytes.mcp.utils.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #18: prompts capability wire behavior on the protocol session.
 */
class PromptsSessionTest {

    private ProtocolSession session;

    @BeforeEach
    void setUp() {
        PromptRegistry prompts = new PromptRegistry();
        prompts.registerAll(CrewPrompts.definitions());
        session = new ProtocolSession(new McpServerRegistry(),
                FeatureConfiguration.parse(null, null), new SchemaRenderer(), prompts);
    }

    @Test
    void initializeAlwaysAdvertisesPromptsCapability() {
        JsonNode init = session.handleInitialize(JsonUtils.MAPPER.createObjectNode());
        assertTrue(init.at("/capabilities/prompts").isObject(),
                "prompts must be advertised alongside tools (issue #18)");
        assertTrue(init.at("/capabilities/tools").isObject());
    }

    @Test
    void promptsListReturnsCrewPackWithArguments() {
        JsonNode result = session.handlePromptsList();
        JsonNode prompts = result.get("prompts");
        assertTrue(prompts.isArray());
        assertEquals(4, prompts.size());
        JsonNode recon = prompts.get(0);
        assertEquals("recon", recon.get("name").asText());
        assertFalse(recon.get("description").asText().isBlank());
        JsonNode args = recon.get("arguments");
        assertTrue(args.isArray());
        assertEquals("source_path", args.get(0).get("name").asText());
        assertTrue(args.get(0).get("required").asBoolean());
    }

    @Test
    void promptsGetRendersTemplateIntoUserMessage() {
        com.fasterxml.jackson.databind.node.ObjectNode params = JsonUtils.MAPPER.createObjectNode()
                .put("name", "recon");
        JsonNode arguments = JsonUtils.MAPPER.createObjectNode()
                .put("source_path", "C:/dev/source-app");
        params.set("arguments", arguments);

        JsonNode result = session.handlePromptsGet("recon", arguments);
        assertFalse(result.has("error"));
        JsonNode message = result.get("messages").get(0);
        assertEquals("user", message.get("role").asText());
        JsonNode content = message.get("content");
        assertEquals("text", content.get("type").asText());
        String text = content.get("text").asText();
        assertTrue(text.contains("C:/dev/source-app"), "argument must be substituted");
        assertFalse(text.contains("{{source_path}}"));
    }

    @Test
    void promptsGetUnknownPromptIsInvalidParams() {
        JsonNode result = session.handlePromptsGet("ghost", JsonUtils.MAPPER.createObjectNode());
        assertEquals(-32602, result.at("/error/code").asInt());
        assertTrue(result.at("/error/message").asText().contains("Unknown prompt: ghost"));
    }

    @Test
    void promptsGetMissingRequiredArgumentIsInvalidParams() {
        JsonNode arguments = JsonUtils.MAPPER.createObjectNode(); // no source_path
        JsonNode result = session.handlePromptsGet("recon", arguments);
        assertEquals(-32602, result.at("/error/code").asInt());
        assertTrue(result.at("/error/message").asText().contains("source_path"));
    }
}
