package com.javabytes.mcp.prompts;

import java.util.List;

/**
 * A prompt defined as data (issue #18): name, description, argument list and
 * the template text. Templates are static documents with {{argument}}
 * substitution — they contain no logic and call no tools themselves; the
 * client's agent executes the recipe they describe.
 *
 * @param name        prompt name (prompts/get key)
 * @param description human-readable summary shown in prompts/list
 * @param arguments   template arguments, in declaration order
 * @param template    the full prompt text with {{placeholders}}
 */
public record PromptDefinition(
        String name,
        String description,
        List<PromptArgument> arguments,
        String template) {

    public PromptDefinition {
        arguments = List.copyOf(arguments);
    }
}
