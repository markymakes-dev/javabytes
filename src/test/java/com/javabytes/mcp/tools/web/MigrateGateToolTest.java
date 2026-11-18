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

class MigrateGateToolTest {

    private final MigrateGateTool tool = new MigrateGateTool();

    private static final String FULL_CASES = """
            {"fn":"add","args":[1,2],"result":3,"throws":null,"trap":["undefined_null"]}
            {"fn":"firstChar","args":["a\\uD83D\\uDC4Db"],"result":"a","throws":null,"trap":["utf16_units"]}
            {"fn":"add","args":[-0,0],"result":{"__minusZero":true},"throws":null,"trap":["number_format"]}
            {"fn":"sortNums","args":[[10,9,1]],"result":[1,9,10],"throws":null,"trap":["sort_order"]}
            {"fn":"later","args":["2026-01-02"],"result":"2026-01-02","throws":null,"trap":["date_parse"]}
            """;

    @Test
    void requiresProject() {
        JsonNode out = tool.execute(JsonUtils.MAPPER.createObjectNode());
        assertEquals("MISSING_PARAM", out.path("error").asText(), out.toString());
    }

    @Test
    void rejectsNonMavenProject(@TempDir Path root) {
        JsonNode out = tool.execute(JsonUtils.MAPPER.createObjectNode()
                .put("project", root.toString()));
        assertEquals("NOT_A_MAVEN_PROJECT", out.path("error").asText(), out.toString());
    }

    @Test
    void proveOnlyWhenNoTestGiven(@TempDir Path root) throws Exception {
        writePom(root);
        JsonNode out = tool.execute(JsonUtils.MAPPER.createObjectNode()
                .put("project", root.toString()));
        assertTrue(out.path("success").asBoolean(), out.toString());
        assertEquals("PROVE_ONLY", out.path("verdict").asText(), out.toString());
        assertTrue(out.path("prove_pass").asBoolean(), out.toString());
    }

    @Test
    void trapCoverageCountsAllFive(@TempDir Path root) throws Exception {
        writePom(root);
        Path cases = root.resolve("cases.jsonl");
        Files.writeString(cases, FULL_CASES);

        JsonNode out = tool.execute(JsonUtils.MAPPER.createObjectNode()
                .put("project", root.toString())
                .put("cases", cases.toString()));

        assertTrue(out.path("success").asBoolean(), out.toString());
        assertEquals(5, out.path("cases_total").asInt(), out.toString());
        assertTrue(out.path("traps_pass").asBoolean(), out.toString());
        for (JsonNode t : out.path("trap_coverage")) {
            assertTrue(t.path("covered").asBoolean(), t.toString());
        }
    }

    @Test
    void verdictIsProveOnlyWithoutTestClass(@TempDir Path root) throws Exception {
        writePom(root);
        Path cases = root.resolve("cases.jsonl");
        Files.writeString(cases, """
                {"fn":"add","args":[1,2],"result":3,"throws":null,"trap":["undefined_null"]}
                """);

        JsonNode out = tool.execute(JsonUtils.MAPPER.createObjectNode()
                .put("project", root.toString())
                .put("cases", cases.toString()));

        assertTrue(out.path("success").asBoolean(), out.toString());
        // Verdict ladder: no test class → the Gate tier cannot run, even with cases.
        assertEquals("PROVE_ONLY", out.path("verdict").asText(), out.toString());
        assertTrue(!out.path("traps_pass").asBoolean(), out.toString());
    }

    /**
     * Full E2E: a tiny generated project with a hand-written port + replay
     * test runs the real gate (mvn test-compile + mvn test). Gated behind
     * JAVABYTES_RUN_MVN_TESTS=1 because it shells out to Maven.
     */
    @Test
    void gateRunsFullMavenProjectEndToEnd(@TempDir Path root) throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("JAVABYTES_RUN_MVN_TESTS")),
                "Set JAVABYTES_RUN_MVN_TESTS=1 to run live Maven gate tests");

        writePortProject(root);
        Path cases = root.resolve("m.jsonl");
        Files.writeString(cases, FULL_CASES);

        JsonNode out = tool.execute(JsonUtils.MAPPER.createObjectNode()
                .put("project", root.toString())
                .put("test", "MReplayTest")
                .put("cases", cases.toString()));

        assertTrue(out.path("success").asBoolean(), out.toString());
        assertEquals("GATE_PASS", out.path("verdict").asText(), out.toString());
        assertTrue(out.path("prove_pass").asBoolean(), out.toString());
        assertTrue(out.path("gate_pass").asBoolean(), out.toString());
        assertTrue(out.path("traps_pass").asBoolean(), out.toString());
    }

    /** Minimal pom: JUnit 5 only; surefire picks up MReplayTest. */
    private static void writePom(Path root) throws Exception {
        Files.writeString(root.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>test</groupId>
                  <artifactId>gate-probe</artifactId>
                  <version>0.0.1</version>
                  <properties>
                    <maven.compiler.source>21</maven.compiler.source>
                    <maven.compiler.target>21</maven.compiler.target>
                  </properties>
                </project>
                """);
    }

    /** Generated-style project: port class + replay test + pom WITH junit (as real generated projects carry). */
    private static void writePortProject(Path root) throws Exception {
        Files.writeString(root.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>test</groupId>
                  <artifactId>gate-probe</artifactId>
                  <version>0.0.1</version>
                  <properties>
                    <maven.compiler.source>21</maven.compiler.source>
                    <maven.compiler.target>21</maven.compiler.target>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>org.junit.jupiter</groupId>
                      <artifactId>junit-jupiter</artifactId>
                      <version>5.10.2</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                  <build>
                    <plugins>
                      <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-surefire-plugin</artifactId>
                        <version>3.2.5</version>
                      </plugin>
                    </plugins>
                  </build>
                </project>
                """);
        Path main = root.resolve("src").resolve("main").resolve("java");
        Files.createDirectories(main);
        Files.writeString(main.resolve("M.java"), """
                import java.util.ArrayList;
                import java.util.Collections;
                import java.util.List;

                public class M {
                    public double add(double a, double b) { return a + b; }
                    public String firstChar(String s) {
                        if (s == null) { throw new IllegalArgumentException("null"); }
                        return s.substring(0, 1);
                    }
                    public List<Integer> sortNums(List<Integer> xs) {
                        List<Integer> out = new ArrayList<>(xs);
                        Collections.sort(out);
                        return out;
                    }
                    public String later(String x) { return x; }
                }
                """);
        Path test = root.resolve("src").resolve("test").resolve("java");
        Files.createDirectories(test);
        Files.writeString(test.resolve("MReplayTest.java"), """
                import org.junit.jupiter.api.Test;
                import java.nio.file.*;
                import java.util.*;

                public class MReplayTest {
                    static final String PORT_CLASS = "M";
                    static final Path CASES = Path.of("m.jsonl");

                    static Object port() throws Exception {
                        return Class.forName(PORT_CLASS).getDeclaredConstructor().newInstance();
                    }

                    static List<String> caseLines() throws Exception {
                        return Files.readAllLines(CASES).stream().filter(l -> !l.isBlank()).toList();
                    }

                    @Test
                    public void replays() throws Exception {
                        Object p = port();
                        int ran = 0;
                        for (String line : caseLines()) {
                            org.junit.jupiter.api.Assertions.assertTrue(line.contains("\\"fn\\""));
                            ran++;
                        }
                        org.junit.jupiter.api.Assertions.assertTrue(ran >= 5, "cases must replay");
                    }
                }
                """);
    }
}
