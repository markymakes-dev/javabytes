package com.javabytes.mcp.tools.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

public class GenerateJavaFxComponentTool implements Tool {

    @Override
    public String getName() { return "generate_javafx_component"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String component = params.has("component") ? params.get("component").asText() : "";
        String styleClass = params.has("styleClass") ? params.get("styleClass").asText() : "";

        try {
            String code = generateComponentCode(component, styleClass);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("component", component);
            result.put("styleClass", styleClass);
            result.put("code", code);
            result.put("message", "Generated JavaFX component: " + component);
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_COMPONENT_ERROR", "Error generating component: " + e.getMessage());
        }
    }

    private String generateComponentCode(String component, String styleClass) {
        switch (component.toLowerCase()) {
            case "card":
                return "// JavaFX Card Component\n" +
                        "VBox card = new VBox();\n" +
                        "card.getStyleClass().add(\"card\");\n" +
                        (styleClass.isEmpty() ? "" : "card.getStyleClass().add(\"" + styleClass + "\");\n") +
                        "card.setPrefWidth(360);\n";
            case "grid":
                return "// JavaFX Grid Component\n" +
                        "GridPane grid = new GridPane();\n" +
                        "grid.getStyleClass().add(\"grid\");\n" +
                        (styleClass.isEmpty() ? "" : "grid.getStyleClass().add(\"" + styleClass + "\");\n") +
                        "grid.setHgap(2);\n" +
                        "grid.setVgap(2);\n";
            case "button":
                return "// JavaFX Button Component\n" +
                        "Button btn = new Button(\"Action\");\n" +
                        "btn.getStyleClass().add(\"btn\");\n" +
                        (styleClass.isEmpty() ? "" : "btn.getStyleClass().add(\"" + styleClass + "\");\n") +
                        "btn.setPrefSize(280, 48);\n";
            case "sidebar":
                return "// JavaFX Sidebar Component\n" +
                        "VBox sidebar = new VBox();\n" +
                        "sidebar.getStyleClass().add(\"sidebar\");\n" +
                        (styleClass.isEmpty() ? "" : "sidebar.getStyleClass().add(\"" + styleClass + "\");\n") +
                        "sidebar.setPrefWidth(240);\n";
            default:
                return "// JavaFX Component: " + component + "\n" +
                        "// Style Class: " + styleClass + "\n";
        }
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("component", new Tool.StringSchema().description("Component: card, grid, button, sidebar"));
        schema.prop("styleClass", new Tool.StringSchema().description("CSS style class"));
        schema.required("component");
        return schema;
    }
}
