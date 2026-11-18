package com.javabytes.mcp.tools.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.javabytes.mcp.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrateReplayToolTest {

    private final MigrateReplayTool tool = new MigrateReplayTool();

    private static final String CASES = """
            {"fn":"add","args":[1,2],"result":3,"throws":null,"trap":["undefined_null"]}
            {"fn":"add","args":[-0,0],"result":{"__minusZero":true},"throws":null,"trap":["number_format"]}
            {"fn":"add","args":[],"result":{"__nan":true},"throws":null,"trap":["undefined_null"]}
            {"fn":"sortNums","args":[[10,9,1]],"result":[1,9,10],"throws":null,"trap":["sort_order"]}
            {"fn":"sortNums","args":[null],"result":null,"throws":{"name":"TypeError","message":"x"},"trap":["undefined_null"]}
            {"fn":"firstChar","args":["a\\uD83D\\uDC4Db"],"result":"a","throws":null,"trap":["utf16_units"]}
            {"fn":"later","args":["2026-01-02"],"result":"2026-01-02","throws":null,"trap":["date_parse"]}
            """;

    @Test
    void requiresCasesAndClass(@TempDir Path root) {
        JsonNode out = tool.execute(JsonUtils.MAPPER.createObjectNode());
        assertEquals("MISSING_PARAM", out.path("error").asText(), out.toString());
    }

    @Test
    void rejectsMissingCasesFile(@TempDir Path root) {
        JsonNode out = tool.execute(JsonUtils.MAPPER.createObjectNode()
                .put("cases", root.resolve("nope.jsonl").toString())
                .put("class", "Port"));
        assertEquals("PATH_NOT_FOUND", out.path("error").asText(), out.toString());
    }

    @Test
    void rejectsEmptyCases(@TempDir Path root) throws Exception {
        Path cases = root.resolve("empty.jsonl");
        Files.writeString(cases, "");
        JsonNode out = tool.execute(JsonUtils.MAPPER.createObjectNode()
                .put("cases", cases.toString())
                .put("class", "Port"));
        assertEquals("EMPTY_CASES", out.path("error").asText(), out.toString());
    }

    @Test
    void rejectsNonJavaOutput(@TempDir Path root) throws Exception {
        Path cases = root.resolve("m.jsonl");
        Files.writeString(cases, CASES);
        JsonNode out = tool.execute(JsonUtils.MAPPER.createObjectNode()
                .put("cases", cases.toString())
                .put("class", "Port")
                .put("output", root.resolve("out").toString()));
        assertEquals("INVALID_OUTPUT", out.path("error").asText(), out.toString());
    }

    @Test
    void generatesReplayTestContent(@TempDir Path root) throws Exception {
        Path cases = root.resolve("m.jsonl");
        Files.writeString(cases, CASES);
        Path output = root.resolve("MReplayTest.java");

        JsonNode out = tool.execute(JsonUtils.MAPPER.createObjectNode()
                .put("cases", cases.toString())
                .put("class", "com.example.Port")
                .put("output", output.toString()));

        assertTrue(out.path("success").asBoolean(), out.toString());
        assertEquals(4, out.path("functions").size(), out.toString());
        assertEquals(7, out.path("cases_embedded").asInt(), out.toString());
        assertTrue(Files.exists(output), "test file must exist");

        String test = Files.readString(output);
        assertTrue(test.contains("package com.example;"), test);
        assertTrue(test.contains("class MReplayTest"), test);
        assertTrue(test.contains("PORT_CLASS = \"com.example.Port\""), test);
        assertTrue(test.contains("replays_add"), test);
        assertTrue(test.contains("replays_sortNums"), test);
        assertTrue(test.contains("assertThrows"), "throws-cases must assertThrows");
        assertTrue(test.contains("Class.forName"), "port must load reflectively");
        assertTrue(test.contains("__minusZero") || test.contains("minusZero") || test.contains("doubleToLongBits"),
                "minus-zero canonicalization must appear");
    }

    @Test
    void writesTestIntoProjectWhenProjectGiven(@TempDir Path root) throws Exception {
        Path cases = root.resolve("m.jsonl");
        Files.writeString(cases, CASES);

        JsonNode out = tool.execute(JsonUtils.MAPPER.createObjectNode()
                .put("cases", cases.toString())
                .put("class", "Port")
                .put("project", root.toString()));

        assertTrue(out.path("success").asBoolean(), out.toString());
        Path written = Path.of(out.path("test_written").asText());
        assertTrue(Files.exists(written), out.toString());
        assertTrue(written.toString().contains("src" + java.io.File.separator + "test"
                + java.io.File.separator + "java"), out.toString());
    }
}
