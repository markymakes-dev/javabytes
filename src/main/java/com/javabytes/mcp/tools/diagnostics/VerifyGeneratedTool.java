package com.javabytes.mcp.tools.diagnostics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic verification of generated Java code against antislop rules.
 * Runs as a pipeline gate before the "Prove" step (compile + start).
 * <p>
 * Checks:
 * <ul>
 *   <li>No {@code // TODO}, {@code // FIXME}, {@code // HACK} comments in generated source</li>
 *   <li>All records are {@code final} / effectively immutable</li>
 *   <li>No raw types (e.g., {@code List} instead of {@code List<String>})</li>
 *   <li>Explicit nullability annotations ({@code @Nullable} / {@code @NonNull}) on all params/returns</li>
 *   <li>No {@code Object} as return type when a specific type is known</li>
 *   <li>No {@code var} in field declarations (explicit types required)</li>
 *   <li>All public APIs have Javadoc</li>
 * </ul>
 * <p>
 * Category: {@code diagnostics} (enabled by default).
 */
public class VerifyGeneratedTool implements Tool {

    private static final String NAME = "verify_generated";

    // Patterns for TODO/FIXME/HACK comments
    private static final Pattern TODO_PATTERN = Pattern.compile("//\\s*(TODO|FIXME|HACK)\\b");

    // Pattern for record declarations (should be final/immutable)
    private static final Pattern RECORD_PATTERN = Pattern.compile("\\brecord\\s+(\\w+)\\s*\\{");

    // Pattern for raw types: List, Set, Map, Collection, Iterator, Optional without type args
    private static final Pattern RAW_TYPE_PATTERN = Pattern.compile("\\b(List|Set|Map|Collection|Iterator|Optional)\\s*[^<]");

    // Pattern for field declarations with var
    private static final Pattern VAR_FIELD_PATTERN = Pattern.compile("(?:private|protected|public)\\s+var\\s+\\w+\\s*[=;]");

    // Pattern for public method/class without Javadoc
    private static final Pattern PUBLIC_NO_JAVADOC_PATTERN = Pattern.compile(
            "(?:^|\\n)\\s*(?:public|protected)\\s+(?:\\w+\\s+)+(\\w+)\\s*\\([^)]*\\)\\s*\\{",
            Pattern.MULTILINE);

    // Pattern for @Nullable/@NonNull annotations
    private static final Pattern NULLABLE_ANNOTATION = Pattern.compile("@(?:Nullable|NonNull)\\b");

    // Pattern for Object return type (simplified - catches explicit Object returns)
    private static final Pattern OBJECT_RETURN_PATTERN = Pattern.compile("\\bObject\\s+\\w+\\s*\\([^)]*\\)\\s*\\{");

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                NAME,
                "Verify Generated Code",
                "Deterministic antislop gate: checks generated Java files for TODO comments, raw types, "
                        + "missing nullability annotations, var in fields, missing Javadoc, and Object return types. "
                        + "Runs as a pipeline gate before Prove (compile + start).",
                "diagnostics",
                true,  // enabledByDefault = true (capability tool)
                true,  // readOnlyHint
                false, // destructiveHint
                true,  // idempotentHint
                false  // openWorldHint
        );
    }

    @Override
    public JsonNode execute(JsonNode params) {
        JsonNode filesNode = params.get("files");
        if (filesNode == null || !filesNode.isObject()) {
            return ErrorHelper.error("INVALID_PARAMS", "Expected 'files' object mapping path -> content");
        }

        try {
            ArrayNode violations = JsonUtils.MAPPER.createArrayNode();
            java.util.concurrent.atomic.AtomicInteger totalFiles = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.concurrent.atomic.AtomicInteger filesWithViolations = new java.util.concurrent.atomic.AtomicInteger(0);

            filesNode.fields().forEachRemaining(entry -> {
                String path = entry.getKey();
                String content = entry.getValue().asText();
                totalFiles.incrementAndGet();

                if (!path.endsWith(".java")) {
                    return; // Only check Java files
                }

                ArrayNode fileViolations = checkFile(path, content);
                if (fileViolations.size() > 0) {
                    filesWithViolations.incrementAndGet();
                    ObjectNode fileResult = JsonUtils.MAPPER.createObjectNode();
                    fileResult.put("path", path);
                    fileResult.set("violations", fileViolations);
                    violations.add(fileResult);
                }
            });

            boolean passed = violations.size() == 0;

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("passed", passed);
            result.put("totalFilesChecked", totalFiles.get());
            result.put("filesWithViolations", filesWithViolations.get());
            result.put("totalViolations", violations.size());
            result.set("violations", violations);
            result.put("message", passed
                    ? "All generated files pass antislop gate"
                    : "Antislop gate failed: " + violations.size() + " violation(s) in " + filesWithViolations.get() + " file(s)");
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("VERIFICATION_ERROR", "Error verifying generated code: " + e.getMessage());
        }
    }

    private ArrayNode checkFile(String path, String content) {
        ArrayNode violations = JsonUtils.MAPPER.createArrayNode();

        // 1. No TODO/FIXME/HACK comments
        checkPattern(content, TODO_PATTERN, "TODO_COMMENT",
                "Generated code must not contain TODO, FIXME, or HACK comments", violations);

        // 2. No raw types (List, Set, Map, Collection, Iterator, Optional without type args)
        checkPattern(content, RAW_TYPE_PATTERN, "RAW_TYPE",
                "Raw type detected — use parameterized type (e.g., List<String>, not List)", violations);

        // 3. No var in field declarations
        checkPattern(content, VAR_FIELD_PATTERN, "VAR_IN_FIELD",
                "Field declarations must use explicit types, not var", violations);

        // 4. No Object as return type when specific type is known
        checkPattern(content, OBJECT_RETURN_PATTERN, "OBJECT_RETURN_TYPE",
                "Return type 'Object' is too generic — use specific type", violations);

        // 5. Public methods/classes should have Javadoc (simplified check)
        checkPublicApiJavadoc(content, violations);

        // 6. Records should be effectively immutable (they are by default in Java, but check for mutable fields)
        checkRecordMutability(content, violations);

        // 7. Nullability annotations on public API (simplified - check presence)
        checkNullabilityAnnotations(path, content, violations);

        return violations;
    }

    private void checkPattern(String content, Pattern pattern, String type, String message, ArrayNode violations) {
        Matcher matcher = pattern.matcher(content);
        int lineNumber = 1;
        int lastMatchEnd = 0;

        while (matcher.find()) {
            // Calculate line number
            for (int i = lastMatchEnd; i < matcher.start(); i++) {
                if (content.charAt(i) == '\n') {
                    lineNumber++;
                }
            }
            lastMatchEnd = matcher.end();

            ObjectNode violation = JsonUtils.MAPPER.createObjectNode();
            violation.put("type", type);
            violation.put("line", lineNumber);
            violation.put("message", message);
            violation.put("snippet", extractSnippet(content, matcher.start(), matcher.end()));
            violations.add(violation);
        }
    }

    private void checkPublicApiJavadoc(String content, ArrayNode violations) {
        // Simplified: check public methods have preceding /** ... */
        // This is a heuristic - real check would need proper parsing
        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("public ") || line.startsWith("protected ")) {
                // Check if previous non-empty line is Javadoc end
                boolean hasJavadoc = false;
                for (int j = i - 1; j >= 0; j--) {
                    String prev = lines[j].trim();
                    if (prev.isEmpty()) continue;
                    if (prev.endsWith("*/")) {
                        hasJavadoc = true;
                    }
                    break;
                }
                if (!hasJavadoc && (line.contains("class ") || line.contains("interface ") || line.contains("("))) {
                    ObjectNode violation = JsonUtils.MAPPER.createObjectNode();
                    violation.put("type", "MISSING_JAVADOC");
                    violation.put("line", i + 1);
                    violation.put("message", "Public API missing Javadoc comment");
                    violation.put("snippet", line.length() > 120 ? line.substring(0, 120) + "..." : line);
                    violations.add(violation);
                }
            }
        }
    }

    private void checkRecordMutability(String content, ArrayNode violations) {
        Matcher matcher = RECORD_PATTERN.matcher(content);
        while (matcher.find()) {
            String recordName = matcher.group(1);
            // Find the record body
            int bodyStart = matcher.end();
            int braceCount = 1;
            int bodyEnd = bodyStart;
            while (bodyEnd < content.length() && braceCount > 0) {
                char c = content.charAt(bodyEnd);
                if (c == '{') braceCount++;
                else if (c == '}') braceCount--;
                bodyEnd++;
            }
            if (bodyEnd > bodyStart) {
                String recordBody = content.substring(bodyStart, bodyEnd - 1);
                // Check for mutable collections in record components
                if (recordBody.contains("List<") || recordBody.contains("Set<") || recordBody.contains("Map<")
                        || recordBody.contains("Collection<") || recordBody.contains("ArrayList")
                        || recordBody.contains("HashSet") || recordBody.contains("HashMap")) {
                    // Not a violation per se, but worth noting - records with mutable components are effectively mutable
                    ObjectNode violation = JsonUtils.MAPPER.createObjectNode();
                    violation.put("type", "RECORD_MUTABLE_COMPONENT");
                    violation.put("line", content.substring(0, matcher.start()).split("\n").length);
                    violation.put("message", "Record '" + recordName + "' has mutable collection component — consider immutable alternatives");
                    violation.put("snippet", extractSnippet(content, matcher.start(), matcher.end()));
                    violations.add(violation);
                }
            }
        }
    }

    private void checkNullabilityAnnotations(String path, String content, ArrayNode violations) {
        // Simplified: check public methods have @Nullable/@NonNull on params/returns
        // This is a heuristic - a real implementation would use a Java parser
        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if ((line.startsWith("public ") || line.startsWith("protected "))
                    && line.contains("(")
                    && line.contains(")")
                    && !line.startsWith("@")) {
                // Check if method has nullability annotations
                boolean hasNullability = NULLABLE_ANNOTATION.matcher(content.substring(
                        Math.max(0, content.indexOf(line) - 200),
                        content.indexOf(line) + line.length())).find();

                // Check return type area
                if (!hasNullability && (line.contains("public ") || line.contains("protected "))) {
                    ObjectNode violation = JsonUtils.MAPPER.createObjectNode();
                    violation.put("type", "MISSING_NULLABILITY");
                    violation.put("line", i + 1);
                    violation.put("message", "Public method should have @Nullable/@NonNull annotations on parameters and return type");
                    violation.put("snippet", line.length() > 120 ? line.substring(0, 120) + "..." : line);
                    violations.add(violation);
                }
            }
        }
    }

    private String extractSnippet(String content, int start, int end) {
        int snippetStart = Math.max(0, start - 40);
        int snippetEnd = Math.min(content.length(), end + 40);
        String snippet = content.substring(snippetStart, snippetEnd);
        return snippet.replace("\n", " ").replace("\r", " ").trim();
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("files", new Tool.StringSchema()
                .description("Object mapping file paths to their content (only .java files are checked)"));
        schema.required("files");
        return schema;
    }
}