package com.javabytes.mcp;

import com.javabytes.mcp.utils.ToolDescriptions;
import com.javabytes.mcp.utils.ToolMetadata;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolMetadataTest {

    @Test
    void catalogCoversEveryRegisteredTool() {
        assertEquals(73, ToolMetadata.catalog().size());
    }

    @Test
    void everyEntryHasTitleCategoryAndDescription() {
        for (var entry : ToolMetadata.catalog().entrySet()) {
            String name = entry.getKey();
            ToolMetadata.Metadata metadata = entry.getValue();

            assertNotNull(metadata.title(), name);
            assertFalse(metadata.title().isBlank(), name + " has a blank title");
            assertFalse(metadata.category().isBlank(), name + " has a blank category");
            assertTrue(ToolMetadata.categories().contains(metadata.category()),
                    name + " uses unknown category " + metadata.category());
            assertFalse(ToolDescriptions.describe(name).endsWith("(JavaBytes tool)"),
                    name + " is missing an explicit description");
        }
    }

    @Test
    void categoriesAreBalancedAndDefaultToCapabilities() {
        Set<String> enabled = new HashSet<>();
        for (var entry : ToolMetadata.catalog().entrySet()) {
            if (entry.getValue().enabledByDefault()) {
                enabled.add(entry.getKey());
            }
        }
        assertEquals(43, enabled.size());
        assertTrue(enabled.contains("read_text_file"));
        assertTrue(enabled.contains("query_database"));
        assertTrue(enabled.contains("analyze_schema_normalization"));
        assertTrue(enabled.contains("analyze_javascript"));
        assertTrue(enabled.contains("javafx_export_image"));
        assertTrue(enabled.contains("analyze_springboot_project"));
        assertFalse(enabled.contains("translate_javascript"));

        for (String category : Set.of(ToolMetadata.UI, ToolMetadata.DESIGN,
                ToolMetadata.CODEGEN, ToolMetadata.SPRINGBOOT)) {
            for (String name : ToolMetadata.toolsIn(category)) {
                assertFalse(ToolMetadata.get(name).enabledByDefault(),
                        name + " should be opt-in");
            }
        }
    }

    @Test
    void readOnlyAndDestructiveHintsAreConsistent() {
        for (var entry : ToolMetadata.catalog().entrySet()) {
            ToolMetadata.Metadata metadata = entry.getValue();
            if (metadata.readOnly()) {
                assertFalse(metadata.destructive(),
                        entry.getKey() + " cannot be both read-only and destructive");
            }
        }
        assertTrue(ToolMetadata.get("read_text_file").readOnly());
        assertTrue(ToolMetadata.get("delete_file").destructive());
        assertTrue(ToolMetadata.get("query_database").openWorld());
        assertFalse(ToolMetadata.get("backup_file").idempotent());
    }

    @Test
    void unknownToolsFallBackSafely() {
        ToolMetadata.Metadata metadata = ToolMetadata.get("something_new");
        assertEquals("Something New", metadata.title());
        assertTrue(metadata.enabledByDefault());
        assertFalse(ToolMetadata.isKnown("something_new"));
    }
}
