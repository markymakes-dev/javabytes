package com.javabytes.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.javabytes.mcp.skills.database.DescribeTableSkill;
import com.javabytes.mcp.database.MyDatabaseClient;
import com.javabytes.mcp.skills.ui.GenerateUICodeSkill;
import com.javabytes.mcp.skills.ui.HtmlCssToJavaFxCssSkill;
import com.javabytes.mcp.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillsRegressionTest {

    @Test
    void cssConversionKeepsPropertyNamesIntact() {
        JsonNode result = new HtmlCssToJavaFxCssSkill().execute(
                JsonUtils.MAPPER.createObjectNode()
                        .put("css", ".btn { color: blue; background-color: red; margin: 4px; }"));

        assertTrue(result.get("success").asBoolean(), result.toString());
        String css = result.get("css").asText();
        assertTrue(css.contains("-fx-background-color: red"), css);
        assertTrue(css.contains("-fx-text-fill: blue"), css);
        assertFalse(css.contains("background--fx"), css);
        assertFalse(css.contains("-fx-margin"), css);
        assertEquals("margin", result.get("unsupported_properties").get(0).asText());
    }

    @Test
    void cssConversionExtractsStyleBlockFromHtml() {
        JsonNode result = new HtmlCssToJavaFxCssSkill().execute(
                JsonUtils.MAPPER.createObjectNode()
                        .put("html", "<style>.x { font-size: 14px; }</style>"));

        assertTrue(result.get("success").asBoolean(), result.toString());
        assertTrue(result.get("css").asText().contains("-fx-font-size: 14px"));
    }

    @Test
    void generatedUiCodeIsValidJava() {
        JsonNode result = new GenerateUICodeSkill().execute(
                JsonUtils.MAPPER.createObjectNode()
                        .put("component", "button")
                        .put("variant", "filled")
                        .put("colorScheme", "primary")
                        .put("designSystem", "Material 3"));

        assertTrue(result.get("success").asBoolean(), result.toString());
        String code = result.get("code").asText();
        assertTrue(code.contains("getStyleClass().addAll(\"button\", \"button-filled\""));
        assertFalse(code.contains(".append("), code);
        assertTrue(code.contains("class GeneratedButton"), code);
        assertTrue(code.contains("\"design-material-3\""), code);
        assertTrue(result.get("css").asText().contains("material-3"), result.get("css").asText());
    }

    @Test
    void describeTableRejectsSqlInjectionAttempts() {
        JsonNode result = new DescribeTableSkill().execute(
                JsonUtils.MAPPER.createObjectNode().put("table_name", "users; DROP TABLE users"));

        assertFalse(result.get("success").asBoolean());
        assertEquals("INVALID_IDENTIFIER", result.get("error").asText());
    }

    @Test
    void databaseClientRejectsWritesBeforeConnecting() {
        MyDatabaseClient client = new MyDatabaseClient("jdbc:mysql://localhost:1/none", "nobody", "nobody");

        JsonNode delete = client.query("DELETE FROM users");
        assertFalse(delete.get("success").asBoolean());
        assertEquals("read_only_violation", delete.get("error").asText());

        JsonNode stacked = client.query("SELECT 1; DROP TABLE users");
        assertFalse(stacked.get("success").asBoolean());
        assertEquals("multiple_statements", stacked.get("error").asText());
    }

    @Test
    void databaseErrorResponsesAreMarkedAsFailures() {
        MyDatabaseClient client = new MyDatabaseClient("jdbc:mysql://localhost:1/none", "nobody", "nobody");
        JsonNode result = client.query("SELECT 1");
        assertFalse(result.get("success").asBoolean());
        assertEquals("database_error", result.get("error").asText());
    }
}
