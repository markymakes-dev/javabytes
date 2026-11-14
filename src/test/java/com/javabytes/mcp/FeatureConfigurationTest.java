package com.javabytes.mcp;

import com.javabytes.mcp.config.FeatureConfiguration;
import com.javabytes.mcp.utils.ToolMetadata;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureConfigurationTest {

    @BeforeAll
    static void deriveCatalog() {
        // ADR 0006: the catalog is derived from the registered tools; the
        // feature configuration resolves names and categories against it.
        ToolMetadata.derive(McpServerMain.declaredTools());
    }


    @Test
    void defaultsEnableCapabilitiesAndDisableGenerators() {
        FeatureConfiguration features = FeatureConfiguration.parse(null, null);

        assertTrue(features.isEnabled("read_text_file"));
        assertTrue(features.isEnabled("query_database"));
        assertTrue(features.isEnabled("project_diagnostic"));
        assertTrue(features.isEnabled("analyze_springboot_project"));

        assertFalse(features.isEnabled("springboot_generate_entity"));
        assertFalse(features.isEnabled("generate_ui_code"));
        assertFalse(features.isEnabled("generate_code"));
        assertFalse(features.isEnabled("design_tokens"));
    }

    @Test
    void toolNameCanEnableAnOptInTool() {
        FeatureConfiguration features = FeatureConfiguration.parse("springboot_generate_entity", null);

        assertTrue(features.isEnabled("springboot_generate_entity"));
        assertFalse(features.isEnabled("springboot_generate_service"));
    }

    @Test
    void toolNameCanDisableADefaultTool() {
        FeatureConfiguration features = FeatureConfiguration.parse(null, "delete_file");

        assertFalse(features.isEnabled("delete_file"));
        assertTrue(features.isEnabled("write_file"));
    }

    @Test
    void categoryEnablesEveryToolInIt() {
        FeatureConfiguration features = FeatureConfiguration.parse("springboot", null);

        assertTrue(features.isEnabled("springboot_generate_entity"));
        assertTrue(features.isEnabled("springboot_generate_docker"));
        assertFalse(features.isEnabled("generate_ui_code"));
    }

    @Test
    void categoryCanDisableDefaultTools() {
        FeatureConfiguration features = FeatureConfiguration.parse(null, "javafx");

        assertFalse(features.isEnabled("javafx_init_canvas"));
        assertTrue(features.isEnabled("read_text_file"));
    }

    @Test
    void disabledByNameBeatsEnabledByCategory() {
        FeatureConfiguration features = FeatureConfiguration.parse(
                "springboot", "springboot_generate_entity");

        assertFalse(features.isEnabled("springboot_generate_entity"));
        assertTrue(features.isEnabled("springboot_generate_service"));
    }

    @Test
    void enabledByNameBeatsDisabledByCategory() {
        FeatureConfiguration features = FeatureConfiguration.parse(
                "javafx_export_image", "javafx");

        assertTrue(features.isEnabled("javafx_export_image"));
        assertFalse(features.isEnabled("javafx_draw_canvas"));
    }

    @Test
    void allEnablesEverythingAndNamesStillWin() {
        FeatureConfiguration features = FeatureConfiguration.parse("all", "delete_file");

        assertTrue(features.isEnabled("springboot_generate_entity"));
        assertTrue(features.isEnabled("generate_ui_code"));
        assertFalse(features.isEnabled("delete_file"));
    }

    @Test
    void allInDisableListTurnsEverythingOffExceptEnabledNames() {
        FeatureConfiguration features = FeatureConfiguration.parse("read_text_file", "all");

        assertTrue(features.isEnabled("read_text_file"));
        assertFalse(features.isEnabled("query_database"));
        assertFalse(features.isEnabled("springboot_generate_entity"));
    }

    @Test
    void starIsAnAliasForAll() {
        assertTrue(FeatureConfiguration.parse("*", null).isEnabled("springboot_generate_test"));
    }

    @Test
    void listsAcceptCommasSemicolonsAndWhitespace() {
        FeatureConfiguration features = FeatureConfiguration.parse(
                "springboot, codegen;design", null);

        assertTrue(features.isEnabled("springboot_generate_test"));
        assertTrue(features.isEnabled("generate_tests"));
        assertTrue(features.isEnabled("design_tokens"));
        assertFalse(features.isEnabled("generate_ui_code"));
    }

    @Test
    void unknownEntriesAreIgnored() {
        FeatureConfiguration features = FeatureConfiguration.parse(
                "not_a_tool", "also_not_a_tool");

        assertTrue(features.isEnabled("read_text_file"));
        assertFalse(features.isEnabled("springboot_generate_entity"));
    }
}
