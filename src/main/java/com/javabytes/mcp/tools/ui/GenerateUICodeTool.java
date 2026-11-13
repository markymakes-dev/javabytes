package com.javabytes.mcp.tools.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

import java.util.Locale;

/**
 * Generates small, compilable JavaFX snippets.
 *
 * <p>All user-supplied values are sanitized before being placed into generated
 * source, so the output is valid Java regardless of the variant or design-system
 * name that was requested.</p>
 */
public class GenerateUICodeTool implements Tool {

    @Override
    public String getName() { return "generate_ui_code"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String component = params.has("component") ? params.get("component").asText() : "button";
        String variant = params.has("variant") ? params.get("variant").asText() : "filled";
        String colorScheme = params.has("colorScheme") ? params.get("colorScheme").asText() : "primary";
        String designSystem = params.has("designSystem") ? params.get("designSystem").asText() : "custom";

        try {
            String code = generateComponentCode(component, variant, colorScheme, designSystem);
            String css = generateCss(component, variant, colorScheme, designSystem);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("component", component);
            result.put("variant", variant);
            result.put("colorScheme", colorScheme);
            result.put("designSystem", designSystem);
            result.put("code", code);
            result.put("css", css);
            result.put("library", "JavaFX");
            result.put("message", "Generated UI code for " + component);
            return result;
        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_UI_ERROR", "Error generating UI code: " + e.getMessage());
        }
    }

    private String generateComponentCode(String component, String variant, String colorScheme, String designSystem) {
        String type = component.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (type.isEmpty()) {
            type = "custom";
        }
        String className = "Generated" + capitalize(type);
        String styleClasses = styleClassList(type, variant, colorScheme, designSystem);

        StringBuilder code = new StringBuilder();
        code.append("// Component: ").append(component).append('\n');
        code.append("// Variant: ").append(variant).append('\n');
        code.append("// Color scheme: ").append(colorScheme).append('\n');
        code.append("// Design system: ").append(designSystem).append("\n\n");

        switch (type) {
            case "button" -> {
                code.append("import javafx.scene.control.Button;\n\n");
                code.append("public final class ").append(className).append(" {\n\n");
                code.append("    private ").append(className).append("() {\n    }\n\n");
                code.append("    public static Button create() {\n");
                code.append("        Button button = new Button(\"Action\");\n");
                code.append("        button.getStyleClass().addAll(").append(styleClasses).append(");\n");
                code.append("        button.setPrefSize(280, 48);\n");
                code.append("        return button;\n");
                code.append("    }\n");
                code.append("}\n");
            }
            case "textfield" -> {
                code.append("import javafx.scene.control.TextField;\n\n");
                code.append("public final class ").append(className).append(" {\n\n");
                code.append("    private ").append(className).append("() {\n    }\n\n");
                code.append("    public static TextField create() {\n");
                code.append("        TextField field = new TextField();\n");
                code.append("        field.getStyleClass().addAll(").append(styleClasses).append(");\n");
                code.append("        field.setPromptText(\"Enter text\");\n");
                code.append("        field.setPrefWidth(320);\n");
                code.append("        return field;\n");
                code.append("    }\n");
                code.append("}\n");
            }
            case "card" -> {
                code.append("import javafx.scene.control.Label;\n");
                code.append("import javafx.scene.layout.VBox;\n\n");
                code.append("public final class ").append(className).append(" {\n\n");
                code.append("    private ").append(className).append("() {\n    }\n\n");
                code.append("    public static VBox create(String title, String body) {\n");
                code.append("        Label heading = new Label(title);\n");
                code.append("        heading.getStyleClass().add(\"card-title\");\n");
                code.append("        Label content = new Label(body);\n");
                code.append("        content.setWrapText(true);\n");
                code.append("        content.getStyleClass().add(\"card-body\");\n");
                code.append("        VBox card = new VBox(8, heading, content);\n");
                code.append("        card.getStyleClass().addAll(").append(styleClasses).append(");\n");
                code.append("        card.setPrefWidth(360);\n");
                code.append("        return card;\n");
                code.append("    }\n");
                code.append("}\n");
            }
            case "dialog" -> {
                code.append("import javafx.scene.control.ButtonType;\n");
                code.append("import javafx.scene.control.Dialog;\n\n");
                code.append("public final class ").append(className).append(" {\n\n");
                code.append("    private ").append(className).append("() {\n    }\n\n");
                code.append("    public static Dialog<ButtonType> create() {\n");
                code.append("        Dialog<ButtonType> dialog = new Dialog<>();\n");
                code.append("        dialog.getDialogPane().getStyleClass().addAll(").append(styleClasses).append(");\n");
                code.append("        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);\n");
                code.append("        return dialog;\n");
                code.append("    }\n");
                code.append("}\n");
            }
            default -> {
                code.append("import javafx.scene.layout.StackPane;\n\n");
                code.append("public final class ").append(className).append(" {\n\n");
                code.append("    private ").append(className).append("() {\n    }\n\n");
                code.append("    public static StackPane create() {\n");
                code.append("        StackPane root = new StackPane();\n");
                code.append("        root.getStyleClass().addAll(").append(styleClasses).append(");\n");
                code.append("        return root;\n");
                code.append("    }\n");
                code.append("}\n");
            }
        }
        return code.toString();
    }

    private String styleClassList(String type, String variant, String colorScheme, String designSystem) {
        String base = sanitizeToken(type);
        String variantToken = base + "-" + sanitizeToken(variant);
        String colorToken = "color-" + sanitizeToken(colorScheme);
        String designToken = "design-" + sanitizeToken(designSystem);
        return quote(base) + ", " + quote(variantToken) + ", " + quote(colorToken) + ", " + quote(designToken);
    }

    private String generateCss(String component, String variant, String colorScheme, String designSystem) {
        String base = sanitizeToken(component.toLowerCase(Locale.ROOT));
        String variantToken = base + "-" + sanitizeToken(variant);
        String color = getPrimaryColor(colorScheme);
        String design = sanitizeToken(designSystem);
        return "/* " + design + " design system, " + variant + " " + component + " */\n"
                + "." + base + " {\n"
                + "    -fx-background-color: " + color + ";\n"
                + "    -fx-text-fill: white;\n"
                + "    -fx-background-radius: 8;\n"
                + "    -fx-padding: 8 16 8 16;\n"
                + "}\n\n"
                + "." + variantToken + " {\n"
                + "    /* variant-specific overrides go here */\n"
                + "}\n";
    }

    private String sanitizeToken(String value) {
        String token = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        token = token.replaceAll("[^a-z0-9-]+", "-").replaceAll("-{2,}", "-");
        token = token.replaceAll("^-+|-+$", "");
        return token.isEmpty() ? "default" : token;
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String getPrimaryColor(String scheme) {
        return switch (scheme == null ? "" : scheme.toLowerCase(Locale.ROOT)) {
            case "blue", "primary" -> "#3b82f6";
            case "green", "success" -> "#22c55e";
            case "red", "danger", "error" -> "#ef4444";
            case "purple" -> "#a855f7";
            case "orange", "warning" -> "#f97316";
            case "teal" -> "#14b8a6";
            case "pink" -> "#ec4899";
            default -> "#3b82f6";
        };
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("component", new Tool.StringSchema().description("Component type, e.g. button, textfield, card, dialog"));
        schema.prop("variant", new Tool.StringSchema().description("Visual variant, e.g. filled, outlined, tonal"));
        schema.prop("colorScheme", new Tool.StringSchema().description("Color scheme, e.g. primary, blue, green"));
        schema.prop("designSystem", new Tool.StringSchema().description("Design-system name used for the style-class prefix"));
        schema.required("component");
        return schema;
    }
}
