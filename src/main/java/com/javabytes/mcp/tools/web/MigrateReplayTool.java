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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * java-anything step 3 (ADR 0015): generate a JUnit 5 replay test from the
 * JSONL cases recorded by {@code migrate_record}. The test reads the cases at
 * runtime and drives the Java port via reflection, comparing observed values
 * (including the JS-only sentinels: undefined, -0, NaN, ±Infinity, bigint)
 * against the recorded originals. {@code mvn test} on the generated project
 * IS the replay; {@code migrate_gate} runs it as the Gate tier.
 */
public final class MigrateReplayTool implements Tool {

    public MigrateReplayTool() {
    }

    @Override
    public String getName() {
        return "migrate_replay";
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "migrate_replay",
                "Generate a JUnit 5 replay test from recorded cases",
                "Generate a JUnit 5 test (ReplayTest) that replays the JSONL cases recorded "
                        + "by migrate_record against the Java port class: invokes each ported "
                        + "method with the recorded arguments and compares values, including "
                        + "JS-only sentinels (undefined, -0, NaN, Infinity, bigint) and throws. "
                        + "Running 'mvn test' on the generated project is the replay. "
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
        String casesParam = params.has("cases") ? params.get("cases").asText().trim() : "";
        String classParam = params.has("class") ? params.get("class").asText().trim() : "";
        String packageParam = params.has("package") ? params.get("package").asText().trim() : "";
        String projectParam = params.has("project") ? params.get("project").asText().trim() : "";
        String outputParam = params.has("output") ? params.get("output").asText().trim() : "";

        if (casesParam.isEmpty()) {
            return ErrorHelper.error("MISSING_PARAM",
                    "Provide 'cases' (path to the .jsonl recorded by migrate_record)");
        }
        if (classParam.isEmpty()) {
            return ErrorHelper.error("MISSING_PARAM",
                    "Provide 'class' — the Java port class (simple name with 'package', "
                            + "or fully-qualified)");
        }

        Path casesFile = Path.of(casesParam);
        if (!PathValidator.isAllowed(casesFile)) {
            return ErrorHelper.error("PATH_NOT_ALLOWED", "cases not allowed: " + casesParam
                    + ". Allowed roots: " + AppConfig.describeAllowedDirectories());
        }
        if (!Files.isRegularFile(casesFile)) {
            return ErrorHelper.error("PATH_NOT_FOUND", "cases file not found: " + casesParam);
        }
        if (!casesParam.endsWith(".jsonl")) {
            return ErrorHelper.error("INVALID_CASES", "cases must be a .jsonl file");
        }

        String unit = casesFile.getFileName().toString().replaceFirst("\\.jsonl$", "");
        String unitClass = capitalize(unit);

        // Parse + validate the cases BEFORE generating anything.
        Set<String> functions = new TreeSet<>();
        int caseCount = 0;
        int throwCount = 0;
        List<String> parseErrors = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(casesFile)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                JsonNode c;
                try {
                    c = JsonUtils.MAPPER.readTree(trimmed);
                } catch (Exception e) {
                    parseErrors.add(trimmed.substring(0, Math.min(80, trimmed.length())));
                    continue;
                }
                if (!c.has("fn")) {
                    parseErrors.add("no fn field: " + trimmed.substring(0, Math.min(60, trimmed.length())));
                    continue;
                }
                functions.add(c.path("fn").asText());
                caseCount++;
                if (c.hasNonNull("throws") && c.path("throws").isObject()) {
                    throwCount++;
                }
            }
        } catch (Exception e) {
            return ErrorHelper.error("MIGRATE_REPLAY_ERROR",
                    "Error reading cases: " + e.getMessage());
        }
        if (caseCount == 0) {
            return ErrorHelper.error("EMPTY_CASES",
                    "No parseable cases in " + casesParam
                            + (parseErrors.isEmpty() ? "" : ". First problems: "
                            + String.join(" | ", parseErrors.subList(0, Math.min(3, parseErrors.size())))));
        }

        // Resolve package + port class name.
        String pkg;
        String portClass;
        int dot = classParam.lastIndexOf('.');
        if (dot > 0) {
            portClass = classParam;
            pkg = packageParam.isEmpty() ? classParam.substring(0, dot) : packageParam;
        } else {
            pkg = packageParam.isEmpty() ? "com.migrated" : packageParam;
            portClass = pkg + "." + classParam;
        }

        // Resolve the output file.
        Path outputDest;
        if (!outputParam.isEmpty()) {
            if (!outputParam.endsWith(".java")) {
                return ErrorHelper.error("INVALID_OUTPUT",
                        "output must be a .java file path (e.g. '.../StoreReplayTest.java'), "
                                + "not a directory. Directory paths are rejected to prevent "
                                + "silent overwrites.");
            }
            outputDest = Path.of(outputParam);
        } else if (!projectParam.isEmpty()) {
            Path project = Path.of(projectParam);
            outputDest = project.resolve("src").resolve("test").resolve("java")
                    .resolve(pkg.replace('.', '/'))
                    .resolve(unitClass + "ReplayTest.java");
        } else {
            Path parent = casesFile.getParent() == null ? Path.of(".") : casesFile.getParent();
            outputDest = parent.resolve(unitClass + "ReplayTest.java");
        }
        if (!PathValidator.isAllowed(outputDest)) {
            return ErrorHelper.error("PATH_NOT_ALLOWED", "output not allowed: " + outputDest
                    + ". Allowed roots: " + AppConfig.describeAllowedDirectories());
        }

        String testSource = renderTest(pkg, unitClass, portClass,
                relativePath(outputDest, casesFile), functions);

        try {
            if (outputDest.getParent() != null) {
                Files.createDirectories(outputDest.getParent());
            }
            Files.writeString(outputDest, testSource);
        } catch (Exception e) {
            return ErrorHelper.error("MIGRATE_REPLAY_ERROR",
                    "Error writing replay test: " + e.getMessage());
        }

        List<String> limitations = new ArrayList<>();
        limitations.add("JS error classes do not map 1:1 to Java exceptions: recorded "
                + "throws are replayed as 'some exception was thrown', not by class name.");
        limitations.add("When several port methods share a name and arity, the replay "
                + "uses the first match; keep one method per recorded name/arity.");
        limitations.add("Environment-dependent behavior (clock, locale, filesystem) must "
                + "be replayed on the same machine that recorded the cases.");
        limitations.add("Unportable values (__function, __symbol, __deep) fail the replay "
                + "loudly by design; restructure the ported function instead.");
        if (projectParam.isEmpty() && outputParam.isEmpty()) {
            limitations.add("Test written next to the cases file; move it to "
                    + "src/test/java/" + pkg.replace('.', '/') + "/ in the generated project "
                    + "(or pass 'project' next time).");
        }

        ObjectNode out = JsonUtils.MAPPER.createObjectNode();
        out.put("success", true);
        out.put("tool", "migrate_replay");
        out.put("unit", unit);
        out.put("test_class", pkg + "." + unitClass + "ReplayTest");
        out.put("port_class", portClass);
        out.put("cases_embedded", caseCount);
        out.put("throws_cases", throwCount);
        out.put("functions", functions.size());
        out.put("test_written", outputDest.toAbsolutePath().toString());
        ArrayNode fns = out.putArray("functions");
        functions.forEach(fns::add);
        ArrayNode lim = out.putArray("limitations");
        limitations.forEach(lim::add);
        if (!parseErrors.isEmpty()) {
            out.put("skipped_lines", parseErrors.size());
        }
        return out;
    }

    /** Best-effort relative path of the cases file as seen from the test's run dir. */
    private static String relativePath(Path outputDest, Path casesFile) {
        Path outputAbs = outputDest.toAbsolutePath().normalize();
        // Heuristic: if output sits under a project root (…/src/test/java/…),
        // the project root is four levels above the package dir; otherwise the
        // cases file is referenced by its absolute path (mvn runs anywhere).
        Path p = outputAbs;
        for (int i = 0; i < 10 && p != null; i++) {
            if (p.getFileName() != null && p.getFileName().toString().equals("java")
                    && p.getParent() != null && p.getParent().getFileName() != null
                    && p.getParent().getFileName().toString().equals("test")) {
                Path projectRoot = p.getParent().getParent();
                try {
                    return projectRoot.relativize(casesFile.toAbsolutePath().normalize())
                            .toString().replace('\\', '/');
                } catch (Exception ignored) {
                    break;
                }
            }
            p = p.getParent();
        }
        return casesFile.toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return "Unit";
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ========================================================================
    // Test source generation
    // ========================================================================

    private static String renderTest(String pkg, String unitClass, String portClass,
                                     String casesPath, Set<String> functions) {
        StringBuilder tests = new StringBuilder();
        for (String fn : functions) {
            String safe = fn.replaceAll("[^A-Za-z0-9]", "_");
            tests.append("""
                    \n    @Test
                    void replays_%s() throws Exception {
                        for (JsonNode c : cases("%s")) {
                            JsonNode args = c.path("args");
                            String desc = c.path("fn").asText() + " case#" + c.hashCode();
                            if (c.hasNonNull("throws") && c.path("throws").isObject()) {
                                assertThrows(Exception.class, () -> invoke("%s", args),
                                        () -> desc + ": expected a throw, got a value");
                            } else {
                                Object actual = invoke("%s", args);
                                compare(desc, c.path("result"), actual);
                            }
                        }
                    }
                    """.formatted(fn, fn, fn, fn));
        }

        return """
                package %s;

                import com.fasterxml.jackson.databind.JsonNode;
                import com.fasterxml.jackson.databind.ObjectMapper;
                import org.junit.jupiter.api.Test;

                import java.lang.reflect.Array;
                import java.lang.reflect.InvocationTargetException;
                import java.lang.reflect.Method;
                import java.lang.reflect.Modifier;
                import java.math.BigDecimal;
                import java.nio.file.Files;
                import java.nio.file.Path;
                import java.util.ArrayList;
                import java.util.Collection;
                import java.util.LinkedHashMap;
                import java.util.List;
                import java.util.Map;

                import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                import static org.junit.jupiter.api.Assertions.assertFalse;
                import static org.junit.jupiter.api.Assertions.assertNotNull;
                import static org.junit.jupiter.api.Assertions.assertThrows;
                import static org.junit.jupiter.api.Assertions.assertTrue;

                /**
                 * Generated by migrate_replay (java-anything by JavaBytes).
                 * Replays recorded original-JS behavior (migrate_record cases) against
                 * the Java port %s. Gate tier per ADR 0015: 100%% replay match.
                 * Runs with plain 'mvn test' on the generated project.
                 */
                class %sReplayTest {

                    private static final String CASES_JSONL = "%s";
                    private static final String PORT_CLASS = "%s";
                    private static final ObjectMapper MAPPER = new ObjectMapper();
                    private static Object portInstance;

                    private static Object port() throws Exception {
                        if (portInstance == null) {
                            Class<?> cls;
                            try {
                                cls = Class.forName(PORT_CLASS);
                            } catch (ClassNotFoundException e) {
                                throw new AssertionError("Port class not found: " + PORT_CLASS
                                        + " — the port is not written yet; the Gate cannot pass "
                                        + "without it.", e);
                            }
                            Object instance = assertDoesNotThrow(() -> {
                                java.lang.reflect.Constructor<?> ctor = cls.getDeclaredConstructor();
                                ctor.setAccessible(true);
                                return ctor.newInstance();
                            }, "Port class " + PORT_CLASS + " must have a no-arg constructor");
                            portInstance = instance;
                        }
                        return portInstance;
                    }

                    private static List<JsonNode> cases(String fn) throws Exception {
                        Path p = Path.of(CASES_JSONL);
                        assertTrue(Files.exists(p), "cases file missing: " + p
                                + " — run migrate_record first or fix CASES_JSONL");
                        List<JsonNode> out = new ArrayList<>();
                        for (String line : Files.readAllLines(p)) {
                            if (line.isBlank()) {
                                continue;
                            }
                            JsonNode c = MAPPER.readTree(line);
                            if (fn.equals(c.path("fn").asText())) {
                                out.add(c);
                            }
                        }
                        assertFalse(out.isEmpty(), "no recorded cases for '" + fn + "'");
                        return out;
                    }

                    // ---- invocation ----------------------------------------------------

                    private static Object invoke(String fn, JsonNode args) throws Exception {
                        Object target = port();
                        List<Method> matches = new ArrayList<>();
                        for (Method m : target.getClass().getDeclaredMethods()) {
                            if (m.getName().equals(fn) && m.getParameterCount() == args.size()
                                    && !m.isSynthetic()) {
                                matches.add(m);
                            }
                        }
                        assertFalse(matches.isEmpty(),
                                "Port " + PORT_CLASS + " has no method '" + fn + "' with arity "
                                        + args.size());
                        Method m = matches.get(0);
                        m.setAccessible(true);
                        Class<?>[] pts = m.getParameterTypes();
                        Object[] argv = new Object[args.size()];
                        for (int i = 0; i < argv.length; i++) {
                            argv[i] = coerce(args.get(i), box(pts[i]));
                        }
                        try {
                            return m.invoke(target, argv);
                        } catch (InvocationTargetException e) {
                            Throwable cause = e.getCause() == null ? e : e.getCause();
                            if (cause instanceof RuntimeException re) {
                                throw re;
                            }
                            if (cause instanceof Error err) {
                                throw err;
                            }
                            throw new RuntimeException(cause);
                        }
                    }

                    private static Class<?> box(Class<?> t) {
                        if (!t.isPrimitive()) {
                            return t;
                        }
                        if (t == int.class) return Integer.class;
                        if (t == long.class) return Long.class;
                        if (t == double.class) return Double.class;
                        if (t == float.class) return Float.class;
                        if (t == short.class) return Short.class;
                        if (t == byte.class) return Byte.class;
                        if (t == boolean.class) return Boolean.class;
                        if (t == char.class) return Character.class;
                        return Object.class;
                    }

                    private static Object coerce(JsonNode v, Class<?> target) {
                        if (v == null || v.isNull() || isMarker(v, "__undefined")) {
                            return null;
                        }
                        if (target == Object.class) {
                            return canonical(v);
                        }
                        if (target == String.class) {
                            return v.isTextual() ? v.asText() : v.toString();
                        }
                        if (target == Boolean.class) {
                            return v.asBoolean();
                        }
                        if (target == Integer.class) {
                            return (int) v.asDouble();
                        }
                        if (target == Long.class) {
                            return (long) v.asDouble();
                        }
                        if (target == Short.class) {
                            return (short) v.asDouble();
                        }
                        if (target == Byte.class) {
                            return (byte) v.asDouble();
                        }
                        if (target == Float.class) {
                            return (float) v.asDouble();
                        }
                        if (target == Double.class) {
                            return numberValue(v);
                        }
                        if (target == Character.class) {
                            String s = v.asText();
                            return s.isEmpty() ? '\\0' : s.charAt(0);
                        }
                        if (target == List.class || target == Collection.class) {
                            List<Object> out = new ArrayList<>();
                            v.forEach(n -> out.add(canonical(n)));
                            return out;
                        }
                        if (target == Map.class) {
                            Map<String, Object> out = new LinkedHashMap<>();
                            v.fields().forEachRemaining(e -> out.put(e.getKey(), canonical(e.getValue())));
                            return out;
                        }
                        return canonical(v);
                    }

                    private static boolean isMarker(JsonNode v, String marker) {
                        return v != null && v.isObject() && v.has(marker);
                    }

                    /** Recorded JSON (with JS sentinels) → comparable Java value. */
                    private static Object canonical(JsonNode v) {
                        if (v == null || v.isNull() || isMarker(v, "__undefined")) {
                            return null;
                        }
                        if (isMarker(v, "__minusZero")) return Double.valueOf(-0.0d);
                        if (isMarker(v, "__nan")) return Double.NaN;
                        if (isMarker(v, "__infinity")) {
                            return v.path("__infinity").asInt() < 0
                                    ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
                        }
                        if (isMarker(v, "__bigint")) {
                            return new BigDecimal(v.path("__bigint").asText());
                        }
                        if (isMarker(v, "__date") || isMarker(v, "__error")
                                || isMarker(v, "__function") || isMarker(v, "__symbol")
                                || isMarker(v, "__deep")) {
                            return v; // compared specially or failed loudly in compare()
                        }
                        if (v.isNumber()) {
                            return numberValue(v);
                        }
                        if (v.isTextual()) return v.asText();
                        if (v.isBoolean()) return v.asBoolean();
                        if (v.isArray()) {
                            List<Object> out = new ArrayList<>();
                            v.forEach(n -> out.add(canonical(n)));
                            return out;
                        }
                        if (v.isObject()) {
                            Map<String, Object> out = new LinkedHashMap<>();
                            v.fields().forEachRemaining(e -> out.put(e.getKey(), canonical(e.getValue())));
                            return out;
                        }
                        return v.toString();
                    }

                    private static double numberValue(JsonNode v) {
                        if (isMarker(v, "__minusZero")) return -0.0d;
                        if (isMarker(v, "__nan")) return Double.NaN;
                        if (isMarker(v, "__infinity")) {
                            return v.path("__infinity").asInt() < 0
                                    ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
                        }
                        return v.asDouble();
                    }

                    // ---- comparison ----------------------------------------------------

                    private static void compare(String desc, JsonNode expected, Object actual) {
                        if (expected == null || expected.isNull() || isMarker(expected, "__undefined")) {
                            assertEquals(null, actual, desc);
                            return;
                        }
                        if (isMarker(expected, "__function") || isMarker(expected, "__symbol")
                                || isMarker(expected, "__deep")) {
                            throw new AssertionError(desc
                                    + ": recorded value is unportable (function/symbol/deep); "
                                    + "restructure the ported function to return data, not code.");
                        }
                        if (isMarker(expected, "__date")) {
                            assertEquals(expected.path("__date").asText(), String.valueOf(actual), desc);
                            return;
                        }
                        if (isMarker(expected, "__error")) {
                            throw new AssertionError(desc
                                    + ": recorded an error object as a VALUE (__error); the original "
                                    + "returned an Error instead of throwing. Compare manually.");
                        }
                        if (expected.isNumber()) {
                            double e = numberValue(expected);
                            assertTrue(actual instanceof Number, desc + ": expected number " + e
                                    + " but port returned " + typeName(actual));
                            double a = ((Number) actual).doubleValue();
                            if (Double.isNaN(e)) {
                                assertTrue(Double.isNaN(a), desc + ": expected NaN, got " + a);
                                return;
                            }
                            if (e == 0.0d && a == 0.0d) {
                                assertEquals(Double.doubleToLongBits(e), Double.doubleToLongBits(a),
                                        desc + ": zero sign mismatch (-0 vs 0)");
                                return;
                            }
                            assertEquals(e, a, desc);
                            return;
                        }
                        if (expected.isTextual()) {
                            assertEquals(expected.asText(), String.valueOf(actual), desc);
                            return;
                        }
                        if (expected.isBoolean()) {
                            assertEquals(expected.asBoolean(), actual, desc);
                            return;
                        }
                        if (isMarker(expected, "__bigint")) {
                            BigDecimal eb = new BigDecimal(expected.path("__bigint").asText());
                            BigDecimal ab = actual instanceof BigDecimal bd ? bd
                                    : new BigDecimal(String.valueOf(actual));
                            assertEquals(0, eb.compareTo(ab), desc);
                            return;
                        }
                        if (expected.isArray()) {
                            int len = expected.size();
                            assertTrue(actual instanceof Collection || actual != null && actual.getClass().isArray(),
                                    desc + ": expected array of " + len + " items but port returned "
                                            + typeName(actual));
                            int actualLen = actual instanceof Collection<?> coll
                                    ? coll.size() : Array.getLength(actual);
                            assertEquals(len, actualLen, desc + ": array length");
                            List<Object> actualList = actual instanceof Collection<?>
                                    ? new ArrayList<Object>((Collection<?>) actual) : toList(actual);
                            for (int i = 0; i < len; i++) {
                                compare(desc + "[" + i + "]", expected.get(i), actualList.get(i));
                            }
                            return;
                        }
                        if (expected.isObject()) {
                            assertTrue(actual instanceof Map, desc + ": expected object but port returned "
                                    + typeName(actual));
                            Map<?, ?> map = (Map<?, ?>) actual;
                            List<String> keys = new ArrayList<>();
                            expected.fieldNames().forEachRemaining(keys::add);
                            assertEquals(new java.util.LinkedHashSet<Object>(keys),
                                    map.keySet(), desc + ": object keys");
                            for (String k : keys) {
                                compare(desc + "." + k, expected.path(k), map.get(k));
                            }
                            return;
                        }
                        assertEquals(expected.toString(), String.valueOf(actual), desc);
                    }

                    private static List<Object> toList(Object array) {
                        int len = Array.getLength(array);
                        List<Object> out = new ArrayList<>(len);
                        for (int i = 0; i < len; i++) {
                            out.add(Array.get(array, i));
                        }
                        return out;
                    }

                    private static String typeName(Object o) {
                        return o == null ? "null" : o.getClass().getName() + " " + o;
                    }

                %s}
                """.formatted(pkg, portClass, unitClass, casesPath.replace("\\", "\\\\"),
                portClass, tests.toString());
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("cases", new Tool.StringSchema()
                .description("Path to the .jsonl cases file recorded by migrate_record"));
        schema.prop("class", new Tool.StringSchema()
                .description("Java port class: simple name (with 'package') or fully-qualified"));
        schema.prop("package", new Tool.StringSchema()
                .description("Package for the generated test (default: derived from 'class', "
                        + "else com.migrated)"));
        schema.prop("project", new Tool.StringSchema()
                .description("Generated Maven project root; the test is written to "
                        + "src/test/java/<package>/ (default: next to the cases file)"));
        schema.prop("output", new Tool.StringSchema()
                .description("Explicit .java file path for the test (must end with .java)"));
        return schema;
    }
}
