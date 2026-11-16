package com.javabytes.mcp.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.protocol.ProtocolSession;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.ProtocolLogger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JSON-RPC 2.0 stdio transport.
 * Reads requests line-by-line from stdin, delegates to {@link ProtocolSession},
 * writes responses to stdout. stdout is reserved ONLY for protocol responses;
 * all logging goes to stderr.
 */
public class JsonRpcTransport {

    private final ProtocolSession session;
    private final PrintStream out;
    private final BufferedReader reader;
    private final AtomicInteger rootsRequestCounter = new AtomicInteger();
    private String pendingRootsRequestId = null;

    public JsonRpcTransport(ProtocolSession session, PrintStream out, BufferedReader reader) {
        this.session = session;
        this.out = out;
        this.reader = reader;
    }

    /**
     * Runs the transport loop until stdin closes.
     */
    public void run() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                ProtocolLogger.log("<--", line);

                JsonNode msg;
                try {
                    msg = JsonUtils.MAPPER.readTree(line);
                } catch (Exception e) {
                    writeResponse(-32700, "Parse error", null);
                    continue;
                }

                // A message without a method is a response to a server-initiated request.
                // Only roots/list responses are expected.
                if (!msg.has("method")) {
                    if (msg.has("id") && pendingRootsRequestId != null
                            && pendingRootsRequestId.equals(msg.get("id").asText())) {
                        pendingRootsRequestId = null;
                        session.handleRootsResponse(msg);
                    }
                    continue;
                }

                String method = msg.get("method").asText("");
                boolean isNotification = !msg.has("id");
                JsonNode id = isNotification ? null : msg.get("id");

                switch (method) {
                    case "initialize" -> {
                        JsonNode result = session.handleInitialize(msg);
                        writeResult(result, id);
                    }
                    case "notifications/initialized" -> {
                        if (session.clientSupportsRoots()) {
                            pendingRootsRequestId = sendRootsRequest();
                        }
                    }
                    case "notifications/roots/list_changed" -> {
                        if (session.clientSupportsRoots()) {
                            pendingRootsRequestId = sendRootsRequest();
                        }
                    }
                    case "ping" -> writeResult(JsonUtils.MAPPER.createObjectNode(), id);
                    case "tools/list" -> {
                        JsonNode result = session.handleToolsList();
                        writeResult(result, id);
                    }
                    case "tools/call" -> {
                        String toolName = msg.at("/params/name").asText("");
                        JsonNode args = msg.at("/params/arguments");
                        if (args == null || args.isMissingNode() || args.isNull()) {
                            args = JsonUtils.MAPPER.createObjectNode();
                        }
                        if (!args.isObject()) {
                            writeResponse(-32602, "Tool arguments must be a JSON object", id);
                            continue;
                        }
                        JsonNode result = session.handleToolsCall(toolName, args);
                        writeResult(result, id);
                    }
                    default -> {
                        if (!isNotification) {
                            writeResponse(-32601, "Method not found: " + method, id);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("MCP stdio loop terminated: " + e.getMessage());
        } finally {
            ProtocolLogger.close();
        }
        System.err.println("MCP Server stopped.");
    }

    private String sendRootsRequest() {
        String id = "javabytes-roots-" + rootsRequestCounter.incrementAndGet();
        ObjectNode request = JsonUtils.MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", "roots/list");
        send(request);
        return id;
    }

    private void writeResult(JsonNode result, JsonNode id) {
        ObjectNode resp = JsonUtils.MAPPER.createObjectNode();
        resp.put("jsonrpc", "2.0");
        if (id != null) resp.set("id", id);
        resp.set("result", result);
        send(resp);
    }

    private void writeResponse(int code, String message, JsonNode id) {
        ObjectNode resp = JsonUtils.MAPPER.createObjectNode();
        resp.put("jsonrpc", "2.0");
        if (id != null) resp.set("id", id);
        ObjectNode error = JsonUtils.MAPPER.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        resp.set("error", error);
        send(resp);
    }

    private void send(JsonNode message) {
        String line = message.toString();
        ProtocolLogger.log("-->", line);
        out.println(line);
        out.flush();
    }
}