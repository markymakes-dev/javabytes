package com.javabytes.mcp.prompts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The prompt registry (issue #18): prompts defined as data in one place,
 * mirroring the tool registry's shape. Adding a fifth crew prompt is a
 * single edit here plus its test. Prompts are documentation — always
 * advertised, never feature-gated — and rendering is pure text
 * substitution: no logic, no tool calls, no I/O.
 */
public final class PromptRegistry {

    private final Map<String, PromptDefinition> prompts = new LinkedHashMap<>();

    public void register(PromptDefinition prompt) {
        prompts.put(prompt.name(), prompt);
        // stdout is reserved for protocol responses; log to stderr
        System.err.println("Registered prompt: " + prompt.name());
    }

    public void registerAll(List<PromptDefinition> definitions) {
        definitions.forEach(this::register);
    }

    /** All registered prompts, in registration order. */
    public List<PromptDefinition> list() {
        return new ArrayList<>(prompts.values());
    }

    public PromptDefinition get(String name) {
        return prompts.get(name);
    }

    public boolean hasPrompt(String name) {
        return prompts.containsKey(name);
    }

    public int getCount() {
        return prompts.size();
    }

    /**
     * Render a prompt's template with argument substitution: every
     * {{name}} placeholder is replaced by the argument's value; optional
     * arguments that were not supplied render as an empty string. Unknown
     * placeholders are left visible so template mistakes are loud.
     */
    public String render(String name, Map<String, String> args) {
        PromptDefinition prompt = prompts.get(name);
        if (prompt == null) {
            throw new IllegalArgumentException("Unknown prompt: " + name);
        }
        Map<String, String> safeArgs = args == null ? Map.of() : args;
        String text = prompt.template();
        for (PromptArgument argument : prompt.arguments()) {
            String placeholder = "{{" + argument.name() + "}}";
            String value = safeArgs.getOrDefault(argument.name(), "");
            text = text.replace(placeholder, value);
        }
        return text;
    }
}
