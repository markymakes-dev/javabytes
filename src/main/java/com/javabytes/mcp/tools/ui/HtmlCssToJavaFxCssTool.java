package com.javabytes.mcp.tools.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts a subset of HTML/CSS into JavaFX CSS.
 *
 * <p>Properties are matched on whole property names, so {@code background-color}
 * can never be mangled into {@code background--fx-text-fill}. Unsupported
 * properties are left untouched and reported back to the caller instead of
 * being emitted as invalid JavaFX declarations.</p>
 */
public class HtmlCssToJavaFxCssTool implements Tool {

    private static final Map<String, List<String>> PROPERTY_MAP = new LinkedHashMap<>();

    static {
        map("color", "-fx-text-fill");
        map("background-color", "-fx-background-color");
        map("background", "-fx-background-color");
        map("background-image", "-fx-background-image");
        map("border-color", "-fx-border-color");
        map("border-top-color", "-fx-border-color");
        map("border-width", "-fx-border-width");
        map("border-style", "-fx-border-style");
        map("border-radius", "-fx-background-radius", "-fx-border-radius");
        map("padding", "-fx-padding");
        map("padding-top", "-fx-padding");
        map("padding-right", "-fx-padding");
        map("padding-bottom", "-fx-padding");
        map("padding-left", "-fx-padding");
        map("font-family", "-fx-font-family");
        map("font-size", "-fx-font-size");
        map("font-weight", "-fx-font-weight");
        map("font-style", "-fx-font-style");
        map("text-align", "-fx-text-alignment");
        map("width", "-fx-pref-width");
        map("min-width", "-fx-min-width");
        map("max-width", "-fx-max-width");
        map("height", "-fx-pref-height");
        map("min-height", "-fx-min-height");
        map("max-height", "-fx-max-height");
        map("opacity", "-fx-opacity");
        map("cursor", "-fx-cursor");
        map("visibility", "-fx-visibility");
        map("filter", "-fx-effect");
    }

    private static void map(String htmlProperty, String... javafxProperties) {
        PROPERTY_MAP.put(htmlProperty, List.of(javafxProperties));
    }

    private static final Pattern DECLARATION = Pattern.compile(
            "(^|[;{])\\s*([a-zA-Z-]+)\\s*:\\s*([^;{}]+)", Pattern.MULTILINE);
    private static final Pattern STYLE_BLOCK = Pattern.compile(
            "<style[^>]*>(.*?)</style>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Override
    public String getName() { return "html_css_to_javafx_css"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String html = params.has("html") ? params.get("html").asText() : "";
        String css = params.has("css") ? params.get("css").asText() : "";

        try {
            List<String> warnings = new ArrayList<>();
            if (css.isBlank() && !html.isBlank()) {
                css = extractStyleBlocks(html);
                if (css.isBlank()) {
                    return ErrorHelper.error("NO_CSS", "No CSS supplied and no <style> block found in the HTML.");
                }
            }

            Set<String> unsupported = new LinkedHashSet<>();
            String javafxCss = convertToJavaFxCss(css, unsupported);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("css", javafxCss);
            result.put("message", "Converted CSS to JavaFX format");
            ArrayNode unsupportedNode = JsonUtils.MAPPER.createArrayNode();
            unsupported.forEach(unsupportedNode::add);
            result.set("unsupported_properties", unsupportedNode);
            result.set("warnings", JsonUtils.MAPPER.valueToTree(warnings));
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("CONVERT_ERROR", "Error converting CSS: " + e.getMessage());
        }
    }

    private String extractStyleBlocks(String html) {
        StringBuilder out = new StringBuilder();
        Matcher m = STYLE_BLOCK.matcher(html);
        while (m.find()) {
            out.append(m.group(1)).append('\n');
        }
        return out.toString();
    }

    private String convertToJavaFxCss(String css, Set<String> unsupported) {
        String converted = DECLARATION.matcher(css).replaceAll(match -> {
            String prefix = match.group(1);
            String property = match.group(2).toLowerCase();
            String value = match.group(3).trim().replace("!important", "").trim();

            List<String> javafxProperties = PROPERTY_MAP.get(property);
            if (javafxProperties == null) {
                if (isPresentational(property)) {
                    unsupported.add(property);
                }
                return Matcher.quoteReplacement(match.group(0));
            }

            StringBuilder replacement = new StringBuilder(prefix).append(' ');
            for (int i = 0; i < javafxProperties.size(); i++) {
                if (i > 0) {
                    replacement.append("; ");
                }
                replacement.append(javafxProperties.get(i)).append(": ").append(value);
            }
            return Matcher.quoteReplacement(replacement.toString().stripTrailing());
        });

        StringBuilder body = new StringBuilder(converted.trim());
        if (!unsupported.isEmpty()) {
            body.insert(0, "/* Unsupported in JavaFX (left unchanged above): "
                    + String.join(", ", unsupported) + " */\n");
        }
        return "/* Converted from HTML/CSS to JavaFX CSS */\n\n" + body + "\n";
    }

    /**
     * Only report properties that look like CSS presentation rules, so selectors
     * and unknown pseudo-classes do not show up as "unsupported".
     */
    private boolean isPresentational(String property) {
        return switch (property) {
            case "margin", "margin-top", "margin-right", "margin-bottom", "margin-left",
                 "display", "position", "top", "right", "bottom", "left", "float",
                 "flex", "flex-direction", "justify-content", "align-items",
                 "gap", "box-shadow", "line-height", "letter-spacing", "word-spacing",
                 "text-decoration", "text-transform", "z-index", "overflow",
                 "box-sizing", "transform", "transition", "animation" -> true;
            default -> false;
        };
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("html", new Tool.StringSchema()
                .description("Optional HTML source; its <style> blocks are used when css is empty"));
        schema.prop("css", new Tool.StringSchema().description("CSS to convert to JavaFX CSS"));
        return schema;
    }
}
