package com.javabytes.mcp.prompts;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptRegistryTest {

    @Test
    void crewPackRegistersFourPromptsInPipelineOrder() {
        PromptRegistry registry = new PromptRegistry();
        registry.registerAll(CrewPrompts.definitions());

        assertEquals(4, registry.getCount());
        assertEquals(List.of("recon", "port", "verify", "report"),
                registry.list().stream().map(PromptDefinition::name).toList());
    }

    @Test
    void getReturnsDefinitionAndRenderSubstitutesArguments() {
        PromptRegistry registry = new PromptRegistry();
        registry.registerAll(CrewPrompts.definitions());

        PromptDefinition recon = registry.get("recon");
        assertEquals("recon", recon.name());
        assertFalse(recon.description().isBlank());
        assertFalse(recon.arguments().isEmpty());
        assertTrue(recon.arguments().stream().anyMatch(PromptArgument::required));

        String rendered = registry.render("recon", Map.of("source_path", "C:/dev/app"));
        assertFalse(rendered.contains("{{source_path}}"), "supplied argument must be substituted");
        assertTrue(rendered.contains("C:/dev/app"));
        // optional argument left unset renders empty, not a literal placeholder
        assertFalse(rendered.contains("{{target}}"));
    }

    @Test
    void unknownPlaceholderStaysVisibleAndUnknownPromptThrows() {
        PromptRegistry registry = new PromptRegistry();
        registry.register(new PromptDefinition("tiny", "d", List.of(), "Hello {{nobody_defined_me}}"));

        String rendered = registry.render("tiny", Map.of());
        assertTrue(rendered.contains("{{nobody_defined_me}}"),
                "unknown placeholders must stay loud so template mistakes are visible");

        assertThrows(IllegalArgumentException.class, () -> registry.render("ghost", Map.of()));
        assertFalse(registry.hasPrompt("ghost"));
    }
}
