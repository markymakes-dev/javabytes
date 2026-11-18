package com.javabytes.mcp.tools.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.javabytes.mcp.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrateReportToolTest {

    private final MigrateReportTool tool = new MigrateReportTool();

    private static final String CASES = """
            {"fn":"add","args":[1,2],"result":3,"throws":null,"trap":["undefined_null"]}
            {"fn":"add","args":[-0,0],"result":{"__minusZero":true},"throws":null,"trap":["number_format"]}
            {"fn":"sortNums","args":[[10,9,1]],"result":[1,9,10],"throws":null,"trap":["sort_order"]}
            {"fn":"sortNums","args":[null],"result":null,"throws":{"name":"TypeError","message":"x"},"trap":["undefined_null"]}
            {"fn":"later","args":["2026-01-02"],"result":"2026-01-02","throws":null,"trap":["date_parse"]}
            """;

    @Test
    void requiresProject() {
        JsonNode out = tool.execute(JsonUtils.MAPPER.createObjectNode());
        assertEquals("MISSING_PARAM", out.path("error").asText(), out.toString());
    }

    @Test
    void rejectsMissingProject(@TempDir Path root) {
        JsonNode out = tool.execute(JsonUtils.MAPPER.createObjectNode()
                .put("project", root.resolve("nope").toString()));
        assertEquals("PATH_NOT_FOUND", out.path("error").asText(), out.toString());
    }

    @Test
    void refusesReportWithoutCases(@TempDir Path root) {
        JsonNode out = tool.execute(JsonUtils.MAPPER.createObjectNode()
                .put("project", root.toString()));
        assertEquals("NO_CASES", out.path("error").asText(), out.toString());
    }

    @Test
    void rejectsNonMdReportPath(@TempDir Path root) throws Exception {
        writeProject(root);
        JsonNode out = tool.execute(JsonUtils.MAPPER.createObjectNode()
                .put("project", root.toString())
                .put("report_path", root.resolve("REPORT.txt").toString()));
        assertEquals("INVALID_REPORT_PATH", out.path("error").asText(), out.toString());
    }

    @Test
    void rendersUnitsTableAndEvidence(@TempDir Path root) throws Exception {
        writeProject(root);

        JsonNode out = tool.execute(JsonUtils.MAPPER.createObjectNode()
                .put("project", root.toString())
                .put("title", "Shiftee"));

        assertTrue(out.path("success").asBoolean(), out.toString());
        assertEquals(1, out.path("units").asInt(), out.toString());
        assertEquals(1, out.path("replay_tests_found").asInt(), out.toString());
        assertEquals(1, out.path("gate_results_found").asInt(), out.toString());
        assertEquals(1, out.path("gate_pass").asInt(), out.toString());

        String report = Files.readString(root.resolve("MIGRATION-REPORT.md"));
        assertTrue(report.contains("# Migration report — Shiftee"), report);
        assertTrue(report.contains("| Unit | Functions | Cases | Throws |"), report);
        assertTrue(report.contains("add, later, sortNums"), report);
        assertTrue(report.contains("4/5"), report);  // traps covered n/5
        assertTrue(report.contains("GATE_PASS"), report);
        assertTrue(report.contains("**missing**") == false, "ReplayTest exists, not missing");
        assertTrue(report.contains("rust-anything"), report);
    }

    @Test
    void flagsMissingReplayTest(@TempDir Path root) throws Exception {
        // cases only, no test file, no gate result
        Path cases = root.resolve("cases");
        Files.createDirectories(cases);
        Files.writeString(cases.resolve("solo.jsonl"), CASES);

        JsonNode out = tool.execute(JsonUtils.MAPPER.createObjectNode()
                .put("project", root.toString()));

        assertTrue(out.path("success").asBoolean(), out.toString());
        assertEquals(0, out.path("replay_tests_found").asInt(), out.toString());
        String report = Files.readString(root.resolve("MIGRATION-REPORT.md"));
        assertTrue(report.contains("**missing**"), report);
        assertTrue(report.contains("not run"), report);
    }

    private static void writeProject(Path root) throws Exception {
        Path cases = root.resolve("cases");
        Files.createDirectories(cases);
        Files.writeString(cases.resolve("m.jsonl"), CASES);

        Path test = root.resolve("src").resolve("test").resolve("java");
        Files.createDirectories(test);
        Files.writeString(test.resolve("MReplayTest.java"), "class MReplayTest {}\n");

        Path target = root.resolve("target");
        Files.createDirectories(target);
        Files.writeString(target.resolve("migrate-gate-m.json"),
                """
                        {"unit":"m","verdict":"GATE_PASS","prove_pass":true,"gate_pass":true,"traps_pass":false}
                        """);
    }
}
