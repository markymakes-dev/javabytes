package com.javabytes.mcp.database;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Analyses user-declared functional dependencies (FDs) against the primary key.
 *
 * <p>Only FDs that the caller declares are used. The analyzer computes
 * attribute closures and reports the strongest applicable normal-form problem
 * per dependency: partial dependency (2NF), transitive dependency (3NF), or a
 * non-superkey determinant (BCNF). It never infers FDs from data.</p>
 *
 * <p>Input format is one dependency per line:</p>
 * <pre>
 * orders: customer_id, order_date -&gt; customer_name, customer_email
 * </pre>
 * A line without a table prefix uses the {@code defaultTable} argument.
 * Blank lines and lines starting with {@code #} or {@code --} are ignored.</p>
 */
public final class FunctionalDependencyAnalyzer {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

    public record Dependency(String table, Set<String> determinant,
                             Set<String> dependent, int line) {
    }

    public record Closure(String table, Set<String> determinant,
                          Set<String> closure, boolean superkey) {
    }

    public record Violation(String normalForm, String severity, String table,
                            Set<String> determinant, Set<String> dependent,
                            String evidence, String recommendation,
                            List<String> suggestedRelations) {
    }

    public record Result(List<Dependency> dependencies, List<Closure> closures,
                         List<Violation> violations, List<String> warnings) {
    }

    private FunctionalDependencyAnalyzer() {
    }

    /**
     * Parse and analyze a specification.
     *
     * @param specification newline-separated {@code table: A,B -> C,D} lines
     * @param defaultTable table used for lines without a prefix, may be null
     * @param primaryKeys primary-key columns per table, may be empty
     * @param tableColumns known columns per table, used only for warnings
     * @throws IllegalArgumentException when the specification is malformed
     */
    public static Result analyze(String specification, String defaultTable,
                                 Map<String, Set<String>> primaryKeys,
                                 Map<String, Set<String>> tableColumns) {
        List<Dependency> dependencies = parse(specification, defaultTable);

        Map<String, List<Dependency>> byTable = new LinkedHashMap<>();
        for (Dependency dependency : dependencies) {
            byTable.computeIfAbsent(dependency.table(), key -> new ArrayList<>())
                    .add(dependency);
        }

        List<Closure> closures = new ArrayList<>();
        List<Violation> violations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (Map.Entry<String, List<Dependency>> entry : byTable.entrySet()) {
            String table = entry.getKey();
            List<Dependency> tableDependencies = entry.getValue();

            Set<String> universe = new TreeSet<>();
            for (Dependency dependency : tableDependencies) {
                universe.addAll(dependency.determinant());
                universe.addAll(dependency.dependent());
            }

            Set<String> primaryKey = lookup(primaryKeys, table);
            universe.addAll(primaryKey);
            Set<String> nonPrime = new TreeSet<>(universe);
            nonPrime.removeAll(primaryKey);

            if (primaryKey.isEmpty()) {
                warnings.add("No primary key is known for table '" + table
                        + "'; 2NF/3NF checks are skipped and only BCNF is evaluated "
                        + "from the declared dependencies.");
            }
            Set<String> knownColumns = lookup(tableColumns, table);
            if (knownColumns.isEmpty()) {
                warnings.add("Table '" + table + "' was not found in the inspected schema; "
                        + "the analysis uses only the declared attributes.");
            } else {
                Set<String> lowered = new TreeSet<>();
                knownColumns.forEach(column -> lowered.add(column.toLowerCase(Locale.ROOT)));
                for (String attribute : universe) {
                    if (!lowered.contains(attribute.toLowerCase(Locale.ROOT))) {
                        warnings.add("Attribute '" + attribute + "' of table '" + table
                                + "' does not exist in the inspected schema.");
                    }
                }
            }

            for (Dependency dependency : tableDependencies) {
                if (universe.containsAll(dependency.dependent())
                        && dependency.determinant().containsAll(dependency.dependent())) {
                    warnings.add("Line " + dependency.line() + ": dependency "
                            + display(dependency.determinant()) + " -> "
                            + display(dependency.dependent())
                            + " is trivial and was ignored.");
                    continue;
                }

                Set<String> closure = closure(dependency.determinant(), tableDependencies);
                boolean superkey = closure.containsAll(universe);
                closures.add(new Closure(table, dependency.determinant(), closure, superkey));
                if (superkey) {
                    continue;
                }

                boolean partial = !primaryKey.isEmpty()
                        && !dependency.determinant().equals(primaryKey)
                        && primaryKey.containsAll(dependency.determinant());
                Set<String> dependentOnNonPrime = new TreeSet<>(closure);
                dependentOnNonPrime.retainAll(nonPrime);

                if (partial && !dependentOnNonPrime.isEmpty()) {
                    violations.add(violation("2NF", "high", table, dependency, closure,
                            universe,
                            "Declared dependency " + display(dependency.determinant())
                                    + " -> " + display(dependency.dependent())
                                    + " is partial: " + display(dependency.determinant())
                                    + " is a proper subset of the primary key "
                                    + display(primaryKey) + ", so non-key attributes depend on "
                                    + "only part of the key.",
                            "Move the dependent attributes into a child table keyed by "
                                    + display(dependency.determinant())
                                    + "; keep the remaining columns in '" + table + "'."));
                } else if (!dependentOnNonPrime.isEmpty()) {
                    violations.add(violation("3NF", "medium", table, dependency, closure,
                            universe,
                            "Declared dependency " + display(dependency.determinant())
                                    + " -> " + display(dependency.dependent())
                                    + " is transitive: the determinant is not a superkey and "
                                    + "its closure contains non-key attributes "
                                    + display(dependentOnNonPrime) + ".",
                            "Create a table keyed by "
                                    + display(dependency.determinant())
                                    + " holding " + display(dependency.dependent())
                                    + " and remove those columns from '" + table + "'."));
                } else {
                    violations.add(violation("BCNF", "medium", table, dependency, closure,
                            universe,
                            "Declared dependency " + display(dependency.determinant())
                                    + " -> " + display(dependency.dependent())
                                    + " violates BCNF: the determinant is not a superkey of "
                                    + display(universe) + " (closure = "
                                    + display(closure) + ").",
                            "Split '" + table + "' so that "
                                    + display(dependency.determinant())
                                    + " is a key of the table containing "
                                    + display(dependency.dependent()) + "."));
                }
            }
        }

        return new Result(dependencies, closures, violations, warnings);
    }

    /** Parse only, without analyzing. Exposed for validation tests. */
    public static List<Dependency> parse(String specification, String defaultTable) {
        List<Dependency> dependencies = new ArrayList<>();
        if (specification == null || specification.isBlank()) {
            return dependencies;
        }
        int lineNumber = 0;
        for (String raw : specification.split("\\R")) {
            lineNumber++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("--")) {
                continue;
            }

            String table = defaultTable == null ? "" : defaultTable.strip();
            String body = line;
            int colon = line.indexOf(':');
            int arrow = arrowIndex(line);
            if (colon >= 0 && (arrow < 0 || colon < arrow)) {
                table = line.substring(0, colon).strip();
                body = line.substring(colon + 1).strip();
            }
            if (table.isEmpty()) {
                throw new IllegalArgumentException("Line " + lineNumber
                        + ": missing table prefix (expected 'table: A,B -> C,D')");
            }
            if (!IDENTIFIER.matcher(table).matches()) {
                throw new IllegalArgumentException("Line " + lineNumber
                        + ": invalid table name '" + table + "'");
            }

            int separator = arrowIndex(body);
            if (separator < 0) {
                throw new IllegalArgumentException("Line " + lineNumber
                        + ": expected exactly one '->' between determinant and dependent");
            }
            String left = body.substring(0, separator);
            int width = body.startsWith("->", separator) ? 2 : 1;
            String right = body.substring(separator + width);
            if (arrowIndex(right) >= 0) {
                throw new IllegalArgumentException("Line " + lineNumber
                        + ": expected exactly one '->'");
            }

            Set<String> determinant = parseAttributes(left, lineNumber);
            Set<String> dependent = parseAttributes(right, lineNumber);
            if (determinant.isEmpty()) {
                throw new IllegalArgumentException("Line " + lineNumber
                        + ": determinant is empty");
            }
            if (dependent.isEmpty()) {
                throw new IllegalArgumentException("Line " + lineNumber
                        + ": dependent attributes are empty");
            }
            dependencies.add(new Dependency(table, determinant, dependent, lineNumber));
        }
        return dependencies;
    }

    /** Attribute closure of {@code start} under the given dependencies. */
    public static Set<String> closure(Set<String> start, List<Dependency> dependencies) {
        Set<String> result = new TreeSet<>(start);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Dependency dependency : dependencies) {
                if (result.containsAll(dependency.determinant())
                        && result.addAll(dependency.dependent())) {
                    changed = true;
                }
            }
        }
        return result;
    }

    private static Violation violation(String normalForm, String severity, String table,
                                       Dependency dependency, Set<String> closure,
                                       Set<String> universe, String evidence,
                                       String recommendation) {
        List<String> relations = new ArrayList<>();
        Set<String> first = new TreeSet<>(dependency.determinant());
        first.addAll(dependency.dependent());
        Set<String> second = new TreeSet<>(universe);
        Set<String> dependentOnly = new TreeSet<>(dependency.dependent());
        dependentOnly.removeAll(dependency.determinant());
        second.removeAll(dependentOnly);

        relations.add(relationName(table, "1", first));
        if (!second.isEmpty()) {
            relations.add(relationName(table, "2", second));
        }
        return new Violation(normalForm, severity, table, dependency.determinant(),
                dependency.dependent(), evidence, recommendation, relations);
    }

    private static String relationName(String table, String suffix, Set<String> attributes) {
        return table + "_" + suffix + display(attributes);
    }

    private static Set<String> parseAttributes(String raw, int lineNumber) {
        Set<String> attributes = new TreeSet<>();
        for (String part : raw.split(",", -1)) {
            String value = unquote(part.strip());
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Line " + lineNumber
                        + ": empty attribute name (check for a trailing comma)");
            }
            if (!IDENTIFIER.matcher(value).matches()) {
                throw new IllegalArgumentException("Line " + lineNumber
                        + ": invalid attribute name '" + value + "'");
            }
            attributes.add(value);
        }
        return attributes;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '`' && last == '`')
                    || (first == '"' && last == '"')
                    || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1).strip();
            }
        }
        return value;
    }

    private static int arrowIndex(String value) {
        int ascii = value.indexOf("->");
        int unicode = value.indexOf('\u2192');
        if (ascii < 0) {
            return unicode;
        }
        if (unicode < 0) {
            return ascii;
        }
        return Math.min(ascii, unicode);
    }

    private static Set<String> lookup(Map<String, Set<String>> map, String table) {
        if (map == null) {
            return new TreeSet<>();
        }
        Set<String> direct = map.get(table);
        if (direct != null) {
            return new TreeSet<>(direct);
        }
        for (Map.Entry<String, Set<String>> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(table)) {
                return new TreeSet<>(entry.getValue());
            }
        }
        return new TreeSet<>();
    }

    private static String display(Set<String> attributes) {
        return "{" + String.join(", ", attributes) + "}";
    }
}
