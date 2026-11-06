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

/**
 * Suggests Maven dependency fixes based on what the source actually uses.
 * This tool is advisory: it never rewrites pom.xml.
 */
public class FixMavenDependenciesSkill implements Skill {

    @Override
    public String getName() { return "fix_maven_dependencies"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String projectPath = params.has("projectPath") ? params.get("projectPath").asText() : ".";
        String action = params.has("action") ? params.get("action").asText() : "check";

        try {
            Path path = Path.of(projectPath);
            if (!PathValidator.isAllowed(path)) {
                return ErrorHelper.error("PATH_NOT_ALLOWED",
                        "Path not allowed: " + projectPath + ". Allowed roots: "
                                + AppConfig.describeAllowedDirectories());
            }
            Path pomPath = path.resolve("pom.xml");
            if (!Files.isRegularFile(pomPath)) {
                return ErrorHelper.error("POM_NOT_FOUND", "pom.xml not found at " + pomPath);
            }

            ObjectNode inspection = ProjectInspector.inspect(path);
            ArrayNode issues = (ArrayNode) inspection.get("issues");
            ArrayNode fixes = JsonUtils.MAPPER.createArrayNode();
            for (JsonNode issue : issues) {
                String suggestion = dependencyFor(issue.get("code").asText());
                if (suggestion == null) {
                    continue;
                }
                ObjectNode fix = JsonUtils.MAPPER.createObjectNode();
                fix.put("type", "ADD_DEPENDENCY");
                fix.put("code", issue.get("code").asText());
                fix.put("description", issue.get("description").asText());
                fix.put("dependency", suggestion);
                fixes.add(fix);
            }

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("action", action);
            result.put("projectPath", path.toAbsolutePath().toString());
            result.set("fixesNeeded", fixes);
            result.put("fixCount", fixes.size());
            result.put("applied", false);
            result.put("message", fixes.isEmpty()
                    ? "No dependency problems detected"
                    : fixes.size() + " dependency fix(es) suggested; review and add them to pom.xml");
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("FIX_DEPS_ERROR", "Error checking dependencies: " + e.getMessage());
        }
    }

    /** Dependency snippet for an issue code, or null when no Maven fix applies. */
    private String dependencyFor(String code) {
        return switch (code) {
            case "MISSING_SPRING_BOOT" ->
                    "<dependency>\n    <groupId>org.springframework.boot</groupId>\n"
                            + "    <artifactId>spring-boot-starter-web</artifactId>\n</dependency>";
            case "MISSING_JPA" ->
                    "<dependency>\n    <groupId>org.springframework.boot</groupId>\n"
                            + "    <artifactId>spring-boot-starter-data-jpa</artifactId>\n</dependency>";
            case "MISSING_MYSQL_DRIVER", "LEGACY_MYSQL_COORDINATES" ->
                    "<dependency>\n    <groupId>com.mysql</groupId>\n"
                            + "    <artifactId>mysql-connector-j</artifactId>\n    <scope>runtime</scope>\n</dependency>";
            case "MISSING_JAVAFX" ->
                    "<dependency>\n    <groupId>org.openjfx</groupId>\n"
                            + "    <artifactId>javafx-controls</artifactId>\n    <version>25</version>\n</dependency>";
            case "MISSING_JACKSON" ->
                    "<dependency>\n    <groupId>com.fasterxml.jackson.core</groupId>\n"
                            + "    <artifactId>jackson-databind</artifactId>\n</dependency>";
            case "MISSING_JUNIT" ->
                    "<dependency>\n    <groupId>org.junit.jupiter</groupId>\n"
                            + "    <artifactId>junit-jupiter</artifactId>\n    <scope>test</scope>\n</dependency>";
            default -> null;
        };
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.prop("projectPath", new Skill.StringSchema().description("Path to the project directory"));
        schema.prop("action", new Skill.StringSchema().description("check (default) or fix; fixes are suggested, never applied"));
        return schema;
    }
}
