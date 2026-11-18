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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * java-anything step 5: a human-readable migration report over a generated
 * project. Scans {@code cases/*.jsonl} (recorded evidence), the generated
 * {@code ReplayTest} sources, and any {@code target/migrate-gate-*.json}
 * results written by {@code migrate_gate}, and renders MIGRATION-REPORT.md.
 * The report is derived from recorded evidence only — it claims nothing the
 * recording cannot back.
 */
public final class MigrateReportTool implements Tool {

    public MigrateReportTool() {
    }

    @Override
    public String getName() {
        return "migrate_report";
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "migrate_report",
                "Render a migration report for a ported project",
                "Scan a generated Maven project for recorded cases (cases/*.jsonl), "
                        + "generated ReplayTest sources and migrate_gate results "
                        + "(target/migrate-gate-*.json), and render MIGRATION-REPORT.md: "
                        + "per-unit evidence table, trap coverage, gate verdicts and honest "
                        + "remaining work. Inspiration: rust-anything (MIT).",
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
        String reportParam = params.has("report_path") ? params.get("report_path").asText().trim() : "";
        String titleParam = params.has("title") ? params.get("title").asText().trim() : "";

        if (projectParam.isEmpty()) {
            return ErrorHelper.error("MISSING_PARAM",
                    "Provide 'project' — the generated project directory to report on");
        }

        Path project = Path.of(projectParam);
        if (!PathValidator.isAllowed(project)) {
            return ErrorHelper.error("PATH_NOT_ALLOWED", "project not allowed: " + projectParam
                    + ". Allowed roots: " + AppConfig.describeAllowedDirectories());
        }
        if (!Files.isDirectory(project)) {
            return ErrorHelper.error("PATH_NOT_FOUND", "Not a directory: " + projectParam);
        }

        String reportPath = reportParam.isEmpty()
                ? project.resolve("MIGRATION-REPORT.md").toString()
                : reportParam;
        if (!reportPath.endsWith(".md")) {
            return ErrorHelper.error("INVALID_REPORT_PATH",
                    "report_path must be a .md file path (e.g. '.../MIGRATION-REPORT.md'), "
                            + "not a directory or other extension.");
        }
        Path reportDest = Path.of(reportPath);
        if (!PathValidator.isAllowed(reportDest)) {
            return ErrorHelper.error("PATH_NOT_ALLOWED", "report_path not allowed: " + reportPath
                    + ". Allowed roots: " + AppConfig.describeAllowedDirectories());
        }

        Path casesDir = project.resolve("cases");
        Map<String, UnitRow> units = new LinkedHashMap<>();
        if (Files.isDirectory(casesDir)) {
            try (Stream<Path> files = Files.list(casesDir)) {
                files.filter(f -> f.getFileName().toString().endsWith(".jsonl")).sorted()
                        .forEach(f -> collectUnit(f, units));
            } catch (IOException e) {
                return ErrorHelper.error("MIGRATE_REPORT_ERROR",
                        "Error scanning cases: " + e.getMessage());
            }
        }
        if (units.isEmpty()) {
            return ErrorHelper.error("NO_CASES",
                    "No cases/*.jsonl found under " + project.toAbsolutePath()
                            + ". Run migrate_record first; a report without recorded evidence "
                            + "would be a claim, not a report.");
        }

        // Replay tests: src/test/java/**/*ReplayTest.java
        Map<String, Path> replayTests = new LinkedHashMap<>();
        Path testRoot = project.resolve("src").resolve("test").resolve("java");
        if (Files.isDirectory(testRoot)) {
            try (Stream<Path> walk = Files.walk(testRoot)) {
                walk.filter(f -> f.getFileName().toString().endsWith("ReplayTest.java"))
                        .forEach(f -> replayTests.put(replayKey(f), f));
            } catch (IOException e) {
                return ErrorHelper.error("MIGRATE_REPORT_ERROR",
                        "Error scanning tests: " + e.getMessage());
            }
        }

        // Gate results: target/migrate-gate-<unit>.json
        Map<String, JsonNode> gateResults = new LinkedHashMap<>();
        Path target = project.resolve("target");
        if (Files.isDirectory(target)) {
            try (Stream<Path> files = Files.list(target)) {
                files.filter(f -> f.getFileName().toString().startsWith("migrate-gate-")
                                && f.getFileName().toString().endsWith(".json"))
                        .sorted()
                        .forEach(f -> {
                            try {
                                JsonNode g = JsonUtils.MAPPER.readTree(f.toFile());
                                gateResults.put(g.path("unit").asText(
                                        unitName(f.getFileName().toString())), g);
                            } catch (Exception ignored) {
                                // unreadable gate result: skipped, noted in limitations
                            }
                        });
            } catch (IOException ignored) {
                // target missing or unreadable: gate section omitted
            }
        }

        String markdown = renderMarkdown(project, titleParam, units, replayTests, gateResults);
        try {
            if (reportDest.getParent() != null) {
                Files.createDirectories(reportDest.getParent());
            }
            Files.writeString(reportDest, markdown);
        } catch (IOException e) {
            return ErrorHelper.error("MIGRATE_REPORT_ERROR",
                    "Error writing report: " + e.getMessage());
        }

        long gatePass = gateResults.values().stream()
                .filter(g -> "GATE_PASS".equals(g.path("verdict").asText())).count();
        long replayPresent = units.keySet().stream().filter(u -> replayTests.containsKey(u)).count();

        ObjectNode out = JsonUtils.MAPPER.createObjectNode();
        out.put("success", true);
        out.put("tool", "migrate_report");
        out.put("project", project.toAbsolutePath().toString());
        out.put("units", units.size());
        out.put("replay_tests_found", replayPresent);
        out.put("gate_results_found", gateResults.size());
        out.put("gate_pass", gatePass);
        out.put("report_written", reportDest.toAbsolutePath().toString());
        ArrayNode lim = out.putArray("limitations");
        lim.add("The report is derived from recorded evidence only: cases not recorded "
                + "are behavior not proved.");
        lim.add("Gate results appear only for units whose migrate_gate output was saved "
                + "as target/migrate-gate-<unit>.json.");
        return out;
    }

    private static void collectUnit(Path casesFile, Map<String, UnitRow> units) {
        UnitRow row = new UnitRow();
        row.unit = unitName(casesFile.getFileName().toString());
        row.casesFile = casesFile;
        Map<String, Integer> traps = new LinkedHashMap<>();
        for (String trap : MigrateGateTool.TRAPS) {
            traps.put(trap, 0);
        }
        try {
            for (String line : Files.readAllLines(casesFile)) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode c = JsonUtils.MAPPER.readTree(line.trim());
                row.cases++;
                row.functions.add(c.path("fn").asText());
                if (c.hasNonNull("throws") && c.path("throws").isObject()) {
                    row.throwsCount++;
                }
                c.path("trap").forEach(t -> traps.merge(t.asText(), 1, Integer::sum));
            }
        } catch (Exception ignored) {
            row.risks.add("cases file unreadable in report scan");
        }
        row.traps = traps;
        units.put(row.unit, row);
    }

    /** 'StoreReplayTest' under …/store/… → 'store'; falls back to the file stem. */
    private static String replayKey(Path testFile) {
        String name = testFile.getFileName().toString().replaceFirst("ReplayTest\\.java$", "");
        return name.toLowerCase(java.util.Locale.ROOT);
    }

    private static String unitName(String fileName) {
        return fileName.replaceFirst("\\.jsonl$", "")
                .replaceFirst("^migrate-gate-", "");
    }

    private static String renderMarkdown(Path project, String title, Map<String, UnitRow> units,
                                         Map<String, Path> replayTests, Map<String, JsonNode> gates) {
        StringBuilder md = new StringBuilder();
        md.append("# Migration report")
                .append(title == null || title.isEmpty() ? "" : " — " + title)
                .append("\n\n");
        md.append("Project: `").append(project.toAbsolutePath()).append("`\n\n");
        md.append("Generated by `migrate_report` (java-anything by JavaBytes; ").append(java.time.LocalDate.now())
                .append("). Every number below comes from recorded evidence (ADR 0015): ")
                .append("behavior the cases do not cover is not proved.\n\n");

        md.append("## Units\n\n");
        md.append("| Unit | Functions | Cases | Throws | Traps covered | ReplayTest | Gate verdict |\n");
        md.append("|---|---|---|---|---|---|---|\n");
        for (UnitRow row : units.values()) {
            long covered = row.traps.values().stream().filter(n -> n > 0).count();
            String test = replayTests.containsKey(row.unit) ? "yes" : "**missing**";
            JsonNode gate = gates.get(row.unit);
            String verdict = gate == null ? "not run"
                    : gate.path("verdict").asText("unknown");
            md.append("| ").append(row.unit)
                    .append(" | ").append(row.functions.size())
                    .append(" | ").append(row.cases)
                    .append(" | ").append(row.throwsCount)
                    .append(" | ").append(covered).append("/").append(MigrateGateTool.TRAPS.length)
                    .append(" | ").append(test)
                    .append(" | ").append(verdict)
                    .append(" |\n");
        }

        md.append("\n## Per-unit evidence\n\n");
        for (UnitRow row : units.values()) {
            md.append("### `").append(row.unit).append("`\n\n");
            md.append("- Cases recorded: ").append(row.cases)
                    .append(" (throws recorded: ").append(row.throwsCount).append(")\n");
            md.append("- Functions: ").append(String.join(", ", row.functions)).append("\n");
            md.append("- Trap coverage:");
            for (Map.Entry<String, Integer> e : row.traps.entrySet()) {
                md.append(" `").append(e.getKey()).append("` ×").append(e.getValue());
            }
            md.append("\n");
            JsonNode gate = gates.get(row.unit);
            if (gate != null) {
                md.append("- Gate: `").append(gate.path("verdict").asText("unknown"))
                        .append("` (prove=").append(gate.path("prove_pass").asBoolean())
                        .append(", gate=").append(gate.path("gate_pass").asBoolean())
                        .append(", traps=").append(gate.path("traps_pass").asBoolean())
                        .append(")\n");
            } else {
                md.append("- Gate: not run (save migrate_gate output as ")
                        .append("`target/migrate-gate-").append(row.unit).append(".json` to include it)\n");
            }
            if (!row.risks.isEmpty()) {
                md.append("- Risks: ").append(String.join("; ", row.risks)).append("\n");
            }
            md.append("\n");
        }

        md.append("## Remaining work\n\n");
        md.append("1. Port any unit whose ReplayTest is missing (generate with ")
                .append("`migrate_replay`, then write the port).\n");
        md.append("2. Run `migrate_gate` per unit and save results under `target/` ")
                .append("so the report carries verdicts.\n");
        md.append("3. Fill the starter decisions from `migrate_plan` (they stay ")
                .append("needs-owner until the owner confirms them).\n");
        md.append("4. Remember the ceiling: recorded cases are evidence only for what ")
                .append("they record — floats, regexes, unrecorded inputs and side effects ")
                .append("are outside the Gate.\n\n");
        md.append("---\n\n*The port was written by a coding agent; JavaBytes supplies ")
                .append("planning, recording, replay and gating. Reference: ")
                .append("[rust-anything](https://github.com/erkamyaman/rust-anything) (MIT).*\n");
        return md.toString();
    }

    private static final class UnitRow {
        String unit;
        Path casesFile;
        int cases;
        int throwsCount;
        java.util.Set<String> functions = new java.util.TreeSet<>();
        Map<String, Integer> traps = new LinkedHashMap<>();
        List<String> risks = new ArrayList<>();
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("project", new Tool.StringSchema()
                .description("Generated project directory (contains cases/, optionally "
                        + "src/test/java/**/ReplayTest.java and target/migrate-gate-*.json)"));
        schema.prop("report_path", new Tool.StringSchema()
                .description("Optional .md output path (default: <project>/MIGRATION-REPORT.md; "
                        + "must end with .md)"));
        schema.prop("title", new Tool.StringSchema()
                .description("Optional report title suffix"));
        return schema;
    }
}
