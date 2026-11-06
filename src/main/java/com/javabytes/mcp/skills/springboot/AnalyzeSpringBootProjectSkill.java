package com.javabytes.mcp.skills.springboot;

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
import java.util.stream.Stream;

/**
 * Read-only analysis of a Spring Boot project layout.
 */
public class AnalyzeSpringBootProjectSkill implements Skill {

    @Override
    public String getName() { return "analyze_springboot_project"; }

    @Override
    public JsonNode execute(JsonNode params) {
        String projectPath = params.has("projectPath") ? params.get("projectPath").asText() : ".";

        try {
            Path path = Path.of(projectPath);
            if (!PathValidator.isAllowed(path)) {
                return ErrorHelper.error("PATH_NOT_ALLOWED",
                        "Path not allowed: " + projectPath + ". Allowed roots: "
                                + AppConfig.describeAllowedDirectories());
            }
            if (!Files.exists(path) || !Files.isDirectory(path)) {
                return ErrorHelper.error("PATH_NOT_FOUND", "Project path not found: " + projectPath);
            }

            String build = "";
            boolean hasPomXml = Files.isRegularFile(path.resolve("pom.xml"));
            boolean hasBuildGradle = Files.isRegularFile(path.resolve("build.gradle"))
                    || Files.isRegularFile(path.resolve("build.gradle.kts"));
            if (hasPomXml) {
                build = Files.readString(path.resolve("pom.xml"));
            } else if (Files.isRegularFile(path.resolve("build.gradle"))) {
                build = Files.readString(path.resolve("build.gradle"));
            } else if (Files.isRegularFile(path.resolve("build.gradle.kts"))) {
                build = Files.readString(path.resolve("build.gradle.kts"));
            }

            boolean hasApplicationProperties = Files.exists(path.resolve("src/main/resources/application.properties"));
            boolean hasApplicationYml = Files.exists(path.resolve("src/main/resources/application.yml"))
                    || Files.exists(path.resolve("src/main/resources/application.yaml"));
            boolean hasMainClass = findMainClass(path);
            boolean declaresSpringBoot = build.contains("spring-boot");

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("projectPath", path.toAbsolutePath().toString());
            result.put("hasPomXml", hasPomXml);
            result.put("hasBuildGradle", hasBuildGradle);
            result.put("hasApplicationProperties", hasApplicationProperties);
            result.put("hasApplicationYml", hasApplicationYml);
            result.put("hasMainClass", hasMainClass);
            result.put("declaresSpringBootDependency", declaresSpringBoot);
            result.put("isSpringBootProject", hasMainClass || declaresSpringBoot);

            ArrayNode recommendations = JsonUtils.MAPPER.createArrayNode();
            if (!hasApplicationProperties && !hasApplicationYml) {
                recommendations.add("Add src/main/resources/application.properties or application.yml");
            }
            if (!hasMainClass) {
                recommendations.add("Add a main class annotated with @SpringBootApplication");
            }
            if (!declaresSpringBoot) {
                recommendations.add("Declare a Spring Boot starter in the build file");
            }
            result.set("recommendations", recommendations);
            result.put("message", "Spring Boot project analyzed");
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("ANALYZE_ERROR", "Error analyzing project: " + e.getMessage());
        }
    }

    private boolean findMainClass(Path path) {
        Path src = path.resolve("src/main/java");
        Path root = Files.isDirectory(src) ? src : path;
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/target/"))
                    .limit(5000)
                    .anyMatch(p -> {
                        try {
                            return Files.readString(p).contains("@SpringBootApplication");
                        } catch (Exception e) {
                            return false;
                        }
                    });
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Skill.JsonSchema getInputSchema() {
        Skill.JsonSchema schema = new Skill.JsonSchema();
        schema.prop("projectPath", new Skill.StringSchema().description("Path to the Spring Boot project (default: working directory)"));
        return schema;
    }
}
