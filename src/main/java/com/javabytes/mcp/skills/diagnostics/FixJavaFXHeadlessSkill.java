package com.javabytes.mcp.skills.diagnostics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.config.AppConfig;
import com.javabytes.mcp.skills.Skill;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.PathValidator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Adds the Monocle headless property to a JavaFX entry point.
 *
 * <p>By default this tool only reports what it would change ({@code dryRun}).
 * Pass {@code apply=true} to write the change.</p>
 */
public class FixJavaFXHeadlessSkill implements Skill {

    @Override
    public String getName() { return "fix_javafx_headless"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String projectPath = params.has("projectPath") ? params.get("projectPath").asText() : ".";
        String mainClass = params.has("mainClass") ? params.get("mainClass").asText().trim() : "";
        boolean apply = params.has("apply") && params.get("apply").asBoolean();

        try {
            Path root = Path.of(projectPath);
            if (!PathValidator.isAllowed(root)) {
                return ErrorHelper.error("PATH_NOT_ALLOWED",
                        "Path not allowed: " + projectPath + ". Allowed roots: "
                                + AppConfig.describeAllowedDirectories());
            }
            Path sourceRoot = Files.isDirectory(root.resolve("src")) ? root.resolve("src") : root;
            if (!Files.isDirectory(sourceRoot)) {
                return ErrorHelper.error("PATH_NOT_FOUND", "Project directory not found: " + projectPath);
            }

            String simpleClassName = mainClass.isEmpty()
                    ? ""
                    : mainClass.substring(mainClass.lastIndexOf('.') + 1);

            ArrayNode fixes = JsonUtils.MAPPER.createArrayNode();
            boolean changed = false;

            for (Path javaFile : collectJavaFiles(sourceRoot)) {
                String content;
                try {
                    content = Files.readString(javaFile);
                } catch (Exception e) {
                    continue;
                }
                boolean isEntryPoint = content.contains("public static void main")
                        || (!simpleClassName.isEmpty() && content.contains("class " + simpleClassName));
                if (!isEntryPoint) {
                    continue;
                }

                ObjectNode fix = JsonUtils.MAPPER.createObjectNode();
                fix.put("file", javaFile.toString());
                if (content.contains("javafx.platform") && content.contains("monocle")) {
                    fix.put("action", "already_configured");
                    fixes.add(fix);
                    continue;
                }

                String patched = addHeadlessProperty(content);
                if (patched.equals(content)) {
                    fix.put("action", "manual_edit_required");
                    fix.put("reason", "Could not locate a class body to insert the static initializer");
                } else if (apply) {
                    Files.writeString(javaFile, patched);
                    fix.put("action", "added_javafx_headless_property");
                    fix.put("property", "javafx.platform=monocle");
                    changed = true;
                } else {
                    fix.put("action", "would_add_javafx_headless_property");
                    fix.put("proposed_static_initializer",
                            "static { System.setProperty(\"javafx.platform\", \"monocle\"); }");
                }
                fixes.add(fix);
            }

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("apply", apply);
            result.set("fixes", fixes);
            result.put("fixCount", fixes.size());
            result.put("message", fixes.isEmpty()
                    ? "No JavaFX entry point found"
                    : changed ? "JavaFX headless property added"
                    : apply ? "Nothing to change" : "Dry run: pass apply=true to write changes");
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("FIX_JAVAFX_ERROR", "Error fixing JavaFX headless config: " + e.getMessage());
        }
    }

    private String addHeadlessProperty(String content) {
        String insertion = "\n    static {\n        System.setProperty(\"javafx.platform\", \"monocle\");\n    }\n";
        int classIdx = content.indexOf("public class ");
        if (classIdx < 0) {
            return content;
        }
        int braceIdx = content.indexOf('{', classIdx);
        if (braceIdx < 0) {
            return content;
        }
        return content.substring(0, braceIdx + 1) + insertion + content.substring(braceIdx + 1);
    }

    private List<Path> collectJavaFiles(Path root) throws Exception {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/target/"))
                    .limit(5000)
                    .forEach(files::add);
        }
        return files;
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.prop("projectPath", new Skill.StringSchema().description("Path to the project directory"));
        schema.prop("mainClass", new Skill.StringSchema().description("Optional main class name to target"));
        schema.prop("apply", new Skill.BoolSchema().description("Write the change; default false performs a dry run"));
        return schema;
    }
}
