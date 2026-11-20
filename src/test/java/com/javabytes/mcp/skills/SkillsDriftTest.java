package com.javabytes.mcp.skills;

import com.javabytes.mcp.McpServerMain;
import com.javabytes.mcp.utils.ToolMetadata;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Skills drift regression, mirroring {@code CrewPromptsDriftTest} for the
 * agent skills under {@code skills/}. The skills reference tools by
 * backticked name, but unlike the crew prompts they also backtick
 * parameters, env vars, commands and class names — so a token must be
 * EITHER a known tool OR on the explicit NON_TOOL_TOKENS allowlist below.
 * A tool rename or removal can therefore never silently strand a skill
 * doc, and a brand-new backticked token forces a conscious decision:
 * real tool name, or allowlisted vocabulary with a category.
 */
class SkillsDriftTest {

    private static final Path SKILLS_DIR =
            Path.of(System.getProperty("user.dir"), "skills");
    private static final Path README =
            Path.of(System.getProperty("user.dir"), "README.md");

    private static final Pattern BACKTICK_SPAN = Pattern.compile("`([^`]+)`");
    private static final Pattern TOKEN = Pattern.compile("^[a-z][a-z0-9_]*$");
    private static final Pattern SKILL_NAME = Pattern.compile("^javabytes-[a-z]+$");

    /**
     * Backticked lowercase tokens in the skills that are deliberately NOT
     * tool names, grouped by category. Everything else that matches the
     * token pattern must be in the catalog.
     */
    private static final Set<String> NON_TOOL_TOKENS = Set.of(
            // The MCP server itself, and client-side tools/commands
            "javabytes", "question", "retro", "eval",
            // Binaries and commands
            "javac",
            // JAVABYTES_ENABLE tool-group names (documentation vocabulary)
            "files", "design", "codegen", "javafx", "ui", "springboot",
            // Pipeline phases and strategy/parameter values
            "gate", "record", "replay", "report", "auto",
            "code", "env", "environment", "limitations",
            // Parameter and output-field names
            "functional_dependencies", "report_path", "write_to", "strategy_reason");

    @BeforeAll
    static void deriveCatalog() {
        ToolMetadata.derive(McpServerMain.declaredTools());
    }

    @Test
    void everySkillDirectoryHasASkillMd() throws IOException {
        List<Path> dirs = skillDirectories();
        assertFalse(dirs.isEmpty(), "skills/ must not be empty — did the path move?");
        for (Path dir : dirs) {
            assertTrue(Files.isRegularFile(dir.resolve("SKILL.md")),
                    dir.getFileName() + " has no SKILL.md");
        }
    }

    @Test
    void everyBacktickedTokenIsAKnownToolOrAllowlisted() throws IOException {
        List<String> failures = new ArrayList<>();
        for (Path dir : skillDirectories()) {
            String skill = Files.readString(dir.resolve("SKILL.md"));
            for (String token : backtickedTokens(skill)) {
                if (!ToolMetadata.isKnown(token) && !NON_TOOL_TOKENS.contains(token)) {
                    failures.add(dir.getFileName() + " references unknown token: " + token
                            + " — a tool name drifted, or add it to NON_TOOL_TOKENS with a category");
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void skillsTeachTheCorePipelines() throws IOException {
        Set<String> all = new LinkedHashSet<>();
        for (Path dir : skillDirectories()) {
            all.addAll(backtickedTokens(Files.readString(dir.resolve("SKILL.md"))));
        }
        // Backend loop (javabytes-backend) and migrate loop (javabytes-migrate /
        // javabytes-convert) must keep being taught by name.
        for (String required : List.of("extract_data_models", "models_to_schema",
                "generate_database", "translate_webapp", "verify_generated",
                "analyze_javascript", "translate_javascript", "migrate_plan",
                "migrate_record", "migrate_replay", "migrate_gate", "migrate_report")) {
            assertTrue(all.contains(required),
                    "skills must keep referencing " + required + "; names drifted?");
        }
    }

    @Test
    void readmeListsEverySkillDirectory() throws IOException {
        String readme = Files.readString(README);
        int sectionStart = readme.indexOf("## Agent skills");
        assertTrue(sectionStart >= 0, "README.md lost its '## Agent skills' section");
        String section = readme.substring(sectionStart);
        for (Path dir : skillDirectories()) {
            String name = dir.getFileName().toString();
            assertTrue(section.contains("`" + name + "`"),
                    "README '## Agent skills' section does not list " + name);
        }
    }

    private List<Path> skillDirectories() throws IOException {
        try (Stream<Path> entries = Files.list(SKILLS_DIR)) {
            return entries.filter(Files::isDirectory)
                    .filter(d -> SKILL_NAME.matcher(d.getFileName().toString()).matches())
                    .sorted()
                    .toList();
        }
    }

    /**
     * Tool names in the skills are always single-line inline spans, so
     * matching runs per line with fenced code blocks (``` lines) skipped —
     * matching across the whole file would let a ``` fence open a phantom
     * span that swallows every real token after it.
     */
    private Set<String> backtickedTokens(String skill) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String line : skill.split("\n", -1)) {
            if (line.stripTrailing().startsWith("```")) {
                continue;
            }
            Matcher spans = BACKTICK_SPAN.matcher(line);
            while (spans.find()) {
                String span = spans.group(1).trim();
                if (TOKEN.matcher(span).matches()) {
                    tokens.add(span);
                }
            }
        }
        return tokens;
    }
}
