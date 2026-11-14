package com.javabytes.mcp.tools.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.config.AppConfig;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.PathValidator;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Translates the safely-typed subset of a JavaScript/TypeScript file into a
 * compilable Java class. Everything outside that subset is reported, not
 * silently mistranslated.
 */
public class TranslateJavaScriptTool implements Tool {

    @Override
    public String getName() { return "translate_javascript"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "translate_javascript",
                "Translate JavaScript to Java",
                "Translate the safely typed subset of JS/TS to a compilable Java class and report everything that needs manual work.",
                "codegen",
                false,
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
        String className = params.has("class_name")
                ? params.get("class_name").asText("TranslatedCode")
                : "TranslatedCode";
        String packageName = params.has("package_name")
                ? params.get("package_name").asText("com.example")
                : "com.example";
        String writeTo = params.has("write_to") ? params.get("write_to").asText().trim() : "";

        try {
            String source;
            String sourceName;
            if (code != null) {
                source = code;
                sourceName = params.has("file_name")
                        ? params.get("file_name").asText("input.js")
                        : "input.js";
            } else {
                if (pathParam.isEmpty()) {
                    return ErrorHelper.error("MISSING_PARAM",
                            "Provide either 'path' or 'code'");
                }
                Path path = Path.of(pathParam);
                if (!PathValidator.isAllowed(path)) {
                    return ErrorHelper.error("PATH_NOT_ALLOWED",
                            "Path not allowed: " + pathParam + ". Allowed roots: "
                                    + AppConfig.describeAllowedDirectories());
                }
                if (!Files.isRegularFile(path)) {
                    return ErrorHelper.error("FILE_NOT_FOUND", "File not found: " + pathParam);
                }
                source = Files.readString(path);
                sourceName = path.toString();
            }

            JsToJavaTranslator.Result result =
                    JsToJavaTranslator.translate(source, className, packageName);
            ObjectNode out = result.report();
            out.put("success", true);
            out.put("source", sourceName);
            out.put("java_source", result.javaSource());

            if (!writeTo.isEmpty()) {
                Path target = Path.of(writeTo);
                if (!PathValidator.isAllowed(target)) {
                    return ErrorHelper.error("PATH_NOT_ALLOWED",
                            "write_to is outside the sandbox: " + writeTo + ". Allowed roots: "
                                    + AppConfig.describeAllowedDirectories());
                }
                if (Files.isDirectory(target)) {
                    target = target.resolve(out.path("class_name").asText() + ".java");
                } else if (!target.getFileName().toString().endsWith(".java")) {
                    target = target.resolveSibling(target.getFileName() + ".java");
                }
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                Files.writeString(target, result.javaSource());
                out.put("written_to", target.toAbsolutePath().toString());
            }
            return out;
        } catch (Exception e) {
            return ErrorHelper.error("TRANSLATE_ERROR",
                    "Error translating JavaScript: " + e.getMessage());
        }
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("path", new Tool.StringSchema()
                .description("JavaScript/TypeScript file to translate"));
        schema.prop("code", new Tool.StringSchema()
                .description("Inline source to translate instead of reading a path"));
        schema.prop("file_name", new Tool.StringSchema()
                .description("Virtual file name for inline code (default input.js)"));
        schema.prop("class_name", new Tool.StringSchema()
                .description("Java class name for the output (default TranslatedCode)"));
        schema.prop("package_name", new Tool.StringSchema()
                .description("Java package for the output (default com.example)"));
        schema.prop("write_to", new Tool.StringSchema()
                .description("Optional .java file or directory inside the sandbox; omit to return text only"));
        return schema;
    }
}
