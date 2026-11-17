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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * java-anything step 1 (ADR 0014): turn an analyzer inventory into a migration
 * plan — units, dependency waves (port order), risks, numbered starter
 * decisions and a vocabulary seed. The agent writes the port; this tool
 * supplies the order and the honest risk map (design: docs/java-anything.md).
 */
public class MigratePlanTool implements Tool {

    private static final Pattern RELATIVE_IMPORT = Pattern.compile(
            "(?:\\bimport\\s+(?:[^'\";]+?\\s+from\\s+)?|\\brequire\\s*\\(\\s*|\\bimport\\s*\\(\\s*)"
                    + "['\"](\\.{1,2}/[^'\"]+)['\"]");
    private static final String[] SOURCE_EXTENSIONS = {
            ".js", ".ts", ".tsx", ".mjs", ".cjs", ".jsx", ".vue", ".svelte" };

    @Override
    public String getName() { return "migrate_plan"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "migrate_plan",
                "Plan JS/TS to Java migration",
                "Build a java-anything migration plan for a JS/TS file or project: units, "
                        + "dependency-ordered waves, risks, numbered starter decisions and a vocabulary "
                        + "seed. Inspiration: rust-anything (MIT).",
                "analysis",
                true,
                false,
                false,
                true,
                false);
    }

    @Override
    public JsonNode execute(JsonNode params) {
        String code = params.has("code") && !params.get("code").isNull()
                ? params.get("code").asText()
                : null;
        String pathParam = params.has("path") ? params.get("path").asText().trim() : "";
        int maxFiles = params.has("max_files") ? params.get("max_files").asInt(500) : 500;
        String reportPath = params.has("report_path") ? params.get("report_path").asText().trim() : "";
        String projectName = params.has("name") ? params.get("name").asText().trim() : "";

        try {
            ObjectNode analysis;
            String rootPath;
            if (code != null) {
                String fileName = params.has("file_name")
                        ? params.get("file_name").asText("input.js")
                        : "input.js";
                analysis = JavaScriptAnalyzer.enrichSource(
                        JavaScriptAnalyzer.analyzeSource(code, fileName));
                rootPath = "";
            } else {
                if (pathParam.isEmpty()) {
                    return ErrorHelper.error("MISSING_PARAM",
                            "Provide either 'path' (file or directory) or 'code'");
                }
                Path path = Path.of(pathParam);
                if (!PathValidator.isAllowed(path)) {
                    return ErrorHelper.error("PATH_NOT_ALLOWED",
                            "Path not allowed: " + pathParam + ". Allowed roots: "
                                    + AppConfig.describeAllowedDirectories());
                }
                if (!Files.exists(path)) {
                    return ErrorHelper.error("PATH_NOT_FOUND", "Path not found: " + pathParam);
                }
                analysis = Files.isDirectory(path)
                        ? JavaScriptAnalyzer.analyzeProject(path, maxFiles)
                        : JavaScriptAnalyzer.enrichSource(
                                JavaScriptAnalyzer.analyzeSource(Files.readString(path), path.toString()));
                rootPath = analysis.path("root").asText(pathParam);
            }

            if (projectName.isEmpty()) {
                projectName = rootPath.isEmpty() ? "inline-source"
                        : Path.of(rootPath).getFileName().toString();
            }
            if (!rootPath.isEmpty() && !analysis.has("root")) {
                analysis.put("root", rootPath);
            }

            List<Unit> units = buildUnits(analysis);
            List<List<String>> waves = computeWaves(units);
            ObjectNode out = JsonUtils.MAPPER.createObjectNode();
            out.put("success", true);
            out.put("tool", "migrate_plan");
            out.put("project", projectName);
            out.put("root", rootPath);
            out.put("units_count", units.size());
            out.put("total_lines", analysis.path("total_lines").asInt(units.stream().mapToInt(u -> u.lines).sum()));

            ArrayNode unitsNode = out.putArray("units");
            for (Unit unit : units) {
                ObjectNode unitNode = unitsNode.addObject();
                unitNode.put("unit", unit.name);
                unitNode.put("path", unit.path);
                unitNode.put("lines", unit.lines);
                unitNode.put("kind", unit.kind);
                unitNode.put("wave", unit.wave);
                ArrayNode deps = unitNode.putArray("dependsOn");
                unit.dependsOn.forEach(deps::add);
                ArrayNode dependents = unitNode.putArray("dependedOnBy");
                unit.dependedOnBy.forEach(dependents::add);
                ArrayNode risks = unitNode.putArray("risks");
                unit.risks.forEach(risks::add);
            }

            ArrayNode wavesNode = out.putArray("waves");
            for (List<String> wave : waves) {
                ArrayNode w = wavesNode.addArray();
                wave.forEach(w::add);
            }

            ArrayNode decisions = out.putArray("decisions");
            starterDecisions(analysis, projectName).forEach(decisions::add);

            ArrayNode vocabulary = out.putArray("vocabulary");
            vocabularySeed(units, analysis).forEach(vocabulary::add);

            ArrayNode projectRisks = out.putArray("project_risks");
            projectRisks(units, analysis).forEach(projectRisks::add);

            ArrayNode limitations = out.putArray("limitations");
            limitations().forEach(limitations::add);

            if (!reportPath.isEmpty()) {
                if (!reportPath.endsWith(".md")) {
                    return ErrorHelper.error("INVALID_REPORT_PATH",
                            "report_path must be a .md file path (e.g. '.../MIGRATION-PLAN.md'), "
                                    + "not a directory. Directory paths are rejected to prevent "
                                    + "silent overwrites.");
                }
                Path reportDest = Path.of(reportPath);
                if (!PathValidator.isAllowed(reportDest)) {
                    return ErrorHelper.error("PATH_NOT_ALLOWED",
                            "report_path not allowed: " + reportPath + ". Allowed roots: "
                                    + AppConfig.describeAllowedDirectories());
                }
                if (reportDest.getParent() != null) {
                    Files.createDirectories(reportDest.getParent());
                }
                Files.writeString(reportDest, renderMarkdown(projectName, analysis, units, waves));
                out.put("report_written", reportDest.toAbsolutePath().toString());
            }

            return out;
        } catch (Exception e) {
            return ErrorHelper.error("MIGRATE_PLAN_ERROR",
                    "Error building migration plan: " + e.getMessage());
        }
    }

    // ========================================================================
    // Units, classification, dependency graph
    // ========================================================================

    private static final class Unit {
        String name;
        String path;
        int lines;
        String kind;
        int wave = -1;
        List<String> dependsOn = new ArrayList<>();
        List<String> dependedOnBy = new ArrayList<>();
        List<String> risks = new ArrayList<>();
        boolean inCycle;
    }

    private List<Unit> buildUnits(ObjectNode analysis) {
        List<Unit> units = new ArrayList<>();
        JsonNode files = analysis.path("files");
        List<ObjectNode> fileNodes = new ArrayList<>();
        files.forEach(f -> fileNodes.add((ObjectNode) f));

        if (fileNodes.isEmpty() && analysis.hasNonNull("file")) {
            // Single-source (inline or single file) mode: analyzeSource/enrichSource
            // emit the file's own facts at the top level instead of a files array.
            ObjectNode fileNode = JsonUtils.MAPPER.createObjectNode();
            fileNode.put("path", analysis.path("file").asText("input.js"));
            fileNode.put("lines", analysis.path("lines").asInt(0));
            fileNode.set("frameworks", analysis.path("frameworks"));
            fileNode.put("jsx", analysis.path("jsx").asBoolean(false));
            fileNode.set("browser_apis", analysis.path("browser_apis"));
            fileNodes.add(fileNode);
        }

        for (ObjectNode file : fileNodes) {
            Unit unit = new Unit();
            unit.path = file.path("path").asText();
            String stem = Path.of(unit.path).getFileName().toString().replaceFirst("\\.[^.]+$", "");
            unit.name = stem;
            unit.lines = file.path("lines").asInt();

            boolean hasFramework = file.path("frameworks").size() > 0;
            boolean hasJsx = file.path("jsx").asBoolean(false);
            boolean hasBrowserApis = file.path("browser_apis").size() > 0;
            if (hasFramework || hasJsx) {
                unit.kind = "ui";
            } else if (hasBrowserApis) {
                unit.kind = "mixed";
            } else {
                unit.kind = "portable";
            }
            units.add(unit);
        }

        // Dependency edges (in-project relative imports only).
        String root = analysis.path("root").asText("");
        Map<String, Unit> byPath = new HashMap<>();
        for (Unit unit : units) {
            byPath.put(unit.path, unit);
        }
        for (Unit unit : units) {
            Path abs = root.isEmpty() ? null : Path.of(root).resolve(unit.path);
            if (abs == null) {
                continue;
            }
            String source;
            try {
                source = Files.readString(abs);
            } catch (IOException | OutOfMemoryError e) {
                unit.risks.add("source not readable during dependency scan");
                continue;
            }
            Set<String> resolved = resolveRelativeImports(source, unit.path, byPath.keySet());
            for (String dep : resolved) {
                Unit target = byPath.get(dep);
                if (target != null && target != unit) {
                    unit.dependsOn.add(target.name);
                    target.dependedOnBy.add(unit.name);
                }
            }
        }

        for (Unit unit : units) {
            if (unit.kind.equals("ui")) {
                unit.risks.add("framework component: UI is not mechanically portable"
                        + " (UI strategy decision required)");
            } else if (unit.kind.equals("mixed")) {
                unit.risks.add("browser API usage mixed with logic: extract portable logic"
                        + " before porting");
            }
            if (unit.lines > 500) {
                unit.risks.add("large unit (" + unit.lines + " lines): split before porting");
            }
        }
        return units;
    }

    /** Resolve './x' style specifiers to in-project unit paths; ignore everything else. */
    private static Set<String> resolveRelativeImports(String source, String fromPath, Set<String> known) {
        Set<String> out = new LinkedHashSet<>();
        Matcher matcher = RELATIVE_IMPORT.matcher(source);
        // Files at the project root have no parent dir; resolve against the root itself.
        Path parent = Path.of(fromPath).getParent();
        Path base = parent == null ? Path.of("") : parent;
        while (matcher.find()) {
            String spec = matcher.group(1);
            String candidate = base.resolve(spec).normalize().toString().replace('\\', '/');
            String resolved = tryResolve(candidate, known);
            if (resolved != null) {
                out.add(resolved);
            }
        }
        return out;
    }

    private static String tryResolve(String candidate, Set<String> known) {
        if (known.contains(candidate)) {
            return candidate;
        }
        for (String ext : SOURCE_EXTENSIONS) {
            if (known.contains(candidate + ext)) {
                return candidate + ext;
            }
        }
        for (String ext : SOURCE_EXTENSIONS) {
            String index = candidate + "/index" + ext;
            if (known.contains(index)) {
                return index;
            }
        }
        return null;
    }

    // ========================================================================
    // Waves (Kahn topological order, deterministic tiebreaks)
    // ========================================================================

    private List<List<String>> computeWaves(List<Unit> units) {
        Map<String, Unit> byName = new HashMap<>();
        for (Unit unit : units) {
            Unit prev = byName.put(unit.name, unit);
            if (prev != null) {
                String warning = "duplicate unit name: " + unit.name
                        + " (edges by name are ambiguous)";
                prev.risks.add(warning);
                unit.risks.add(warning);
            }
        }
        List<List<String>> waves = new ArrayList<>();
        Set<String> placed = new LinkedHashSet<>();
        List<Unit> remaining = new ArrayList<>(units);
        int waveIndex = 0;

        while (!remaining.isEmpty()) {
            List<Unit> ready = new ArrayList<>();
            for (Unit unit : remaining) {
                boolean allPlaced = unit.dependsOn.stream()
                        .allMatch(dep -> {
                            Unit d = byName.get(dep);
                            return d == null || placed.contains(dep) || d.inCycle;
                        });
                if (allPlaced) {
                    ready.add(unit);
                }
            }
            if (ready.isEmpty()) {
                // Dependency cycle: break it deterministically, flag the units.
                Unit smallest = remaining.stream()
                        .min(Comparator.comparingInt((Unit u) -> u.lines).thenComparing(u -> u.path))
                        .orElseThrow();
                smallest.inCycle = true;
                smallest.risks.add("dependency cycle involving " + smallest.name
                        + ": port order within the cycle is unresolved");
                ready.add(smallest);
            }
            ready.sort(Comparator.comparingInt((Unit u) -> u.lines).thenComparing(u -> u.path));
            List<String> wave = new ArrayList<>();
            for (Unit unit : ready) {
                unit.wave = waveIndex;
                wave.add(unit.name);
                placed.add(unit.name);
            }
            remaining.removeAll(ready);
            waves.add(wave);
            waveIndex++;
        }
        return waves;
    }

    // ========================================================================
    // Plan content: decisions, vocabulary, risks, limitations
    // ========================================================================

    private List<ObjectNode> starterDecisions(ObjectNode analysis, String projectName) {
        List<ObjectNode> out = new ArrayList<>();
        int n = 1;
        out.add(decision(n++, "UI strategy",
                "javafx is the default UI strategy (ADR 0013); keep-frontend remains available"
                        + " for webapps (ADR 0004). Confirm which target this migration produces.",
                "ADR 0013 / ADR 0004"));
        out.add(decision(n++, "Identifier strategy",
                "Preserve string IDs from the JS source (client-assigned) unless the source"
                        + " uses numeric IDs; the entity template only emits @GeneratedValue for"
                        + " numeric IDs.",
                "Shiftee round-2 evidence; entity template contract"));
        out.add(decision(n++, "Date and time representation",
                "Dates/times arrive as strings from JS; decide per entity whether to keep"
                        + " strings (faithful) or use java.time (typed).",
                "js-to-java mapping table"));
        out.add(decision(n++, "Package naming",
                "Java package root for the ported project (default com." + slug(projectName) + ").",
                "translate_webapp default"));
        out.add(decision(n++, "Coexistence boundary",
                "Webapp migrations keep the original frontend and take over routes one unit"
                        + " at a time (strangler, keep-frontend); desktop migrations port behind"
                        + " the REST API seam.",
                "docs/java-anything.md; ADR 0004; ADR 0013"));
        return out;
    }

    private static ObjectNode decision(int n, String title, String detail, String basis) {
        ObjectNode d = JsonUtils.MAPPER.createObjectNode();
        d.put("n", n);
        d.put("decision", title);
        d.put("detail", detail);
        d.put("basis", basis);
        d.put("status", "needs-owner");
        return d;
    }

    private static String slug(String name) {
        String s = name.replaceAll("[^A-Za-z0-9]+", "").toLowerCase();
        return s.isEmpty() ? "migrated" : s;
    }

    private List<ObjectNode> vocabularySeed(List<Unit> units, ObjectNode analysis) {
        List<ObjectNode> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Unit unit : units) {
            if (!seen.add(unit.name)) {
                continue;
            }
            ObjectNode v = JsonUtils.MAPPER.createObjectNode();
            v.put("term", unit.name);
            v.put("source", unit.path);
            v.put("definition", "");
            out.add(v);
        }
        for (JsonNode component : analysis.path("components")) {
            String term = component.asText();
            if (!term.isBlank() && seen.add(term)) {
                ObjectNode v = JsonUtils.MAPPER.createObjectNode();
                v.put("term", term);
                v.put("source", "component");
                v.put("definition", "");
                out.add(v);
            }
        }
        return out;
    }

    private List<ObjectNode> projectRisks(List<Unit> units, ObjectNode analysis) {
        List<ObjectNode> out = new ArrayList<>();
        long ui = units.stream().filter(u -> u.kind.equals("ui")).count();
        long mixed = units.stream().filter(u -> u.kind.equals("mixed")).count();
        if (ui > 0) {
            out.add(risk(ui + " UI unit(s): not mechanically portable; UI strategy decision"
                    + " governs what happens to them"));
        }
        if (mixed > 0) {
            out.add(risk(mixed + " mixed unit(s): extract portable logic before porting"));
        }
        for (JsonNode unsupported : analysis.path("unsupported")) {
            out.add(risk("unsupported construct in source: " + unsupported.asText()));
        }
        boolean anyCycle = units.stream().anyMatch(u -> u.inCycle);
        if (anyCycle) {
            out.add(risk("dependency cycle detected: port order inside the cycle is"
                    + " unresolved; break the cycle before porting"));
        }
        return out;
    }

    private static ObjectNode risk(String message) {
        ObjectNode r = JsonUtils.MAPPER.createObjectNode();
        r.put("risk", message);
        return r;
    }

    private List<String> limitations() {
        List<String> out = new ArrayList<>();
        out.add("Static analysis only: this plan does not translate code and does not"
                + " prove behavior; the port is written by the coding agent.");
        out.add("migrate_record / migrate_replay / migrate_gate land in a later 1.3.x"
                + " release; until then 'matches the original' is a claim, not evidence.");
        out.add("Starter decisions are seeds with status needs-owner; they are not"
                + " decisions until the owner confirms them.");
        out.add("Port order inside a dependency cycle is unresolved by definition.");
        out.add("Vocabulary definitions are empty seeds; fill them from the source.");
        return out;
    }

    // ========================================================================
    // Markdown report
    // ========================================================================

    private String renderMarkdown(String projectName, ObjectNode analysis,
                                  List<Unit> units, List<List<String>> waves) {
        StringBuilder md = new StringBuilder();
        md.append("# Migration Plan — ").append(projectName).append("\n\n");
        md.append("Generated by `migrate_plan` (java-anything by JavaBytes). "
                + "Methodology inspired by [rust-anything](https://github.com/erkamyaman/rust-anything) (MIT).\n\n");
        md.append("## Summary\n\n");
        md.append("| Metric | Value |\n|---|---|\n");
        md.append("| Files analyzed | ").append(analysis.path("files_analyzed").asInt(units.size())).append(" |\n");
        md.append("| Units | ").append(units.size()).append(" |\n");
        md.append("| Total lines | ").append(analysis.path("total_lines").asInt()).append(" |\n");
        long portable = units.stream().filter(u -> u.kind.equals("portable")).count();
        long mixed = units.stream().filter(u -> u.kind.equals("mixed")).count();
        long ui = units.stream().filter(u -> u.kind.equals("ui")).count();
        md.append("| Portable | ").append(portable).append(" |\n");
        md.append("| Mixed (extract logic first) | ").append(mixed).append(" |\n");
        md.append("| UI (not mechanically portable) | ").append(ui).append(" |\n\n");

        md.append("## Port order\n\n");
        int waveNo = 1;
        for (List<String> wave : waves) {
            md.append("### Wave ").append(waveNo++).append("\n\n");
            md.append("| Unit | Kind | Lines | Depends on |\n|---|---|---|---|\n");
            for (String name : wave) {
                Unit unit = units.stream().filter(u -> u.name.equals(name)).findFirst().orElseThrow();
                md.append("| ").append(unit.name).append(" | ").append(unit.kind).append(" | ")
                        .append(unit.lines).append(" | ")
                        .append(unit.dependsOn.isEmpty() ? "-" : String.join(", ", unit.dependsOn))
                        .append(" |\n");
            }
            md.append("\n");
        }

        md.append("## Risks\n\n");
        for (Unit unit : units) {
            for (String risk : unit.risks) {
                md.append("- `").append(unit.name).append("`: ").append(risk).append("\n");
            }
        }
        if (units.stream().allMatch(u -> u.risks.isEmpty())) {
            md.append("- No unit-level risks detected by static analysis.\n");
        }
        md.append("\n");

        md.append("## Starter decisions (needs-owner)\n\n");
        int n = 1;
        for (ObjectNode d : starterDecisions(analysis, projectName)) {
            md.append(n++).append(". **").append(d.path("decision").asText()).append("** — ")
                    .append(d.path("detail").asText()).append("\n");
        }
        md.append("\n");

        md.append("## Vocabulary seed (GLOSSARY.md)\n\n");
        md.append("| Term | Source |\n|---|---|\n");
        for (ObjectNode v : vocabularySeed(units, analysis)) {
            md.append("| ").append(v.path("term").asText()).append(" | ")
                    .append(v.path("source").asText()).append(" |\n");
        }
        md.append("\n");

        md.append("## Limitations\n\n");
        for (String limitation : limitations()) {
            md.append("- ").append(limitation).append("\n");
        }
        md.append("\n## Next steps\n\n");
        md.append("1. Owner confirms the starter decisions.\n");
        md.append("2. Port wave 1 units (agent-written; `translate_javascript` assists on the honest subset).\n");
        md.append("3. When migrate_record ships: record the original unit, port, replay, gate.\n");
        return md.toString();
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("path", new Tool.StringSchema()
                .description("JavaScript/TypeScript file or project directory inside an allowed root"));
        schema.prop("code", new Tool.StringSchema()
                .description("Inline source to plan instead of reading a path"));
        schema.prop("file_name", new Tool.StringSchema()
                .description("Virtual file name for inline code, e.g. app.tsx (default input.js)"));
        schema.prop("max_files", new Tool.NumberSchema()
                .integer()
                .minimum(1)
                .maximum(2000)
                .description("Maximum files to analyze for a directory (default 500)"));
        schema.prop("name", new Tool.StringSchema()
                .description("Project name used in the plan (default: directory name)"));
        schema.prop("report_path", new Tool.StringSchema()
                .description("Optional .md file path to receive the full migration plan markdown"
                        + " (must end with .md, not a directory)"));
        return schema;
    }
}
