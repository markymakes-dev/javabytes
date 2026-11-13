package com.javabytes.mcp.tools.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.database.MyDatabaseClient;
import com.javabytes.mcp.database.SchemaCatalog;
import com.javabytes.mcp.database.FunctionalDependencyAnalyzer;
import com.javabytes.mcp.schema.Column;
import com.javabytes.mcp.schema.Index;
import com.javabytes.mcp.schema.InformationSchemaReader;
import com.javabytes.mcp.schema.Relation;
import com.javabytes.mcp.schema.SchemaModel;
import com.javabytes.mcp.schema.Table;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reviews the live schema for likely normal-form problems.
 *
 * <p>This is deliberately evidence-based: it inspects primary keys, foreign
 * keys, indexes, column types and (optionally) a bounded sample of list-like
 * columns. It does not claim to prove functional dependencies it cannot see.
 * The catalog snapshot comes from the shared {@link InformationSchemaReader}.</p>
 */
public class AnalyzeSchemaNormalizationTool implements Tool {

    private static final int MAX_TABLES = 300;
    private static final int MAX_SAMPLE_COLUMNS = 50;

    private static final Pattern NUMBERED_COLUMN = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*?)(\\d+)$");
    private static final Pattern LIST_LIKE = Pattern.compile(
            "(?i)^(tags?|ids?|list|csv|values?|categories|emails?|phones?)$"
                    + "|(?i).*(_(tags?|ids|list|csv|values|emails?|phones?|categories))$");

    private static final Set<String> AUDIT_COLUMNS = Set.of(
            "id", "created_at", "updated_at", "deleted_at", "created_on", "updated_on",
            "version", "tenant_id", "created_by", "updated_by");

    private static final Set<String> TEXT_TYPES = Set.of(
            "char", "varchar", "tinytext", "text", "mediumtext", "longtext", "json", "set");

    private record Finding(String normalForm, String severity, String table,
                           List<String> columns, String evidence, String recommendation) {
    }

    @Override
    public String getName() { return "analyze_schema_normalization"; }

    @Override
    public JsonNode execute(JsonNode params) {
        int sampleRows = params.has("sample_rows") ? params.get("sample_rows").asInt(20) : 20;
        sampleRows = Math.max(0, Math.min(100, sampleRows));
        boolean includeSamples = !params.has("include_samples")
                || params.get("include_samples").asBoolean(true);
        String dependencySpecification = params.has("functional_dependencies")
                ? params.get("functional_dependencies").asText("")
                : "";
        String defaultTable = params.has("default_table")
                ? params.get("default_table").asText("").trim()
                : "";
        if (defaultTable.isEmpty()) {
            defaultTable = null;
        }

        try {
            MyDatabaseClient db = new MyDatabaseClient();
            SchemaModel schema = new InformationSchemaReader().read();
            String database = schema.database();
            if (database == null) {
                return ErrorHelper.error("NO_DATABASE",
                        "No schema selected. Set JAVABYTES_DB_URL to a database or qualify the connection.");
            }

            Map<String, Table> allTables = tablesByName(schema.tables());
            boolean truncated = allTables.size() > MAX_TABLES;
            Map<String, Table> tables = truncated
                    ? firstTables(allTables, MAX_TABLES)
                    : allTables;

            List<Relation> foreignKeys = schema.relations();
            Map<String, List<Index>> indexes = indexesByTable(tables);

            List<Finding> findings = new ArrayList<>();
            checkPrimaryKeys(tables, findings);
            checkRepeatingGroups(tables, findings);
            checkJunctionTables(tables, foreignKeys, indexes, findings);
            checkForeignKeyIndexes(tables, foreignKeys, indexes, findings);
            checkDuplicatedAttributes(tables, foreignKeys, findings);
            if (includeSamples && sampleRows > 0) {
                checkDelimitedValues(db, tables, sampleRows, findings);
            }

            ObjectNode dependencyAnalysis = null;
            if (!dependencySpecification.isBlank()) {
                try {
                    FunctionalDependencyAnalyzer.Result result =
                            FunctionalDependencyAnalyzer.analyze(
                                    dependencySpecification,
                                    defaultTable,
                                    primaryKeyColumns(tables),
                                    columnNames(tables));
                    dependencyAnalysis = functionalDependencyReport(result);
                    for (FunctionalDependencyAnalyzer.Violation violation : result.violations()) {
                        List<String> columns = new ArrayList<>(violation.determinant());
                        for (String dependent : violation.dependent()) {
                            if (!columns.contains(dependent)) {
                                columns.add(dependent);
                            }
                        }
                        findings.add(new Finding(violation.normalForm(),
                                violation.severity(), violation.table(), columns,
                                violation.evidence(), violation.recommendation()));
                    }
                } catch (IllegalArgumentException e) {
                    return ErrorHelper.error("INVALID_FUNCTIONAL_DEPENDENCIES", e.getMessage());
                }
            }

            return report(database, tables.size(), sampleRows, truncated, findings,
                    dependencyAnalysis);
        } catch (Exception e) {
            return ErrorHelper.error("DB_ERROR", "Error analyzing schema: " + e.getMessage());
        }
    }

    /** Tables by name in natural order, matching the previous TreeMap-backed loading. */
    private static Map<String, Table> tablesByName(List<Table> schemaTables) {
        Map<String, Table> byName = new TreeMap<>();
        for (Table table : schemaTables) {
            byName.put(table.name(), table);
        }
        return byName;
    }

    private static Map<String, Table> firstTables(Map<String, Table> tables, int limit) {
        Map<String, Table> subset = new LinkedHashMap<>();
        for (Map.Entry<String, Table> entry : tables.entrySet()) {
            if (subset.size() >= limit) {
                break;
            }
            subset.put(entry.getKey(), entry.getValue());
        }
        return subset;
    }

    private static Map<String, List<Index>> indexesByTable(Map<String, Table> tables) {
        Map<String, List<Index>> indexes = new TreeMap<>();
        for (Map.Entry<String, Table> entry : tables.entrySet()) {
            indexes.put(entry.getKey(), entry.getValue().indexes());
        }
        return indexes;
    }

    private static void checkPrimaryKeys(Map<String, Table> tables, List<Finding> findings) {
        for (Map.Entry<String, Table> entry : tables.entrySet()) {
            if (!entry.getValue().primaryKeys().isEmpty()) {
                continue;
            }
            findings.add(new Finding("2NF", "high", entry.getKey(), List.of(),
                    "Table has no primary key.",
                    "Add a primary key that uniquely identifies each row; without one, "
                            + "non-key attributes cannot be functionally dependent on a key."));
        }
    }

    private static void checkRepeatingGroups(Map<String, Table> tables, List<Finding> findings) {
        for (Map.Entry<String, Table> entry : tables.entrySet()) {
            String table = entry.getKey();
            Map<String, List<String>> numbered = new TreeMap<>();
            for (Column column : entry.getValue().columns()) {
                if (entry.getValue().primaryKeys().contains(column.name())) {
                    continue;
                }
                if (dataType(column).equals("set")) {
                    findings.add(new Finding("1NF", "medium", table, List.of(column.name()),
                            "Column uses MySQL SET, which stores multiple values in one column.",
                            "Replace SET with a child table or a JSON column if the list is opaque."));
                }
                Matcher matcher = NUMBERED_COLUMN.matcher(column.name());
                if (matcher.matches() && matcher.group(1).length() >= 2) {
                    numbered.computeIfAbsent(matcher.group(1), k -> new ArrayList<>())
                            .add(column.name());
                }
            }
            for (Map.Entry<String, List<String>> group : numbered.entrySet()) {
                if (group.getValue().size() >= 2) {
                    findings.add(new Finding("1NF", "medium", table, group.getValue(),
                            "Numbered variants of '" + group.getKey() + "' suggest a repeating group.",
                            "Move each value into a child table (parent_id, value, position), "
                                    + "or a JSON column if the list is genuinely opaque."));
                }
            }
        }
    }

    private static void checkJunctionTables(Map<String, Table> tables,
                                            List<Relation> foreignKeys,
                                            Map<String, List<Index>> indexes,
                                            List<Finding> findings) {
        Map<String, List<Relation>> byTable = new LinkedHashMap<>();
        for (Relation foreignKey : foreignKeys) {
            byTable.computeIfAbsent(foreignKey.sourceTable(), k -> new ArrayList<>()).add(foreignKey);
        }
        for (Map.Entry<String, Table> entry : tables.entrySet()) {
            List<Relation> keys = byTable.getOrDefault(entry.getKey(), List.of());
            if (keys.size() != 2) {
                continue;
            }
            Set<String> fkColumns = new LinkedHashSet<>();
            keys.forEach(key -> fkColumns.add(key.sourceColumn()));

            boolean everyColumnIsForeignKey = entry.getValue().columns().stream()
                    .allMatch(column -> fkColumns.contains(column.name()));
            if (!everyColumnIsForeignKey) {
                continue;
            }

            Set<String> primaryKey = new LinkedHashSet<>(entry.getValue().primaryKeys());
            boolean uniquePair = indexes.getOrDefault(entry.getKey(), List.of()).stream()
                    .anyMatch(index -> index.unique()
                            && new LinkedHashSet<>(index.columns()).equals(fkColumns));

            if (!primaryKey.equals(fkColumns) && !uniquePair) {
                findings.add(new Finding("integrity", "medium", entry.getKey(),
                        List.copyOf(fkColumns),
                        "Join table has two foreign keys but no primary key or unique constraint on the pair.",
                        "Add PRIMARY KEY (" + String.join(", ", fkColumns) + ") or a UNIQUE index "
                                + "to prevent duplicate links."));
            }
        }
    }

    private static void checkForeignKeyIndexes(Map<String, Table> tables,
                                               List<Relation> foreignKeys,
                                               Map<String, List<Index>> indexes,
                                               List<Finding> findings) {
        Set<String> reported = new LinkedHashSet<>();
        for (Relation foreignKey : foreignKeys) {
            if (!tables.containsKey(foreignKey.sourceTable())) {
                continue;
            }
            boolean covered = indexes.getOrDefault(foreignKey.sourceTable(), List.of()).stream()
                    .anyMatch(index -> !index.columns().isEmpty()
                            && index.columns().get(0).equals(foreignKey.sourceColumn()));
            if (!covered && reported.add(foreignKey.sourceTable() + "." + foreignKey.sourceColumn())) {
                findings.add(new Finding("performance", "low", foreignKey.sourceTable(),
                        List.of(foreignKey.sourceColumn()),
                        "Foreign key column has no index with this column first.",
                        "Add INDEX (" + SchemaCatalog.quote(foreignKey.sourceColumn()) + ") "
                                + "if this relationship is queried or the parent row is deleted."));
            }
        }
    }

    private static void checkDuplicatedAttributes(Map<String, Table> tables,
                                                  List<Relation> foreignKeys,
                                                  List<Finding> findings) {
        Set<String> reported = new LinkedHashSet<>();
        for (Relation foreignKey : foreignKeys) {
            Table local = tables.get(foreignKey.sourceTable());
            Table referenced = tables.get(foreignKey.targetTable());
            if (local == null || referenced == null) {
                continue;
            }
            Set<String> referencedNames = new LinkedHashSet<>();
            referenced.columns().forEach(column -> referencedNames.add(column.name().toLowerCase(Locale.ROOT)));

            for (Column column : local.columns()) {
                String name = column.name().toLowerCase(Locale.ROOT);
                if (local.primaryKeys().contains(column.name())
                        || name.equals(foreignKey.sourceColumn().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                if (AUDIT_COLUMNS.contains(name) || !referencedNames.contains(name)) {
                    continue;
                }
                String key = foreignKey.sourceTable() + "." + name;
                if (reported.add(key)) {
                    findings.add(new Finding("3NF", "low", foreignKey.sourceTable(), List.of(column.name()),
                            "Column also exists in referenced table '"
                                    + foreignKey.targetTable() + "'.",
                            "If this column mirrors the referenced row, remove it and join instead; "
                                    + "keep it only if it is an intentional historical snapshot."));
                }
            }
        }
    }

    private static void checkDelimitedValues(MyDatabaseClient db,
                                             Map<String, Table> tables,
                                             int sampleRows,
                                             List<Finding> findings) {
        int sampledColumns = 0;
        for (Map.Entry<String, Table> entry : tables.entrySet()) {
            for (Column column : entry.getValue().columns()) {
                if (sampledColumns >= MAX_SAMPLE_COLUMNS) {
                    return;
                }
                if (!TEXT_TYPES.contains(dataType(column))
                        || !LIST_LIKE.matcher(column.name()).matches()) {
                    continue;
                }
                sampledColumns++;
                String sql = "SELECT " + SchemaCatalog.quote(column.name()) + " AS v FROM "
                        + SchemaCatalog.quote(entry.getKey())
                        + " WHERE " + SchemaCatalog.quote(column.name()) + " IS NOT NULL LIMIT "
                        + sampleRows;
                try {
                    int nonBlank = 0;
                    int withDelimiter = 0;
                    for (JsonNode row : SchemaCatalog.rows(db.query(sql))) {
                        String value = row.path("v").asText("");
                        if (value.isBlank()) {
                            continue;
                        }
                        nonBlank++;
                        if (value.indexOf(',') >= 0 || value.indexOf(';') >= 0
                                || value.indexOf('|') >= 0) {
                            withDelimiter++;
                        }
                    }
                    if (withDelimiter > 0) {
                        findings.add(new Finding("1NF", "medium", entry.getKey(),
                                List.of(column.name()),
                                withDelimiter + " of " + nonBlank
                                        + " sampled non-null values contain a list delimiter.",
                                "Store one value per row in a child table instead of a delimited string."));
                    }
                } catch (Exception ignored) {
                    // A single unreadable column should not abort the whole review.
                }
            }
        }
    }

    private static Map<String, Set<String>> primaryKeyColumns(Map<String, Table> tables) {
        Map<String, Set<String>> keys = new LinkedHashMap<>();
        for (Map.Entry<String, Table> entry : tables.entrySet()) {
            keys.put(entry.getKey(), new LinkedHashSet<>(entry.getValue().primaryKeys()));
        }
        return keys;
    }

    private static Map<String, Set<String>> columnNames(Map<String, Table> tables) {
        Map<String, Set<String>> columns = new LinkedHashMap<>();
        for (Map.Entry<String, Table> entry : tables.entrySet()) {
            Set<String> names = new LinkedHashSet<>();
            for (Column column : entry.getValue().columns()) {
                names.add(column.name());
            }
            columns.put(entry.getKey(), names);
        }
        return columns;
    }

    /**
     * Base type name of a raw MySQL column type, e.g. {@code varchar(191)} maps
     * to {@code varchar}. This is the information_schema DATA_TYPE equivalent
     * used for type matching; the raw type itself is never rewritten.
     */
    private static String dataType(Column column) {
        String raw = column.rawType();
        int parentheses = raw == null ? -1 : raw.indexOf('(');
        String base = parentheses >= 0 ? raw.substring(0, parentheses) : raw;
        return base == null ? "" : base.trim().toLowerCase(Locale.ROOT);
    }

    private static ObjectNode functionalDependencyReport(
            FunctionalDependencyAnalyzer.Result result) {
        ObjectNode out = JsonUtils.MAPPER.createObjectNode();
        out.put("provided", true);
        out.put("declared_count", result.dependencies().size());

        ArrayNode declared = out.putArray("declared");
        for (FunctionalDependencyAnalyzer.Dependency dependency : result.dependencies()) {
            ObjectNode item = JsonUtils.MAPPER.createObjectNode();
            item.put("table", dependency.table());
            item.set("determinant", array(dependency.determinant()));
            item.set("dependent", array(dependency.dependent()));
            item.put("line", dependency.line());
            declared.add(item);
        }

        ArrayNode closures = out.putArray("closures");
        for (FunctionalDependencyAnalyzer.Closure closure : result.closures()) {
            ObjectNode item = JsonUtils.MAPPER.createObjectNode();
            item.put("table", closure.table());
            item.set("determinant", array(closure.determinant()));
            item.set("closure", array(closure.closure()));
            item.put("superkey", closure.superkey());
            closures.add(item);
        }

        ArrayNode violations = out.putArray("violations");
        for (FunctionalDependencyAnalyzer.Violation violation : result.violations()) {
            ObjectNode item = JsonUtils.MAPPER.createObjectNode();
            item.put("normal_form", violation.normalForm());
            item.put("severity", violation.severity());
            item.put("table", violation.table());
            item.set("determinant", array(violation.determinant()));
            item.set("dependent", array(violation.dependent()));
            item.put("evidence", violation.evidence());
            item.put("recommendation", violation.recommendation());
            item.set("suggested_relations", array(violation.suggestedRelations()));
            violations.add(item);
        }

        ArrayNode warnings = out.putArray("warnings");
        result.warnings().forEach(warnings::add);
        return out;
    }

    private static ArrayNode array(Set<String> values) {
        ArrayNode array = JsonUtils.MAPPER.createArrayNode();
        values.forEach(array::add);
        return array;
    }

    private static ArrayNode array(List<String> values) {
        ArrayNode array = JsonUtils.MAPPER.createArrayNode();
        values.forEach(array::add);
        return array;
    }

    private static JsonNode report(String database, int tableCount, int sampleRows,
                                   boolean truncated, List<Finding> findings,
                                   ObjectNode dependencyAnalysis) {
        ObjectNode result = JsonUtils.MAPPER.createObjectNode();
        result.put("success", true);
        result.put("database", database);
        result.put("tables_analyzed", tableCount);
        result.put("sample_rows", sampleRows);
        result.put("truncated", truncated);

        ArrayNode items = JsonUtils.MAPPER.createArrayNode();
        Map<String, Integer> bySeverity = new LinkedHashMap<>();
        Map<String, Integer> byNormalForm = new LinkedHashMap<>();
        for (Finding finding : findings) {
            ObjectNode item = JsonUtils.MAPPER.createObjectNode();
            item.put("normal_form", finding.normalForm());
            item.put("severity", finding.severity());
            item.put("table", finding.table());
            ArrayNode columns = JsonUtils.MAPPER.createArrayNode();
            finding.columns().forEach(columns::add);
            item.set("columns", columns);
            item.put("evidence", finding.evidence());
            item.put("recommendation", finding.recommendation());
            items.add(item);

            bySeverity.merge(finding.severity(), 1, Integer::sum);
            byNormalForm.merge(finding.normalForm(), 1, Integer::sum);
        }
        result.set("findings", items);
        if (dependencyAnalysis != null) {
            result.set("functional_dependency_analysis", dependencyAnalysis);
        } else {
            ObjectNode notProvided = result.putObject("functional_dependency_analysis");
            notProvided.put("provided", false);
            notProvided.put("note", "Pass functional_dependencies to analyze declared FDs; "
                    + "nothing is inferred from data.");
        }

        ObjectNode summary = JsonUtils.MAPPER.createObjectNode();
        summary.put("finding_count", findings.size());
        ObjectNode severities = summary.putObject("by_severity");
        bySeverity.forEach(severities::put);
        ObjectNode forms = summary.putObject("by_normal_form");
        byNormalForm.forEach(forms::put);
        result.set("summary", summary);

        ObjectNode normalForms = JsonUtils.MAPPER.createObjectNode();
        for (String form : List.of("1NF", "2NF", "3NF", "BCNF", "integrity", "performance")) {
            ObjectNode status = normalForms.putObject(form);
            boolean hasFindings = findings.stream().anyMatch(f -> f.normalForm().equals(form));
            status.put("status", hasFindings ? "review_required" : "no_violations_detected");
            status.put("finding_count", byNormalForm.getOrDefault(form, 0));
        }
        result.set("normal_forms", normalForms);

        ArrayNode limitations = result.putArray("limitations");
        limitations.add("2NF/3NF candidates are inferred from keys, foreign keys and repeated "
                + "column names; functional dependencies are not proven from data.");
        limitations.add("Functional-dependency analysis uses only the declarations passed in "
                + "functional_dependencies; alternate candidate keys and unique constraints are "
                + "not inferred, and the primary key is used as the only candidate key.");
        limitations.add("Data sampling only inspects list-like column names, at most "
                + MAX_SAMPLE_COLUMNS + " columns and " + sampleRows + " rows per column.");
        limitations.add("Views, stored procedures, triggers and generated columns are not analyzed.");
        limitations.add("A clean report is not a formal proof of normalization.");
        return result;
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("sample_rows", new Tool.NumberSchema()
                .integer()
                .minimum(0)
                .maximum(100)
                .description("Rows sampled per list-like column (default 20, 0 disables sampling)"));
        schema.prop("include_samples", new Tool.BoolSchema()
                .description("Sample list-like columns to find delimited values (default true)"));
        schema.prop("functional_dependencies", new Tool.StringSchema()
                .description("Optional newline-separated functional dependencies, e.g. "
                        + "'orders: customer_id -> customer_name'. Analysis uses only these "
                        + "declarations; nothing is inferred from data."));
        schema.prop("default_table", new Tool.StringSchema()
                .description("Table used for dependency lines without a 'table:' prefix"));
        return schema;
    }
}
