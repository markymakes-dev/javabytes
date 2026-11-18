package com.javabytes.mcp.tools.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.config.AppConfig;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.PathValidator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * java-anything step 4 (ADR 0015): the tiered migration gate. Runs on the
 * generated Maven project:
 *
 * <ul>
 *   <li><b>Prove</b> — {@code mvn test-compile} succeeds (the port exists and
 *       compiles). Same tier as the 1.1.0 pipeline.</li>
 *   <li><b>Gate</b> — the generated ReplayTest passes under
 *       {@code mvn test -Dtest=<Unit>ReplayTest}: 100% of recorded cases
 *       replayed against the port.</li>
 *   <li><b>Traps</b> — the cases file carries at least one recorded case for
 *       each of the five ADR 0016 traps.</li>
 * </ul>
 *
 * <p>The verdict is honest about which tiers ran: no cases file means no trap
 * evidence; a compile failure means the Gate cannot even start.</p>
 */
public final class MigrateGateTool implements Tool {

    static final String[] TRAPS = MigrateRecordTool.TRAPS;
    private static final int MAX_OUTPUT_CHARS = 2000;

    public MigrateGateTool() {
    }

    @Override
    public String getName() {
        return "migrate_gate";
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "migrate_gate",
                "Run the tiered migration gate on a ported project",
                "Run the tiered java-anything gate on a generated Maven project: Prove "
                        + "(mvn test-compile), Gate (mvn test on the generated ReplayTest — "
                        + "100% of recorded cases must replay), and Traps (all five semantic "
                        + "traps have recorded cases). Returns a per-tier verdict. "
                        + "Inspiration: rust-anything (MIT).",
                "analysis",
                true,
                false,
                false,
                true,
                false);
    }

    @Override
    public JsonNode execute(JsonNode params) {
        String projectParam = params.has("project") ? params.get("project").asText().trim() : "";
        String testClass = params.has("test") ? params.get("test").asText().trim() : "";
        String casesParam = params.has("cases") ? params.get("cases").asText().trim() : "";
        String mvnParam = params.has("mvn") ? params.get("mvn").asText().trim() : "";
        int timeoutSeconds = params.has("timeout_seconds") ? params.get("timeout_seconds").asInt(600) : 600;

        if (projectParam.isEmpty()) {
            return ErrorHelper.error("MISSING_PARAM",
                    "Provide 'project' — the generated Maven project root (with pom.xml)");
        }
        timeoutSeconds = Math.min(Math.max(timeoutSeconds, 30), 1800);

        Path project = Path.of(projectParam);
        if (!PathValidator.isAllowed(project)) {
            return ErrorHelper.error("PATH_NOT_ALLOWED", "project not allowed: " + projectParam
                    + ". Allowed roots: " + AppConfig.describeAllowedDirectories());
        }
        if (!Files.isRegularFile(project.resolve("pom.xml"))) {
            return ErrorHelper.error("NOT_A_MAVEN_PROJECT",
                    "No pom.xml under " + project.toAbsolutePath() + "; migrate_gate runs "
                            + "Maven tiers against a generated project.");
        }

        String mvn = mvnParam.isEmpty()
                ? System.getenv().getOrDefault("JAVABYTES_MVN", "")
                : mvnParam;
        if (mvn.isEmpty()) {
            // Prefer a wrapper if the project carries one, else plain mvn.
            if (Files.isRegularFile(project.resolve("mvnw.cmd"))) {
                mvn = project.resolve("mvnw.cmd").toString();
            } else if (Files.isRegularFile(project.resolve("mvnw"))) {
                mvn = project.resolve("mvnw").toString();
            } else {
                mvn = "mvn";
            }
        }
        String mvnVersion = probeMvn(project, mvn);
        if (mvnVersion == null) {
            return ErrorHelper.error("MVN_NOT_FOUND",
                    "Maven is required for the gate tiers but '" + mvn + "' is not runnable. "
                            + "Install Maven, use a project mvnw wrapper, or set JAVABYTES_MVN / "
                            + "the 'mvn' parameter. The gate is never silently skipped.");
        }

        // Cases + trap coverage (Traps tier).
        Path casesFile = casesParam.isEmpty() ? null : Path.of(casesParam);
        if (casesFile != null && !PathValidator.isAllowed(casesFile)) {
            return ErrorHelper.error("PATH_NOT_ALLOWED", "cases not allowed: " + casesParam
                    + ". Allowed roots: " + AppConfig.describeAllowedDirectories());
        }
        Map<String, Integer> trapCoverage = new LinkedHashMap<>();
        for (String trap : TRAPS) {
            trapCoverage.put(trap, 0);
        }
        int caseCount = 0;
        if (casesFile != null && Files.isRegularFile(casesFile)) {
            try {
                for (String line : Files.readAllLines(casesFile)) {
                    if (line.isBlank()) {
                        continue;
                    }
                    JsonNode c = JsonUtils.MAPPER.readTree(line.trim());
                    caseCount++;
                    c.path("trap").forEach(t -> trapCoverage.merge(t.asText(), 1, Integer::sum));
                }
            } catch (Exception e) {
                return ErrorHelper.error("INVALID_CASES", "cases file unreadable: " + e.getMessage());
            }
        }

        boolean trapsCovered = trapCoverage.values().stream().allMatch(n -> n > 0);

        // Tier 1: PROVE (test-compile).
        CommandResult prove = runMvn(project, mvn, List.of("-B", "-q", "test-compile"), timeoutSeconds);
        boolean provePass = prove.exitCode == 0;

        // Tier 2: GATE (the replay test).
        boolean gatePass = false;
        String gateTail = "";
        if (provePass && !testClass.isEmpty()) {
            CommandResult gate = runMvn(project, mvn,
                    List.of("-B", "test", "-Dtest=" + testClass), timeoutSeconds);
            gatePass = gate.exitCode == 0;
            gateTail = gate.exitCode == 0 ? "" : tail(gate.output);
        }

        // Verdict, honest about which tiers ran.
        String verdict;
        if (!provePass) {
            verdict = "PROVE_FAILED";
        } else if (testClass.isEmpty()) {
            verdict = "PROVE_ONLY";
        } else if (!gatePass) {
            verdict = "GATE_FAILED";
        } else if (casesFile == null) {
            verdict = "GATE_PASSED_WITHOUT_TRAP_EVIDENCE";
        } else if (!trapsCovered) {
            verdict = "GATE_PASSED_TRAPS_INCOMPLETE";
        } else {
            verdict = "GATE_PASS";
        }

        ObjectNode out = JsonUtils.MAPPER.createObjectNode();
        out.put("success", true);
        out.put("tool", "migrate_gate");
        out.put("project", project.toAbsolutePath().toString());
        out.put("mvn", mvn);
        out.put("mvn_version", mvnVersion);
        out.put("verdict", verdict);
        out.put("prove_pass", provePass);
        out.put("gate_pass", gatePass);
        out.put("traps_pass", trapsCovered);
        out.put("cases_total", caseCount);
        out.put("test_class", testClass);
        ArrayNode tiers = out.putArray("tiers");
        ObjectNode t1 = tiers.addObject();
        t1.put("tier", "prove");
        t1.put("pass", provePass);
        t1.put("detail", provePass ? "mvn test-compile succeeded" : tail(prove.output));
        ObjectNode t2 = tiers.addObject();
        t2.put("tier", "gate");
        t2.put("pass", gatePass);
        t2.put("detail", testClass.isEmpty() ? "no test class given"
                : (gatePass ? testClass + " passed" : gateTail));
        ObjectNode t3 = tiers.addObject();
        t3.put("tier", "traps");
        t3.put("pass", trapsCovered);
        t3.put("detail", casesFile == null ? "no cases file given"
                : (trapsCovered ? "all five traps recorded" : "missing trap cases"));
        ArrayNode trapNode = out.putArray("trap_coverage");
        for (Map.Entry<String, Integer> e : trapCoverage.entrySet()) {
            ObjectNode t = trapNode.addObject();
            t.put("trap", e.getKey());
            t.put("cases", e.getValue());
            t.put("covered", e.getValue() > 0);
        }

        List<String> limitations = new ArrayList<>();
        limitations.add("Recorded cases are the ceiling: the gate proves the port matches "
                + "what THIS machine's Node did for the recorded inputs, nothing more.");
        limitations.add("JS error classes do not map 1:1: recorded throws pass the Gate "
                + "when the port throws any exception.");
        if (verdict.equals("GATE_PASSED_WITHOUT_TRAP_EVIDENCE")) {
            limitations.add("No cases file was provided: the Gate tier has no behavioral "
                    + "evidence behind it — pass 'cases' for the full verdict.");
        }
        ArrayNode lim = out.putArray("limitations");
        limitations.forEach(lim::add);
        return out;
    }

    private static String probeMvn(Path project, String mvn) {
        CommandResult r = runMvn(project, mvn, List.of("--version"), 60);
        if (r.exitCode != 0) {
            return null;
        }
        String first = r.output.strip().lines().findFirst().orElse("");
        return first.isBlank() ? "unknown" : first;
    }

    private static CommandResult runMvn(Path cwd, String mvn, List<String> args, int timeoutSeconds) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        List<String> command = new ArrayList<>();
        if (windows && (mvn.equalsIgnoreCase("mvn") || mvn.endsWith(".cmd"))) {
            command.add("cmd.exe");
            command.add("/c");
        }
        command.add(mvn);
        command.addAll(args);
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(cwd.toFile());
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            Thread reader = pipe(process.getInputStream(), output, 64L * 1024);
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                return new CommandResult(-1, "Maven timed out after " + timeoutSeconds + "s");
            }
            reader.join(2000);
            return new CommandResult(process.exitValue(), output.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandResult(-1, "Maven run interrupted");
        } catch (Exception e) {
            return new CommandResult(-1, e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private static Thread pipe(java.io.InputStream stream, StringBuilder sink, long cap) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                char[] buf = new char[4096];
                int n;
                while ((n = r.read(buf)) != -1) {
                    sink.append(buf, 0, n);
                    if (sink.length() > cap) {
                        break;
                    }
                }
            } catch (Exception ignored) {
                // stream ended or process killed
            }
        }, "javabytes-gate-mvn");
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static String tail(String s) {
        String cleaned = s.strip();
        if (cleaned.length() <= MAX_OUTPUT_CHARS) {
            return cleaned;
        }
        return "…" + cleaned.substring(cleaned.length() - MAX_OUTPUT_CHARS);
    }

    private record CommandResult(int exitCode, String output) {
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("project", new Tool.StringSchema()
                .description("Generated Maven project root (must contain pom.xml)"));
        schema.prop("test", new Tool.StringSchema()
                .description("Replay test class for the Gate tier, e.g. StoreReplayTest "
                        + "(omit to run the Prove tier only)"));
        schema.prop("cases", new Tool.StringSchema()
                .description("Optional .jsonl cases file for the Traps tier "
                        + "(trap coverage evidence)"));
        schema.prop("mvn", new Tool.StringSchema()
                .description("Maven command (default: project mvnw wrapper, else JAVABYTES_MVN, "
                        + "else 'mvn')"));
        schema.prop("timeout_seconds", new Tool.NumberSchema()
                .integer()
                .minimum(30)
                .maximum(1800)
                .description("Per-Maven-run timeout in seconds (default 600)"));
        return schema;
    }
}
