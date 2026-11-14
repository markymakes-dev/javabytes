package com.javabytes.mcp.config;

import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ToolMetadata;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Decides which tools are registered, based on {@code JAVABYTES_ENABLE} and
 * {@code JAVABYTES_DISABLE}.
 *
 * <p>Both variables accept a comma-separated list of tool names, category
 * names, or the special value {@code all}. Precedence, highest first:</p>
 *
 * <ol>
 *   <li>a tool disabled by name;</li>
 *   <li>a tool enabled by name;</li>
 *   <li>a disabled category;</li>
 *   <li>an enabled category;</li>
 *   <li>{@code all} in the disable list, then {@code all} in the enable list;</li>
 *   <li>the category's {@code enabledByDefault} flag.</li>
 * </ol>
 *
 * <p>This is the same model the Dart/Flutter MCP server uses, so a user can
 * turn a whole group on while still opting one tool out.</p>
 */
public final class FeatureConfiguration {

    private static final String ALL = "all";
    private static final String STAR = "*";

    private final Set<String> enabledNames;
    private final Set<String> disabledNames;
    private final Set<String> enabledCategories;
    private final Set<String> disabledCategories;
    private final boolean enableAll;
    private final boolean disableAll;

    private FeatureConfiguration(Set<String> enabledNames,
                                 Set<String> disabledNames,
                                 Set<String> enabledCategories,
                                 Set<String> disabledCategories,
                                 boolean enableAll,
                                 boolean disableAll) {
        this.enabledNames = enabledNames;
        this.disabledNames = disabledNames;
        this.enabledCategories = enabledCategories;
        this.disabledCategories = disabledCategories;
        this.enableAll = enableAll;
        this.disableAll = disableAll;
    }

    public static FeatureConfiguration fromEnvironment() {
        return parse(System.getenv("JAVABYTES_ENABLE"), System.getenv("JAVABYTES_DISABLE"));
    }

    /**
     * Parse two comma-separated specifications. Package-visible for tests; use
     * {@link #fromEnvironment()} in production.
     */
    public static FeatureConfiguration parse(String enableSpec, String disableSpec) {
        Builder enable = new Builder("JAVABYTES_ENABLE");
        enable.addAll(enableSpec);

        Builder disable = new Builder("JAVABYTES_DISABLE");
        disable.addAll(disableSpec);

        return new FeatureConfiguration(
                Collections.unmodifiableSet(enable.names),
                Collections.unmodifiableSet(disable.names),
                Collections.unmodifiableSet(enable.categories),
                Collections.unmodifiableSet(disable.categories),
                enable.all,
                disable.all);
    }

    /** A configuration where every tool is enabled, used by tests and tooling. */
    public static FeatureConfiguration allEnabled() {
        return parse(ALL, null);
    }

    public boolean isEnabled(String toolName) {
        // ADR 0006: a tool without a catalog entry is uncataloged and fails
        // closed — disabled, whatever the environment asks for.
        ToolSpec spec = ToolMetadata.get(toolName);
        if (spec == null) {
            return false;
        }
        String name = toolName.toLowerCase(Locale.ROOT);
        String category = spec.category();

        if (disabledNames.contains(name)) {
            return false;
        }
        if (enabledNames.contains(name)) {
            return true;
        }
        if (disabledCategories.contains(category)) {
            return false;
        }
        if (enabledCategories.contains(category)) {
            return true;
        }
        if (disableAll) {
            return false;
        }
        if (enableAll) {
            return true;
        }
        return spec.enabledByDefault();
    }

    /** Short human-readable summary for the startup log. */
    public String describe() {
        StringBuilder out = new StringBuilder();
        if (enableAll) {
            out.append("enable=all");
        } else if (!enabledCategories.isEmpty() || !enabledNames.isEmpty()) {
            out.append("enable=")
                    .append(String.join(",", sortedUnion(enabledCategories, enabledNames)));
        }
        if (disableAll) {
            appendPart(out, "disable=all");
        } else if (!disabledCategories.isEmpty() || !disabledNames.isEmpty()) {
            appendPart(out, "disable=" + String.join(",", sortedUnion(disabledCategories, disabledNames)));
        }
        return out.isEmpty() ? "defaults" : out.toString();
    }

    private static void appendPart(StringBuilder out, String part) {
        if (!out.isEmpty()) {
            out.append(' ');
        }
        out.append(part);
    }

    private static Set<String> sortedUnion(Set<String> a, Set<String> b) {
        Set<String> union = new java.util.TreeSet<>(a);
        union.addAll(b);
        return union;
    }

    private static final class Builder {
        private final String variable;
        private final Set<String> names = new LinkedHashSet<>();
        private final Set<String> categories = new LinkedHashSet<>();
        private boolean all;

        private Builder(String variable) {
            this.variable = variable;
        }

        private void addAll(String spec) {
            if (spec == null || spec.isBlank()) {
                return;
            }
            for (String token : spec.split("[,;\\s]+")) {
                String value = token.trim().toLowerCase(Locale.ROOT);
                if (value.isEmpty()) {
                    continue;
                }
                if (value.equals(ALL) || value.equals(STAR)) {
                    all = true;
                } else if (ToolMetadata.categories().contains(value)) {
                    categories.add(value);
                } else if (ToolMetadata.isKnown(value)) {
                    names.add(value);
                } else {
                    System.err.println("[javabytes] " + variable
                            + ": ignoring unknown tool or category '" + token.trim() + "'");
                }
            }
        }
    }
}
