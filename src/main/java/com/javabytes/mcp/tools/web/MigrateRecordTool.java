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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * java-anything step 2 (ADR 0015): run the <em>original</em> JS unit under
 * Node on generated edge cases plus the semantic trap catalog (ADR 0016) and
 * record the observed behavior as JSONL cases ({@code cases/<unit>.jsonl}).
 *
 * <p>The recording is the ceiling of what the migration gate can later prove:
 * it captures what this machine's Node did for the generated inputs, nothing
 * more. The tool never claims mechanical translation; the port is still
 * written by the coding agent.</p>
 */
public final class MigrateRecordTool implements Tool {

    private static final Set<String> RUNNABLE_EXTENSIONS = Set.of(".js", ".mjs", ".cjs");
    private static final int MAX_FUNCTIONS = 64;
    private static final int MAX_CASE_LINES = 5000;
    private static final int MAX_STDERR_CHARS = 2000;

    /** Trap names, verbatim from ADR 0016. Adding a trap is a minor bump. */
    static final String[] TRAPS = {
            "undefined_null", "utf16_units", "number_format", "sort_order", "date_parse"
    };

    public MigrateRecordTool() {
    }

    @Override
    public String getName() {
        return "migrate_record";
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "migrate_record",
                "Record original JS behavior as replay cases",
                "Run the original JS unit under Node on generated edge cases and the five "
                        + "semantic traps (undefined/null, UTF-16 code units, number formatting, "
                        + "sort/iteration order, date parsing), recording observed behavior as "
                        + "JSONL cases (cases/<unit>.jsonl) that migrate_replay replays against "
                        + "the Java port. Inspiration: rust-anything (MIT).",
                "analysis",
                true,
                false,
                false,
                true,
                false);
    }

    @Override
    public JsonNode execute(JsonNode params) {
        String pathParam = params.has("path") ? params.get("path").asText().trim() : "";
        String functionsParam = params.has("functions") ? params.get("functions").asText().trim() : "";
        String trapsParam = params.has("traps") ? params.get("traps").asText().trim() : "";
        int casesPerFunction = params.has("cases_per_function")
                ? params.get("cases_per_function").asInt(6)
                : 6;
        String outputParam = params.has("output") ? params.get("output").asText().trim() : "";
        int timeoutSeconds = params.has("timeout_seconds") ? params.get("timeout_seconds").asInt(30) : 30;
        String nodeCommand = params.has("node") ? params.get("node").asText().trim() : "";

        if (pathParam.isEmpty()) {
            return ErrorHelper.error("MISSING_PARAM", "Provide 'path' to a .js/.mjs/.cjs file");
        }
        casesPerFunction = Math.min(Math.max(casesPerFunction, 1), 24);
        timeoutSeconds = Math.min(Math.max(timeoutSeconds, 5), 120);

        Path source = Path.of(pathParam);
        if (!PathValidator.isAllowed(source)) {
            return ErrorHelper.error("PATH_NOT_ALLOWED", "Path not allowed: " + pathParam
                    + ". Allowed roots: " + AppConfig.describeAllowedDirectories());
        }
        if (!Files.isRegularFile(source)) {
            return ErrorHelper.error("PATH_NOT_FOUND", "Not a file: " + pathParam);
        }
        String lower = source.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (!RUNNABLE_EXTENSIONS.stream().anyMatch(lower::endsWith)) {
            return ErrorHelper.error("NOT_RUNNABLE_UNDER_NODE",
                    "Only .js/.mjs/.cjs sources run directly under Node; precompile "
                            + lower.substring(lower.lastIndexOf('.') + 1).toUpperCase()
                            + " sources to plain JS first (TypeScript, JSX and .vue/.svelte "
                            + "are not directly runnable).");
        }

        Set<String> trapFilter = new LinkedHashSet<>();
        for (String t : trapsParam.split(",")) {
            String name = t.trim();
            if (name.isEmpty()) {
                continue;
            }
            if (!Set.of(TRAPS).contains(name)) {
                return ErrorHelper.error("UNKNOWN_TRAP",
                        "Unknown trap '" + name + "'. Supported traps: " + String.join(", ", TRAPS));
            }
            trapFilter.add(name);
        }

        String output = outputParam;
        if (output.isEmpty()) {
            String stem = source.getFileName().toString().replaceFirst("\\.[^.]+$", "");
            output = source.getParent().resolve("cases").resolve(stem + ".jsonl").toString();
        }
        if (!output.endsWith(".jsonl")) {
            return ErrorHelper.error("INVALID_OUTPUT",
                    "output must be a .jsonl file path (e.g. '.../cases/store.jsonl'), "
                            + "not a directory or other extension.");
        }
        Path outputDest = Path.of(output);
        if (!PathValidator.isAllowed(outputDest)) {
            return ErrorHelper.error("PATH_NOT_ALLOWED", "output not allowed: " + output
                    + ". Allowed roots: " + AppConfig.describeAllowedDirectories());
        }

        String node = nodeCommand.isEmpty()
                ? System.getenv().getOrDefault("JAVABYTES_NODE", "node")
                : nodeCommand;

        Path driver = null;
        try {
            String nodeVersion = probeNode(node);
            if (nodeVersion == null) {
                return ErrorHelper.error("NODE_NOT_FOUND",
                        "Node.js is required to record the original behavior but '" + node
                                + "' is not runnable. Install Node or set JAVABYTES_NODE / "
                                + "the 'node' parameter to its path. Recording is never "
                                + "silently skipped.");
            }

            driver = Files.createTempFile("javabytes-record-", ".mjs");
            Files.writeString(driver, buildDriver());

            List<String> command = new ArrayList<>();
            command.add(node);
            command.add(driver.toAbsolutePath().toString());

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(source.getParent().toFile());
            pb.environment().put("JB_MOD_PATH", source.toAbsolutePath().toString());
            pb.environment().put("JB_CASES", String.valueOf(casesPerFunction));
            pb.environment().put("JB_TRAPS", String.join(",", trapFilter));
            pb.environment().put("JB_FUNCTIONS", functionsParam);
            pb.redirectErrorStream(false);

            Process process = pb.start();
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            Thread outReader = pipe(process, process.getInputStream(), stdout,
                    4L * 1024 * 1024, "javabytes-record-stdout");
            Thread errReader = pipe(process, process.getErrorStream(), stderr,
                    MAX_STDERR_CHARS * 4L, "javabytes-record-stderr");

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                return ErrorHelper.error("RECORD_TIMEOUT",
                        "Node did not finish within " + timeoutSeconds + "s and was killed. "
                                + "A module that never terminates cannot be recorded; export "
                                + "pure functions or raise timeout_seconds.");
            }
            outReader.join(2000);
            errReader.join(2000);

            if (process.exitValue() != 0) {
                return ErrorHelper.error("RECORD_ERROR",
                        "Node exited with code " + process.exitValue()
                                + " while importing the module. The module must at least "
                                + "import cleanly to be recorded. stderr: "
                                + tail(stderr.toString()));
            }

            return assembleResult(source, outputDest, stdout.toString(), stderr.toString(), nodeVersion);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ErrorHelper.error("MIGRATE_RECORD_ERROR", "Recording interrupted: " + e.getMessage());
        } catch (Exception e) {
            return ErrorHelper.error("MIGRATE_RECORD_ERROR",
                    "Error recording behavior: " + e.getMessage());
        } finally {
            if (driver != null) {
                try {
                    Files.deleteIfExists(driver);
                } catch (Exception ignored) {
                    // temp file cleanup is best-effort
                }
            }
        }
    }

    /** Run {@code node --version}; null when Node is absent or fails. */
    private static String probeNode(String node) {
        try {
            Process p = new ProcessBuilder(node, "--version").start();
            if (!p.waitFor(15, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            if (p.exitValue() != 0) {
                return null;
            }
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line = r.readLine();
                return line == null ? null : line.trim();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static Thread pipe(Process process, java.io.InputStream stream,
                               StringBuilder sink, long capChars, String name) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                char[] buf = new char[4096];
                int n;
                while ((n = r.read(buf)) != -1) {
                    sink.append(buf, 0, n);
                    if (sink.length() > capChars) {
                        break;
                    }
                }
            } catch (Exception ignored) {
                // stream ended or process killed
            }
        }, name);
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static ObjectNode assembleResult(Path source, Path outputDest, String stdout,
                                             String stderr, String nodeVersion) throws Exception {
        String unit = source.getFileName().toString().replaceFirst("\\.[^.]+$", "");

        JsonNode manifest = null;
        List<ObjectNode> cases = new ArrayList<>();
        int truncated = 0;
        for (String line : stdout.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || !trimmed.startsWith("{")) {
                continue;
            }
            JsonNode node = JsonUtils.MAPPER.readTree(trimmed);
            if (node.has("__manifest")) {
                manifest = node;
                continue;
            }
            if (node.has("fn")) {
                if (cases.size() < MAX_CASE_LINES) {
                    cases.add((ObjectNode) node);
                } else {
                    truncated++;
                }
            }
        }

        Set<String> functionsFound = new LinkedHashSet<>();
        if (manifest != null && manifest.path("functions").isArray()) {
            manifest.path("functions").forEach(f -> functionsFound.add(f.asText()));
        }
        Set<String> functionsRecorded = new LinkedHashSet<>();
        Map<String, Integer> trapCoverage = new LinkedHashMap<>();
        for (String trap : TRAPS) {
            trapCoverage.put(trap, 0);
        }
        for (ObjectNode c : cases) {
            functionsRecorded.add(c.path("fn").asText());
            c.path("trap").forEach(t -> trapCoverage.merge(t.asText(), 1, Integer::sum));
        }

        List<String> limitations = new ArrayList<>();
        limitations.add("Recording is the ceiling: only the generated cases are evidence; "
                + "unrecorded inputs, regex subtleties, floats outside the trap pool and "
                + "non-output side effects are not captured.");
        limitations.add("The original module runs under Node on this machine: arbitrary "
                + "code in it executes during recording.");
        limitations.add("Behavior is environment-dependent (clock, locale, filesystem): "
                + "cases record what THIS machine did; replay must run on the same machine.");
        if (functionsFound.size() > MAX_FUNCTIONS) {
            limitations.add("More than " + MAX_FUNCTIONS + " exported functions found; "
                    + "use the 'functions' parameter to select which to record.");
        }
        if (truncated > 0) {
            limitations.add("Case output truncated at " + MAX_CASE_LINES + " cases ("
                    + truncated + " more were dropped).");
        }
        if (!functionsFound.isEmpty() && functionsRecorded.isEmpty()) {
            limitations.add("Functions were found but no cases recorded: the trap filter "
                    + "may have excluded everything.");
        }
        if (!stderr.isBlank()) {
            limitations.add("Node wrote to stderr during recording (captured in stderr_tail).");
        }

        Files.createDirectories(outputDest.getParent() == null ? Path.of(".") : outputDest.getParent());
        StringBuilder jsonl = new StringBuilder();
        for (ObjectNode c : cases) {
            jsonl.append(c.toString()).append('\n');
        }
        Files.writeString(outputDest, jsonl.toString());

        ObjectNode out = JsonUtils.MAPPER.createObjectNode();
        out.put("success", true);
        out.put("tool", "migrate_record");
        out.put("unit", unit);
        out.put("node_version", nodeVersion == null ? "" : nodeVersion);
        out.put("functions_found", functionsFound.size());
        out.put("functions_recorded", functionsRecorded.size());
        out.put("cases_total", cases.size());
        out.put("cases_written", cases.size());
        out.put("output", outputDest.toAbsolutePath().toString());
        ArrayNode trapNode = out.putArray("trap_coverage");
        for (Map.Entry<String, Integer> e : trapCoverage.entrySet()) {
            ObjectNode t = trapNode.addObject();
            t.put("trap", e.getKey());
            t.put("cases", e.getValue());
            t.put("covered", e.getValue() > 0);
        }
        ArrayNode fns = out.putArray("functions");
        functionsFound.forEach(fns::add);
        ArrayNode lim = out.putArray("limitations");
        limitations.forEach(lim::add);
        if (!stderr.isBlank()) {
            out.put("stderr_tail", tail(stderr.toString()));
        }
        return out;
    }

    private static String tail(String s) {
        String cleaned = s.strip();
        if (cleaned.length() <= MAX_STDERR_CHARS) {
            return cleaned;
        }
        return "…" + cleaned.substring(cleaned.length() - MAX_STDERR_CHARS);
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("path", new Tool.StringSchema()
                .description("Path to the original .js/.mjs/.cjs unit to record (inside an allowed root)"));
        schema.prop("functions", new Tool.StringSchema()
                .description("Optional comma-separated export names to record (default: all exports)"));
        schema.prop("traps", new Tool.StringSchema()
                .description("Optional comma-separated subset of traps to include (default: all five): "
                        + String.join(", ", TRAPS)));
        schema.prop("cases_per_function", new Tool.NumberSchema()
                .integer()
                .minimum(1)
                .maximum(24)
                .description("Trap tuples per function beyond the no-arg case (default 6)"));
        schema.prop("output", new Tool.StringSchema()
                .description("Optional .jsonl file path for the cases (default: <dir>/cases/<unit>.jsonl; "
                        + "must end with .jsonl)"));
        schema.prop("timeout_seconds", new Tool.NumberSchema()
                .integer()
                .minimum(5)
                .maximum(120)
                .description("Node run timeout in seconds (default 30)"));
        schema.prop("node", new Tool.StringSchema()
                .description("Optional path to the Node executable (default: JAVABYTES_NODE env or 'node')"));
        return schema;
    }

    // ========================================================================
    // Driver generation
    // ========================================================================

    /**
     * The Node driver: imports the module, enumerates exported functions at
     * runtime (more honest than static analysis — catches lowercase names the
     * analyzer's capitalized patterns miss), calls each with trap-pool tuples
     * and emits one JSONL case per call on stdout.
     */
    static String buildDriver() {
        return """
                import { pathToFileURL } from 'node:url';

                const modPath = process.env.JB_MOD_PATH;
                const maxCases = parseInt(process.env.JB_CASES || '6', 10);
                const trapFilter = (process.env.JB_TRAPS || '').split(',').filter(Boolean);
                const fnFilter = (process.env.JB_FUNCTIONS || '').split(',').filter(Boolean);
                const emit = (obj) => process.stdout.write(JSON.stringify(obj) + '\\n');

                function ser(v, depth) {
                  depth = depth || 0;
                  if (depth > 4) return { __deep: true };
                  if (v === undefined) return { __undefined: true };
                  if (typeof v === 'number') {
                    if (Number.isNaN(v)) return { __nan: true };
                    if (Object.is(v, -0)) return { __minusZero: true };
                    if (v === Infinity) return { __infinity: 1 };
                    if (v === -Infinity) return { __infinity: -1 };
                    return v;
                  }
                  if (typeof v === 'bigint') return { __bigint: String(v) };
                  if (typeof v === 'function') return { __function: true };
                  if (typeof v === 'symbol') return { __symbol: String(v) };
                  if (v instanceof Date) {
                    const t = v.getTime();
                    return { __date: Number.isNaN(t) ? 'Invalid Date' : v.toISOString() };
                  }
                  if (v instanceof Error) {
                    return { __error: { name: v.name, message: v.message } };
                  }
                  if (Array.isArray(v)) return v.slice(0, 50).map((x) => ser(x, depth + 1));
                  if (v && typeof v === 'object') {
                    const out = {};
                    for (const k of Object.keys(v).slice(0, 50)) out[k] = ser(v[k], depth + 1);
                    return out;
                  }
                  return v;
                }

                // Trap value pool — ADR 0016 starter set of five, verbatim names.
                const TRAP_VALUES = [
                  { trap: 'undefined_null', value: undefined },
                  { trap: 'undefined_null', value: null },
                  { trap: 'utf16_units', value: 'a\\u{1F44D}b' },
                  { trap: 'utf16_units', value: '\\u{1F600}'.repeat(3) },
                  { trap: 'number_format', value: 0.1 + 0.2 },
                  { trap: 'number_format', value: -0 },
                  { trap: 'number_format', value: 1e21 },
                  { trap: 'number_format', value: Number.MAX_SAFE_INTEGER + 1 },
                  { trap: 'sort_order', value: [10, 9, 1] },
                  { trap: 'sort_order', value: { b: 1, a: 2 } },
                  { trap: 'date_parse', value: '2026-01-02' },
                  { trap: 'date_parse', value: 'Jan 2 2026' },
                  { trap: 'date_parse', value: '2026-13-45' },
                  { trap: 'undefined_null', value: 'plain' },
                  { trap: 'undefined_null', value: 7 }
                ];

                async function loadMod() {
                  const mod = await import(pathToFileURL(modPath).href);
                  const fns = new Map();
                  const consider = (name, val) => {
                    if (typeof val === 'function' && !fns.has(name)) fns.set(name, val);
                  };
                  for (const k of Object.keys(mod)) consider(k, mod[k]);
                  if (mod.default && typeof mod.default === 'object') {
                    for (const k of Object.keys(mod.default)) consider(k, mod.default[k]);
                  }
                  return fns;
                }

                async function call(name, fn, args, traps) {
                  if (trapFilter.length && !traps.some((t) => trapFilter.includes(t))) return;
                  const argsJson = ser(args);
                  let result = null;
                  let thrown = null;
                  try {
                    let out = fn(...args);
                    if (out && typeof out.then === 'function') {
                      out = await Promise.race([
                        out,
                        new Promise((resolve, reject) => {
                          setTimeout(() => reject(new Error('__jb_async_timeout')), 2000);
                        })
                      ]);
                    }
                    result = ser(out);
                  } catch (e) {
                    const msg = e && e.message === '__jb_async_timeout'
                      ? 'async case timed out after 2000ms'
                      : String((e && e.message) || '');
                    thrown = { name: (e && e.name) || 'Error', message: msg };
                  }
                  emit({ fn: name, args: argsJson, result: result, throws: thrown, trap: traps });
                }

                const fns = await loadMod();
                emit({ __manifest: true, node_version: process.version, functions: [...fns.keys()] });

                for (const [name, fn] of fns) {
                  if (fnFilter.length && !fnFilter.includes(name)) continue;
                  const arity = Math.max(fn.length, 1);

                  // Case 1: no-arg call (undefined/null trap: every param missing).
                  await call(name, fn, [], ['undefined_null']);

                  // Cases 2..N: deterministic trap-pool tuples.
                  const wanted = Number.isFinite(maxCases) ? Math.min(Math.max(maxCases, 1), 24) : 6;
                  for (let i = 0; i < wanted; i++) {
                    const args = [];
                    const traps = new Set();
                    for (let a = 0; a < arity; a++) {
                      const pick = TRAP_VALUES[(i * 7 + a * 3 + name.length) % TRAP_VALUES.length];
                      args.push(pick.value);
                      traps.add(pick.trap);
                    }
                    await call(name, fn, args, [...traps]);
                  }
                }
                """;
    }
}
