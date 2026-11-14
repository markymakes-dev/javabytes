package com.javabytes.mcp.tools;

/**
 * A tool's self-declared catalog entry (ADR 0006): what it is called, what it
 * does, which feature category owns it, whether it is on by default, and its
 * MCP wire annotations.
 *
 * <p>Every {@link Tool} returns its own spec from {@link Tool#spec()}; the
 * startup catalog in {@code ToolMetadata} is derived from these specs. There
 * is no parallel string-keyed catalog to fall out of sync with.</p>
 *
 * @param name             the tool name; must equal {@link Tool#getName()}
 * @param title            human-readable title shown to clients
 * @param description      what the tool does, shown to the model choosing it
 * @param category         feature category (a plain string for now)
 * @param enabledByDefault whether the tool is registered without configuration
 * @param readOnlyHint     MCP annotation: does not modify its environment
 * @param destructiveHint  MCP annotation: may overwrite or delete state
 * @param idempotentHint   MCP annotation: repeated calls leave the same state
 * @param openWorldHint    MCP annotation: interacts with an external entity
 */
public record ToolSpec(
        String name,
        String title,
        String description,
        String category,
        boolean enabledByDefault,
        boolean readOnlyHint,
        boolean destructiveHint,
        boolean idempotentHint,
        boolean openWorldHint) {
}
