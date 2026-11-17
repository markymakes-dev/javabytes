package com.javabytes.mcp.tools.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigratePlanToolTest {

    private final MigratePlanTool tool = new MigratePlanTool();

    @Test
    void plansProjectInDependencyWaves(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("id.js"), """
                export function uuid() { return crypto.randomUUID(); }
                """);
        Files.createDirectories(root.resolve("utils"));
        Files.writeString(root.resolve("utils/time.js"), """
                export function nowIso() { return new Date().toISOString(); }
                """);
        Files.createDirectories(root.resolve("core"));
        Files.writeString(root.resolve("core/store.js"), """
                import { uuid } from '../id.js';
                import { nowIso } from '../utils/time.js';
                export function createShift(data) { return { id: uuid(), createdAt: nowIso(), ...data }; }
                """);
        Files.createDirectories(root.resolve("ui"));
        Files.writeString(root.resolve("ui/App.jsx"), """
                import React from 'react';
                import { createShift } from '../core/store.js';
                export function App() { return <main onClick={() => createShift({})}>{document.title}</main>; }
                """);

        JsonNode plan = plan(root);

        assertTrue(plan.path("success").asBoolean(), plan.toString());
        assertEquals(4, plan.path("units_count").asInt(), plan.toString());
        assertEquals(0, unit(plan, "id").path("wave").asInt());
        assertEquals(0, unit(plan, "time").path("wave").asInt());
        assertEquals(1, unit(plan, "store").path("wave").asInt(), plan.toString());
        assertEquals(2, unit(plan, "App").path("wave").asInt(), plan.toString());
        assertEquals("portable", unit(plan, "store").path("kind").asText());
        assertEquals("ui", unit(plan, "App").path("kind").asText());
        assertTrue(unit(plan, "store").path("dependsOn").toString().contains("id")
                        && unit(plan, "store").path("dependsOn").toString().contains("time"),
                plan.toString());
        assertTrue(unit(plan, "App").path("dependsOn").toString().contains("store"), plan.toString());
        assertTrue(plan.path("decisions").size() >= 5);
        assertEquals("needs-owner", plan.path("decisions").get(0).path("status").asText());
        assertEquals(1, plan.path("decisions").get(0).path("n").asInt());
    }

    @Test
    void flagsDependencyCycleAndStillPlacesEveryUnit(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("a.js"), "import { b } from './b.js';\nexport const a = 1;\n");
        Files.writeString(root.resolve("b.js"), "import { a } from './a.js';\nexport const b = 2;\n");

        JsonNode plan = plan(root);

        assertTrue(plan.path("project_risks").toString().contains("cycle"), plan.toString());
        int placed = 0;
        for (JsonNode unit : plan.path("units")) {
            if (unit.path("wave").asInt() >= 0) {
                placed++;
            }
        }
        assertEquals(2, placed, plan.toString());
    }

    @Test
    void writesMarkdownReport(@TempDir Path root, @TempDir Path out) throws Exception {
        Files.writeString(root.resolve("main.js"), "export function main() { return 1; }\n");
        Path report = out.resolve("MIGRATION-PLAN.md");

        JsonNode plan = plan(root, report.toString());

        assertTrue(plan.path("report_written").asText().endsWith("MIGRATION-PLAN.md"), plan.toString());
        String md = Files.readString(report);
        assertTrue(md.contains("# Migration Plan"), md);
        assertTrue(md.contains("## Port order"), md);
        assertTrue(md.contains("needs-owner"), md);
        assertTrue(md.contains("rust-anything"), md);
    }

    @Test
    void rejectsDirectoryReportPath(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("main.js"), "export function main() { return 1; }\n");

        JsonNode plan = tool.execute(JsonUtils.MAPPER.createObjectNode()
                .put("path", root.toString())
                .put("report_path", root.resolve("reports").toString()));

        assertEquals("INVALID_REPORT_PATH", plan.path("error").asText(), plan.toString());
    }

    @Test
    void plansInlineCodeWithoutAPath() {
        JsonNode plan = tool.execute(JsonUtils.MAPPER.createObjectNode()
                .put("code", "export function add(a, b) { return a + b; }")
                .put("file_name", "math.js"));

        assertTrue(plan.path("success").asBoolean(), plan.toString());
        assertEquals(1, plan.path("units_count").asInt(), plan.toString());
        assertEquals("portable", plan.path("units").get(0).path("kind").asText(), plan.toString());
    }

    @Test
    void requiresPathOrCode() {
        JsonNode plan = tool.execute(JsonUtils.MAPPER.createObjectNode());
        assertEquals("MISSING_PARAM", plan.path("error").asText(), plan.toString());
    }

    private static JsonNode plan(Path root) {
        return plan(root, "");
    }

    private static JsonNode plan(Path root, String reportPath) {
        ObjectNode params = JsonUtils.MAPPER.createObjectNode();
        params.put("path", root.toString());
        if (!reportPath.isEmpty()) {
            params.put("report_path", reportPath);
        }
        return new MigratePlanTool().execute(params);
    }

    private static JsonNode unit(JsonNode plan, String name) {
        for (JsonNode unit : plan.path("units")) {
            if (unit.path("unit").asText().equals(name)) {
                return unit;
            }
        }
        throw new AssertionError("unit not found in plan: " + name);
    }
}
