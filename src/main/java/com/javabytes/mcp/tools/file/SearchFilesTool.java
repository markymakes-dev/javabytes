package com.javabytes.mcp.tools.file;

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
import java.nio.file.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public class SearchFilesTool implements Tool {

    @Override
    public String getName() { return "search_files"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "search_files",
                "Search Files",
                "Find files by glob name pattern and, optionally, a regular expression on their contents.",
                "files",
                true,
                true,
                false,
                true,
                false);
    }

    @Override
    public JsonNode execute(JsonNode params) {
        String directory = params.has("directory")
                ? params.get("directory").asText()
                : System.getProperty("user.dir");
        String pattern = params.has("pattern") ? params.get("pattern").asText() : "*";
        String content = params.has("content") && !params.get("content").isNull()
                ? params.get("content").asText()
                : null;
        boolean recursive = params.has("recursive") && params.get("recursive").asBoolean();

        try {
            Path dir = Path.of(directory);
            if (!PathValidator.isAllowed(dir)) {
                return ErrorHelper.error("PATH_NOT_ALLOWED",
                        "Path not allowed: " + directory + ". Allowed roots: "
                                + AppConfig.describeAllowedDirectories());
            }
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                return ErrorHelper.error("NOT_A_DIRECTORY", "Not a directory: " + directory);
            }

            // Pattern is a glob (e.g. "*.java"), not a regular expression. This matches
            // the behaviour of the official MCP filesystem server (minimatch).
            PathMatcher nameMatcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            Pattern contentPattern = null;
            if (content != null && !content.isBlank()) {
                try {
                    contentPattern = Pattern.compile(content, Pattern.CASE_INSENSITIVE);
                } catch (PatternSyntaxException e) {
                    return ErrorHelper.error("INVALID_CONTENT_PATTERN",
                            "Content pattern is not a valid regular expression: " + e.getDescription());
                }
            }

            ArrayNode rows = JsonUtils.MAPPER.createArrayNode();
            int maxDepth = recursive ? Integer.MAX_VALUE : 1;
            final Pattern contentRegex = contentPattern;

            try (Stream<Path> stream = Files.walk(dir, maxDepth)) {
                stream.filter(Files::isRegularFile).forEach(path -> {
                    try {
                        String fileName = path.getFileName().toString();
                        if (!nameMatcher.matches(path.getFileName())) {
                            return;
                        }
                        if (contentRegex != null) {
                            String text;
                            try {
                                text = Files.readString(path);
                            } catch (IOException | OutOfMemoryError e) {
                                return; // binary or unreadable file
                            }
                            if (!contentRegex.matcher(text).find()) {
                                return;
                            }
                        }
                        ObjectNode row = JsonUtils.MAPPER.createObjectNode();
                        row.put("path", path.toString());
                        row.put("name", fileName);
                        row.put("size_bytes", Files.size(path));
                        row.put("last_modified", Files.getLastModifiedTime(path).toString());
                        row.put("content_match", contentRegex != null);
                        rows.add(row);
                    } catch (IOException e) {
                        // skip unreadable entry
                    }
                });
            }

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("directory", dir.toAbsolutePath().toString());
            result.put("pattern", pattern);
            result.put("content_search", content != null ? content : "none");
            result.put("recursive", recursive);
            result.set("rows", rows);
            result.put("count", rows.size());
            return result;
        } catch (Exception e) {
            return ErrorHelper.error("SEARCH_ERROR", "Error searching files: " + e.getMessage());
        }
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("directory", new Tool.StringSchema().description("Directory to search (defaults to the working directory)"));
        schema.prop("pattern", new Tool.StringSchema().description("Glob pattern matched against the file name, e.g. *.java"));
        schema.prop("content", new Tool.StringSchema().description("Optional regular expression that file contents must match"));
        schema.prop("recursive", new Tool.BoolSchema().description("Recurse into subdirectories (default false)"));
        return schema;
    }
}
