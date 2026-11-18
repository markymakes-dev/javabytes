package com.javabytes.mcp.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.javabytes.mcp.McpServerMain;
import com.javabytes.mcp.McpServerRegistry;
import com.javabytes.mcp.config.FeatureConfiguration;
import com.javabytes.mcp.prompts.PromptRegistry;
import com.javabytes.mcp.schema.types.SchemaRenderer;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #19: structured tool results. Every descriptor carries a derived
 * outputSchema (the ADR-0008 envelope), successful tools/call results carry
 * structuredContent (the envelope object itself), error results omit it —
 * and text content stays byte-for-byte what it was, so the change is purely
 * additive. Round-trip: structuredContent validates against the emitted
 * schema for representative tools across categories (hand-rolled check, no
 * validator dependency).
 */
class StructuredOutputTest {

    /** Real declared tools, default feature configuration, real registry. */
    private ProtocolSession sessionWithTools() {
        McpServerRegistry registry = new McpServerRegistry();
        FeatureConfiguration features = FeatureConfiguration.parse(null, null);
        for (Tool tool : McpServerMain.declaredTools()) {
            if (features.isEnabled(tool.getName())) {
                registry.register(tool);
            }
        }
        return new ProtocolSession(registry, features, new SchemaRenderer(), new PromptRegistry());
    }

    // --- descriptors ---

    @Test
    void everyDescriptorCarriesDerivedOutputSchema() throws Exception {
        ProtocolSession session = sessionWithTools();
        JsonNode tools = session.handleToolsList().get("tools");
        assertTrue(tools.isArray() && !tools.isEmpty(), "registry must be non-empty");
        for (JsonNode tool : tools) {
            JsonNode out = tool.get("outputSchema");
            assertNotNull(out, tool.get("name").asText() + " has no outputSchema");
            assertEquals("object", out.get("type").asText());
            assertEquals("boolean", out.at("/properties/success/type").asText());
            assertEquals("string", out.at("/properties/error/type").asText());
            assertEquals("string", out.at("/properties/message/type").asText());
            assertTrue(out.get("required").isArray());
            assertEquals(1, out.get("required").size());
            assertEquals("success", out.get("required").get(0).asText());
            assertTrue(out.get("additionalProperties").asBoolean(),
                    tool.get("name").asText() + " must permit payload keys");
        }
    }

    // --- successful results ---

    @Test
    void successfulCallCarriesStructuredContentAndIdenticalText() throws Exception {
        ProtocolSession session = sessionWithTools();
        JsonNode result = session.handleToolsCall("get_version", JsonUtils.MAPPER.createObjectNode());

        assertFalse(result.get("isError").asBoolean());
        JsonNode structured = result.get("structuredContent");
        assertNotNull(structured, "successful results must carry structuredContent");
        assertTrue(structured.isObject(), "structuredContent must be the envelope object, not a string");
        assertTrue(structured.get("success").asBoolean());

        // Text content unchanged: still the serialized envelope.
        JsonNode text = result.get("content").get(0);
        assertEquals("text", text.get("type").asText());
        JsonNode reparsed = JsonUtils.MAPPER.readTree(text.get("text").asText());
        assertEquals(reparsed, structured,
                "text payload and structuredContent must carry the same envelope");
    }

    @Test
    void errorEnvelopeOmitsStructuredContentButKeepsText() throws Exception {
        ProtocolSession session = sessionWithTools();
        // A path outside the sandbox → error envelope.
        JsonNode badArgs = JsonUtils.MAPPER.createObjectNode()
                .put("path", "Z:/definitely/not/allowed.txt");
        JsonNode result = session.handleToolsCall("read_text_file", badArgs);

        assertTrue(result.get("isError").asBoolean(),
                "envelope with success:false must surface as isError");
        assertNull(result.get("structuredContent"),
                "error results must not carry structuredContent (MCP guidance)");
        JsonNode reparsed = JsonUtils.MAPPER.readTree(result.get("content").get(0).get("text").asText());
        assertFalse(reparsed.get("success").asBoolean(), "envelope still travels in text content");
    }

    // --- round-trip: structuredContent validates against outputSchema ---

    @Test
    void roundTripStructuredContentValidatesAgainstSchema() throws Exception {
        ProtocolSession session = sessionWithTools();
        JsonNode schema = SchemaRenderer.outputEnvelopeSchema();

        // Representative tools across categories (issue #19 testing decisions).
        String repoFile = Path.of(System.getProperty("user.dir"),
                "src/main/java/com/javabytes/mcp/tools/system/GetVersionTool.java").toString();
        List<ToolCall> probes = List.of(
                new ToolCall("get_version", JsonUtils.MAPPER.createObjectNode()),
                new ToolCall("read_text_file",
                        JsonUtils.MAPPER.createObjectNode().put("path", repoFile)),
                new ToolCall("analyze_javascript",
                        JsonUtils.MAPPER.createObjectNode()
                                .put("code", "function add(a, b) { return a + b; }")
                                .put("file_name", "math.js")));

        for (ToolCall probe : probes) {
            JsonNode result = session.handleToolsCall(probe.name(), probe.args());
            JsonNode structured = result.get("structuredContent");
            if (result.get("isError").asBoolean()) {
                assertNull(structured, probe.name() + ": error results carry no structuredContent");
                continue;
            }
            validateAgainst(structured, schema, probe.name());
        }
    }

    private record ToolCall(String name, JsonNode args) {}

    /** Minimal hand-rolled conformance check (no JSON-Schema dependency). */
    private void validateAgainst(JsonNode envelope, JsonNode schema, String toolName) {
        assertTrue(envelope.isObject(), toolName + ": structuredContent must be an object");
        for (JsonNode req : schema.get("required")) {
            assertTrue(envelope.has(req.asText()),
                    toolName + ": structuredContent missing required key " + req.asText());
        }
        for (Iterator<String> it = envelope.fieldNames(); it.hasNext(); ) {
            String key = it.next();
            JsonNode declared = schema.at("/properties/" + key);
            if (declared.isMissingNode()) {
                continue; // additionalProperties: true — tool-specific payload
            }
            String want = declared.get("type").asText();
            boolean ok = switch (want) {
                case "boolean" -> envelope.get(key).isBoolean();
                case "string" -> envelope.get(key).isTextual() || envelope.get(key).isNull();
                case "integer", "number" -> envelope.get(key).isNumber();
                case "object" -> envelope.get(key).isObject() || envelope.get(key).isNull();
                case "array" -> envelope.get(key).isArray() || envelope.get(key).isNull();
                default -> true;
            };
            assertTrue(ok, toolName + ": key '" + key + "' violates declared type " + want);
        }
    }
}
