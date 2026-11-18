package com.javabytes.mcp.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.javabytes.mcp.McpServerRegistry;
import com.javabytes.mcp.config.FeatureConfiguration;
import com.javabytes.mcp.prompts.CrewPrompts;
import com.javabytes.mcp.prompts.PromptRegistry;
import com.javabytes.mcp.protocol.ProtocolSession;
import com.javabytes.mcp.schema.types.SchemaRenderer;
import com.javabytes.mcp.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #18 transport routing (thin): new prompt methods route through the
 * JSON-RPC loop; unknown methods still return method-not-found.
 */
class PromptsTransportRoutingTest {

    private static String runLoop(String... lines) {
        StringBuilder in = new StringBuilder();
        for (String line : lines) {
            in.append(line).append("\n");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonRpcTransport transport = new JsonRpcTransport(
                new ProtocolSession(new McpServerRegistry(), FeatureConfiguration.parse(null, null),
                        new SchemaRenderer(), promptsRegistry()),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new BufferedReader(new StringReader(in.toString())));
        transport.run();
        return out.toString(StandardCharsets.UTF_8);
    }

    private static PromptRegistry promptsRegistry() {
        PromptRegistry registry = new PromptRegistry();
        registry.registerAll(CrewPrompts.definitions());
        return registry;
    }

    @Test
    void promptsListRoutesThroughTransport() throws Exception {
        String out = runLoop("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"prompts/list\"}");
        JsonNode resp = singleResponse(out);
        assertEquals(1, resp.get("id").asInt());
        assertEquals(4, resp.at("/result/prompts").size());
        assertEquals("recon", resp.at("/result/prompts/0/name").asText());
    }

    @Test
    void promptsGetRoutesThroughTransportWithSubstitution() throws Exception {
        String out = runLoop("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"prompts/get\","
                + "\"params\":{\"name\":\"recon\",\"arguments\":{\"source_path\":\"C:/dev/app\"}}}");
        JsonNode resp = singleResponse(out);
        assertEquals(7, resp.get("id").asInt());
        String text = resp.at("/result/messages/0/content/text").asText();
        assertTrue(text.contains("C:/dev/app"));
        assertTrue(text.contains("recon"));
    }

    @Test
    void promptsGetMissingArgumentsStillRoutesAsEmptyObject() throws Exception {
        String out = runLoop("{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"prompts/get\","
                + "\"params\":{\"name\":\"report\"}}");
        JsonNode resp = singleResponse(out);
        // report has one required arg (project) — missing args object means
        // the session-level invalid-params error, not a transport parse error.
        assertEquals(-32602, resp.at("/result/error/code").asInt());
        assertTrue(resp.at("/result/error/message").asText().contains("project"));
    }

    @Test
    void unknownMethodStaysMethodNotFound() throws Exception {
        String out = runLoop("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"resources/list\"}");
        JsonNode resp = singleResponse(out);
        assertEquals(-32601, resp.at("/error/code").asInt());
        assertTrue(resp.at("/error/message").asText().contains("resources/list"));
    }

    private static JsonNode singleResponse(String out) throws Exception {
        List<String> lines = out.lines().filter(l -> !l.isBlank()).toList();
        assertEquals(1, lines.size(), "expected exactly one response line, got: " + out);
        return JsonUtils.MAPPER.readTree(lines.get(0));
    }
}
