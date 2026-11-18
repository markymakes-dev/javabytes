package com.javabytes.mcp.schema.types;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.JsonUtils;

/**
 * Renders {@link ToolSpec} to MCP wire format (tools/list entry).
 * Replaces the instanceof chain in {@code McpServerMain.toolDescriptor} (lines 412-456).
 * Single source of truth for schema wire rendering.
 */
public class SchemaRenderer {

    /**
     * Render a tool to its MCP wire format descriptor.
     */
    public ObjectNode render(Tool tool) {
        ToolSpec spec = tool.spec();
        ObjectNode toolNode = JsonUtils.MAPPER.createObjectNode();
        toolNode.put("name", tool.getName());
        toolNode.put("description", spec.description());
        toolNode.put("title", spec.title());

        ObjectNode schema = JsonUtils.MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = JsonUtils.MAPPER.createObjectNode();

        Tool.JsonSchema input = tool.getInputSchema();
        for (java.util.Map.Entry<String, Object> e : input.getProperties().entrySet()) {
            ObjectNode prop = JsonUtils.MAPPER.createObjectNode();
            SchemaType type = SchemaType.fromToolSchema(e.getValue());
            prop.put("type", type.wireName());

            // Add description if present
            String desc = getDescription(e.getValue());
            if (desc != null) {
                prop.put("description", desc);
            }

            // Add constraints for number types
            if (type == SchemaType.INTEGER || type == SchemaType.NUMBER) {
                addNumberConstraints(e.getValue(), prop);
            }

            properties.set(e.getKey(), prop);
        }
        schema.set("properties", properties);

        ArrayNode required = JsonUtils.MAPPER.createArrayNode();
        for (String r : input.getRequired()) {
            required.add(r);
        }
        schema.set("required", required);

        toolNode.set("inputSchema", schema);

        // Issue #19: derived, not declared — one universal envelope schema
        // (ADR 0008) serves every tool, mirroring the catalog-derived
        // philosophy of ADR 0006. Tool-specific payload keys remain
        // permitted via additionalProperties.
        toolNode.set("outputSchema", outputEnvelopeSchema());

        ObjectNode annotations = JsonUtils.MAPPER.createObjectNode();
        annotations.put("title", spec.title());
        annotations.put("readOnlyHint", spec.readOnlyHint());
        annotations.put("destructiveHint", spec.destructiveHint());
        annotations.put("idempotentHint", spec.idempotentHint());
        annotations.put("openWorldHint", spec.openWorldHint());
        toolNode.set("annotations", annotations);

        return toolNode;
    }

    /**
     * The ADR-0008 envelope as a machine-checkable contract: success
     * (boolean, required), error and message (optional strings). Richer
     * tool-specific payloads are permitted beyond these keys.
     */
    public static ObjectNode outputEnvelopeSchema() {
        ObjectNode schema = JsonUtils.MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = JsonUtils.MAPPER.createObjectNode();
        properties.set("success", JsonUtils.MAPPER.createObjectNode().put("type", "boolean"));
        properties.set("error", JsonUtils.MAPPER.createObjectNode().put("type", "string"));
        properties.set("message", JsonUtils.MAPPER.createObjectNode().put("type", "string"));
        schema.set("properties", properties);
        schema.set("required", JsonUtils.MAPPER.createArrayNode().add("success"));
        schema.put("additionalProperties", true);
        return schema;
    }

    private String getDescription(Object schema) {
        if (schema instanceof com.javabytes.mcp.tools.Tool.StringSchema s) {
            return s.getDescription();
        } else if (schema instanceof com.javabytes.mcp.tools.Tool.BoolSchema b) {
            return b.getDescription();
        } else if (schema instanceof com.javabytes.mcp.tools.Tool.NumberSchema n) {
            return n.getDescription();
        }
        return null;
    }

    private void addNumberConstraints(Object schema, ObjectNode prop) {
        if (schema instanceof com.javabytes.mcp.tools.Tool.NumberSchema n) {
            if (n.getMinimum() != null) prop.put("minimum", n.getMinimum());
            if (n.getMaximum() != null) prop.put("maximum", n.getMaximum());
        }
    }
}