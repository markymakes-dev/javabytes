package com.javabytes.mcp.prompts;

import com.javabytes.mcp.McpServerMain;
import com.javabytes.mcp.utils.ToolMetadata;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #18 drift regression: every tool name a crew prompt references must
 * exist in the catalog. The templates reference tools ONLY inside backticks
 * (client-side tools such as the question UI are deliberately written
 * without them), so the drift check is deterministic: extract every
 * snake_case token from `backticked` spans and require it to be a known
 * tool, mirroring the catalog-derived instruction philosophy (ADR 0006) —
 * a rename or removal can never silently break the crew pack.
 */
class CrewPromptsDriftTest {

    private static final Pattern BACKTICK_SPAN = Pattern.compile("`([^`]+)`");
    private static final Pattern TOOL_NAME = Pattern.compile("^[a-z][a-z0-9_]*$");

    @BeforeAll
    static void deriveCatalog() {
        ToolMetadata.derive(McpServerMain.declaredTools());
    }

    @Test
    void everyBacktickedToolNameExistsInCatalog() {
        List<String> failures = new java.util.ArrayList<>();
        for (PromptDefinition prompt : CrewPrompts.definitions()) {
            Set<String> referenced = backtickedToolNames(prompt.template());
            for (String tool : referenced) {
                if (!ToolMetadata.isKnown(tool)) {
                    failures.add(prompt.name() + " references unknown tool: " + tool);
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void promptsReferenceTheCorePipelineTools() {
        Set<String> all = new LinkedHashSet<>();
        for (PromptDefinition prompt : CrewPrompts.definitions()) {
            all.addAll(backtickedToolNames(prompt.template()));
        }
        // The crew pack must keep teaching the core pipeline by name.
        for (String required : List.of("analyze_javascript", "migrate_plan",
                "translate_javascript", "migrate_record", "migrate_replay",
                "migrate_gate", "migrate_report")) {
            assertTrue(all.contains(required),
                    "crew pack must reference " + required + "; catalog names drifted?");
        }
    }

    private Set<String> backtickedToolNames(String template) {
        Set<String> names = new LinkedHashSet<>();
        Matcher spans = BACKTICK_SPAN.matcher(template);
        while (spans.find()) {
            String span = spans.group(1).trim();
            if (TOOL_NAME.matcher(span).matches()) {
                names.add(span);
            }
        }
        return names;
    }
}
