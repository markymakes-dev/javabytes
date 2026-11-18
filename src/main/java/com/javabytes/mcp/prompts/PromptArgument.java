package com.javabytes.mcp.prompts;

/**
 * One argument of a prompt template (MCP prompts/list entry shape).
 *
 * @param name        argument name, substituted into the template as {{name}}
 * @param description what the client should fill in
 * @param required    prompts/get fails with invalid params when missing
 */
public record PromptArgument(String name, String description, boolean required) {
}
