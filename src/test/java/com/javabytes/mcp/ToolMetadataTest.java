package com.javabytes.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.javabytes.mcp.config.FeatureConfiguration;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ToolMetadata;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ADR 0006 consistency gate: the derived catalog and the registered tools
 * must agree, every spec must be honest, and anything uncataloged must fail
 * closed — disabled, never advertised, with a loud startup warning.
 */
class ToolMetadataTest {

    private static List<Tool> tools;

    @BeforeAll
    static void deriveCatalogFromTheRegisteredTools() {
        tools = McpServerMain.declaredTools();
        ToolMetadata.derive(tools);
    }

    @AfterAll
    static void restoreShippedCatalog() {
        ToolMetadata.derive(McpServerMain.declaredTools());
    }

    // --- consistency gate (the primary CI check) ---

    @Test
    void everyRegisteredToolHasExactlyOneCatalogEntry() {
        Set<String> declared = new HashSet<>();
        for (Tool tool : tools) {
            assertTrue(declared.add(tool.getName()), "registered twice: " + tool.getName());
            ToolSpec spec = tool.spec();
            assertNotNull(spec, tool.getName() + " declares no spec");
            assertEquals(tool.getName(), spec.name(),
                    tool.getName() + "'s spec declares a different name");
            assertTrue(ToolMetadata.isKnown(tool.getName()),
                    tool.getName() + " is registered but uncataloged (would fail closed)");
        }
        assertEquals(declared, ToolMetadata.catalog().keySet(),
                "the catalog contains entries that map to no registered tool");
    }

    @Test
    void everyEntryHasNonBlankTitleDescriptionAndCategory() {
        for (var entry : ToolMetadata.catalog().entrySet()) {
            String name = entry.getKey();
            ToolSpec spec = entry.getValue();
            assertFalse(spec.title().isBlank(), name + " has a blank title");
            assertFalse(spec.description().isBlank(), name + " has a blank description");
            assertFalse(spec.category().isBlank(), name + " has a blank category");
            assertTrue(ToolMetadata.KNOWN_CATEGORIES.contains(spec.category()),
                    name + " uses unknown category " + spec.category());
        }
    }

    // --- honesty of the default-on set ---

    @Test
    void optInCategoriesStayOffByDefaultAndCapabilitiesStayOn() {
        for (String category : ToolMetadata.DEFAULT_DISABLED_CATEGORIES) {
            for (String name : ToolMetadata.toolsIn(category)) {
                assertFalse(ToolMetadata.get(name).enabledByDefault(),
                        name + " should be opt-in");
            }
        }
        assertTrue(ToolMetadata.get("read_text_file").enabledByDefault());
        assertTrue(ToolMetadata.get("query_database").enabledByDefault());
        assertTrue(ToolMetadata.get("analyze_springboot_project").enabledByDefault());
        assertFalse(ToolMetadata.get("translate_javascript").enabledByDefault());

        // generate_database stays on by default (ADR 0005: its confirm-gate
        // and collision refusal are the safety mechanisms), now honestly
        // annotated as destructive rather than read-only.
        ToolSpec generateDatabase = ToolMetadata.get("generate_database");
        assertTrue(generateDatabase.enabledByDefault());
        assertFalse(generateDatabase.readOnlyHint());
        assertTrue(generateDatabase.destructiveHint());
    }

    @Test
    void hintsStayConsistent() {
        for (var entry : ToolMetadata.catalog().entrySet()) {
            ToolSpec spec = entry.getValue();
            if (spec.readOnlyHint()) {
                assertFalse(spec.destructiveHint(),
                        entry.getKey() + " cannot be both read-only and destructive");
            }
        }
        assertTrue(ToolMetadata.get("read_text_file").readOnlyHint());
        assertTrue(ToolMetadata.get("delete_file").destructiveHint());
        assertTrue(ToolMetadata.get("query_database").openWorldHint());
        assertFalse(ToolMetadata.get("backup_file").idempotentHint());
    }

    // --- fail closed (ADR 0006) ---

    @Test
    void unknownToolNamesHaveNoCatalogEntryAndAreDisabled() {
        assertNull(ToolMetadata.get("something_new"));
        assertFalse(ToolMetadata.isKnown("something_new"));
        assertFalse(FeatureConfiguration.parse(null, null).isEnabled("something_new"));
        // Even "enable everything" cannot resurrect an uncataloged tool.
        assertFalse(FeatureConfiguration.parse("all", null).isEnabled("something_new"));
    }

    @Test
    void registeredToolWithoutSpecFailsClosedAndWarns() {
        Tool ghost = new Tool() {
            @Override public String getName() { return "ghost_tool"; }
            @Override public ToolSpec spec() { return null; }
            @Override public JsonNode execute(JsonNode params) { return null; }
            @Override public JsonSchema getInputSchema() { return new JsonSchema(); }
        };
        List<Tool> broken = new ArrayList<>(tools);
        broken.add(ghost);

        String warning = deriveCapturingStderr(broken);
        assertFalse(ToolMetadata.isKnown("ghost_tool"), "a spec-less tool must not be cataloged");
        assertFalse(FeatureConfiguration.parse(null, null).isEnabled("ghost_tool"));
        assertFalse(FeatureConfiguration.parse("all", null).isEnabled("ghost_tool"));
        assertTrue(warning.contains("registered but uncataloged: ghost_tool"), warning);
    }

    @Test
    void specNameMismatchFailsClosedAndWarns() {
        Tool mislabeled = new Tool() {
            @Override public String getName() { return "actual_name"; }
            @Override public ToolSpec spec() {
                return new ToolSpec("other_name", "Other", "A spec whose name disagrees.",
                        "files", true, true, false, true, false);
            }
            @Override public JsonNode execute(JsonNode params) { return null; }
            @Override public JsonSchema getInputSchema() { return new JsonSchema(); }
        };
        List<Tool> broken = new ArrayList<>(tools);
        broken.add(mislabeled);

        String warning = deriveCapturingStderr(broken);
        assertFalse(ToolMetadata.isKnown("actual_name"));
        assertFalse(ToolMetadata.isKnown("other_name"));
        assertFalse(FeatureConfiguration.parse("all", null).isEnabled("actual_name"));
        assertTrue(warning.contains("registered but uncataloged: actual_name"), warning);
    }

    /**
     * Derives the catalog from {@code broken} while capturing stderr, then
     * restores the shipped catalog — the catalog is global JVM state.
     */
    private static String deriveCapturingStderr(List<Tool> broken) {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            ToolMetadata.derive(broken);
        } finally {
            System.setErr(originalErr);
            ToolMetadata.derive(McpServerMain.declaredTools());
        }
        return captured.toString(StandardCharsets.UTF_8);
    }
}
