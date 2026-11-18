package com.javabytes.mcp.tools.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.javabytes.mcp.utils.JsonUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrateRecordToolTest {

    private final MigrateRecordTool tool = new MigrateRecordTool();

    @Test
    void requiresPath() {
        JsonNode out = tool.execute(JsonUtils.MAPPER.createObjectNode());
        assertEquals("MISSING_PARAM", out.path("error").asText(), out.toString());
    }

    @Test
    void rejectsMissingFile(@TempDir Path root) {
        JsonNode out = tool.execute(params(root.resolve("nope.js").toString()));
        assertEquals("PATH_NOT_FOUND", out.path("error").asText(), out.toString());
    }

    @Test
    void rejectsDirectory(@TempDir Path root) {
        JsonNode out = tool.execute(params(root.toString()));
        assertEquals("PATH_NOT_FOUND", out.path("error").asText(), out.toString());
    }

    @Test
    void rejectsTypescriptSource(@TempDir Path root) throws Exception {
        Path ts = root.resolve("store.ts");
        Files.writeString(ts, "export const a = 1;\n");
        JsonNode out = tool.execute(params(ts.toString()));
        assertEquals("NOT_RUNNABLE_UNDER_NODE", out.path("error").asText(), out.toString());
    }

    @Test
    void rejectsUnknownTrap(@TempDir Path root) throws Exception {
        Path js = root.resolve("m.js");
        Files.writeString(js, "export const a = 1;\n");
        JsonNode out = tool.execute(params(js.toString())
                .put("traps", "made_up_trap"));
        assertEquals("UNKNOWN_TRAP", out.path("error").asText(), out.toString());
    }

    @Test
    void rejectsNonJsonlOutput(@TempDir Path root) throws Exception {
        Path js = root.resolve("m.js");
        Files.writeString(js, "export const a = 1;\n");
        JsonNode out = tool.execute(params(js.toString())
                .put("output", root.resolve("cases").toString()));
        assertEquals("INVALID_OUTPUT", out.path("error").asText(), out.toString());
    }

    @Test
    void driverContainsTrapPoolAndManifest() {
        String driver = MigrateRecordTool.buildDriver();
        assertTrue(driver.contains("__manifest"), "driver must emit a manifest line");
        assertTrue(driver.contains("pathToFileURL"), "driver must import via file URL");
        for (String trap : MigrateRecordTool.TRAPS) {
            assertTrue(driver.contains("'" + trap + "'"), "driver must seed trap " + trap);
        }
        assertTrue(driver.contains("__minusZero"), "-0 must survive serialization");
        assertTrue(driver.contains("__undefined"), "undefined must survive serialization");
    }

    @Test
    void recordsLiveModuleUnderNode(@TempDir Path root) throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("JAVABYTES_RUN_NODE_TESTS")),
                "Set JAVABYTES_RUN_NODE_TESTS=1 to run live Node recording tests");

        Path module = root.resolve("math.js");
        Files.writeString(module, """
                export function add(a, b) { return a + b; }
                export function firstChar(s) { return s ? s[0] : undefined; }
                export function sortNums(arr) { return [...arr].sort(); }
                export async function later(x) { return x; }
                """);

        JsonNode out = tool.execute(params(module.toString()));

        assertTrue(out.path("success").asBoolean(), out.toString());
        assertTrue(out.path("node_version").asText().startsWith("v"), out.toString());
        assertTrue(out.path("functions_found").asInt() >= 4, out.toString());
        assertEquals(4, out.path("functions_recorded").asInt(), out.toString());
        assertTrue(out.path("cases_written").asInt() >= 10, out.toString());

        Path casesFile = Path.of(out.path("output").asText());
        assertTrue(Files.exists(casesFile), "cases file must exist: " + casesFile);
        List<String> lines = Files.readAllLines(casesFile);
        assertTrue(lines.size() == out.path("cases_written").asInt(), out.toString());

        boolean sawAdd = false;
        boolean sawThrow = false;
        for (String line : lines) {
            JsonNode c = JsonUtils.MAPPER.readTree(line);
            assertTrue(c.has("fn"), line);
            if (c.path("fn").asText().equals("add")) {
                sawAdd = true;
                assertTrue(c.path("args").isArray(), line);
            }
            if (c.hasNonNull("throws") && c.path("throws").isObject()) {
                sawThrow = true;
            }
        }
        assertTrue(sawAdd, "add() must be recorded");
        assertTrue(sawThrow, "at least one throw must be recorded (trap inputs)");

        boolean allTrapsCovered = true;
        for (JsonNode t : out.path("trap_coverage")) {
            if (t.path("cases").asInt() == 0) {
                allTrapsCovered = false;
            }
        }
        assertTrue(allTrapsCovered, "all five traps must have recorded cases: " + out);
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode params(String path) {
        return JsonUtils.MAPPER.createObjectNode().put("path", path);
    }
}
