package com.javabytes.mcp.tools.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.config.AppConfig;
import com.javabytes.mcp.database.FunctionalDependencyAnalyzer;
import com.javabytes.mcp.schema.Column;
import com.javabytes.mcp.schema.LogicalType;
import com.javabytes.mcp.schema.MySqlDdlWriter;
import com.javabytes.mcp.schema.SchemaModel;
import com.javabytes.mcp.schema.Table;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.PathValidator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Converts parsed schema models (from {@link ExtractDataModelsTool} output or
 * equivalent inline input) into normalized schema proposals: proposed DDL,
 * pass-through verdicts, and a verification list.
 *
 * <p>Pure Java — no new dependencies. Honest subset: every parsed fact keeps
 * its confidence (high/medium/low, never numeric); medium and low confidence
 * fields are flagged for human verification, high confidence passes silently.
 * When declared functional dependencies show a table is already in its
 * strongest applicable normal form, no redundant DDL is emitted — a
 * pass-through message is returned instead.</p>
 */
public class ModelsToSchemaTool implements Tool {

    private static final String NAME = "models_to_schema";

    /** Pass-through verdict returned when no DDL changes are proposed. */
    private static final String PASS_THROUGH_MESSAGE =
            "already in strongest applicable form — no changes proposed";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public JsonNode execute(JsonNode params) {
        String pathParam = params.has("path") && !params.get("path").isNull()
                ? params.get("path").asText().trim()
                : "";
        JsonNode entitiesParam = params.has("entities") && !params.get("entities").isNull()
                ? params.get("entities")
                : null;
        String fds = params.has("functional_dependencies") && !params.get("functional_dependencies").isNull()
                ? params.get("functional_dependencies").asText().trim()
                : "";

        try {
            List<JsonNode> entities;
            String sourceType = "";
            if (entitiesParam != null && entitiesParam.isArray()) {
                entities = new ArrayList<>();
                for (JsonNode e : entitiesParam) {
                    entities.add(e);
                }
                sourceType = params.has("source_type") && !params.get("source_type").isNull()
                        ? params.get("source_type").asText().trim()
                        : "inline";
            } else if (!pathParam.isEmpty()) {
                Path path = Path.of(pathParam);
                if (!PathValidator.isAllowed(path)) {
                    return ErrorHelper.error("PATH_NOT_ALLOWED",
                            "Path not allowed: " + pathParam + ". Allowed roots: "
                                    + AppConfig.describeAllowedDirectories());
                }
                if (!Files.exists(path) || !Files.isRegularFile(path)) {
                    return ErrorHelper.error("PATH_NOT_FOUND", "Path not found: " + pathParam);
                }
                String json = Files.readString(path);
                JsonNode tree = JsonUtils.MAPPER.readTree(json);
                if (tree.has("entities") && tree.get("entities").isArray()) {
                    entities = new ArrayList<>();
                    for (JsonNode e : tree.get("entities")) {
                        entities.add(e);
                    }
                    sourceType = tree.has("source_type")
                            ? tree.get("source_type").asText().trim()
                            : detectSourceType(path.getFileName().toString(), json);
                } else {
                    return ErrorHelper.error("INVALID_INPUT",
                            "File at " + pathParam + " does not contain an 'entities' array "
                                    + "(expected extract_data_models output)");
                }
            } else {
                return ErrorHelper.error("MISSING_PARAM",
                        "Provide either 'entities' (parsed schema model array) or 'path' "
                                + "(file inside an allowed root containing extract_data_models output)");
            }

            if (entities.isEmpty()) {
                return buildEmptyResult(sourceType);
            }

            Map<String, Table> tables = buildTables(entities);
            Map<String, Set<String>> primaryKeys = primaryKeys(tables);

            NormalFormAnalysis normalForm = analyzeNormalForm(fds, tables, primaryKeys);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("source_type", sourceType);
            result.put("entity_count", entities.size());
            int fieldCount = 0;
            for (Table table : tables.values()) {
                fieldCount += table.columns().size();
            }
            result.put("field_count", fieldCount);

            // Pass-through rule: declared FDs exist and no table has a
            // normal-form violation → already in strongest applicable form.
            boolean passThrough = !fds.isBlank() && normalForm.allClean;
            result.put("pass_through", passThrough);
            if (passThrough) {
                result.put("pass_through_message", PASS_THROUGH_MESSAGE);
                result.put("proposed_ddl", "");
            } else {
                result.put("pass_through_message", (ObjectNode) null);
                SchemaModel model = new SchemaModel(null, List.copyOf(tables.values()), List.of());
                String ddl = MySqlDdlWriter.write(model);
                result.put("proposed_ddl", ddl);
            }

            // Per-table normalization verdicts.
            ArrayNode tablesNode = JsonUtils.MAPPER.createArrayNode();
            for (Table table : tables.values()) {
                tablesNode.add(tableVerdict(table, normalForm, primaryKeys.getOrDefault(
                        table.name(), Set.of())));
            }
            result.set("tables", tablesNode);

            // Verification list: medium and low confidence fields only.
            ArrayNode verification = JsonUtils.MAPPER.createArrayNode();
            for (JsonNode entity : entities) {
                String tableName = entity.has("table") ? entity.get("table").asText()
                        : entity.has("name") ? tableName(entity.get("name").asText()) : "";
                JsonNode fields = entity.has("fields") && entity.get("fields").isArray()
                        ? entity.get("fields")
                        : JsonUtils.MAPPER.createArrayNode();
                for (JsonNode field : fields) {
                    String confidence = field.has("confidence")
                            ? field.get("confidence").asText("low")
                            : "low";
                    if ("high".equalsIgnoreCase(confidence)) {
                        continue; // high confidence passes silently
                    }
                    ObjectNode item = JsonUtils.MAPPER.createObjectNode();
                    item.put("table", tableName);
                    item.put("column", field.has("column") ? field.get("column").asText() : "");
                    item.put("field", field.has("name") ? field.get("name").asText() : "");
                    item.put("confidence", confidence);
                    item.put("reason", verificationReason(field));
                    ArrayNode fl = JsonUtils.MAPPER.createArrayNode();
                    if (field.has("limitations") && field.get("limitations").isArray()) {
                        for (JsonNode lim : field.get("limitations")) {
                            fl.add(lim);
                        }
                    }
                    item.set("limitations", fl);
                    verification.add(item);
                }
            }
            result.set("verification_list", verification);

            ArrayNode limitations = JsonUtils.MAPPER.createArrayNode();
            for (String lim : normalForm.warnings) {
                limitations.add(lim);
            }
            result.set("limitations", limitations);

            return result;
        } catch (Exception e) {
            return ErrorHelper.error("MODELS_TO_SCHEMA_ERROR",
                    "Error generating schema proposal: " + e.getMessage());
        }
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("entities", new Tool.StringSchema()
                .description("Parsed schema model entities array (extract_data_models output), inline"));
        schema.prop("path", new Tool.StringSchema()
                .description("File inside an allowed root containing extract_data_models JSON output"));
        schema.prop("source_type", new Tool.StringSchema()
                .description("Source type: mongoose, prisma, sequelize, sql or json (auto-detected from path)"));
        schema.prop("functional_dependencies", new Tool.StringSchema()
                .description("Optional declared functional dependencies, one per line: table: A,B -> C,D"));
        return schema;
    }

    // --- build schema model from parsed entities --------------------------

    private Map<String, Table> buildTables(List<JsonNode> entities) {
        Map<String, Table> tables = new LinkedHashMap<>();
        for (JsonNode entity : entities) {
            String name = entity.has("name") ? entity.get("name").asText() : "unnamed";
            String table = entity.has("table") ? entity.get("table").asText()
                    : tableName(name);
            List<Column> columns = new ArrayList<>();
            if (entity.has("fields") && entity.get("fields").isArray()) {
                for (JsonNode field : entity.get("fields")) {
                    LogicalType logicalType = logicalType(field.has("type")
                            ? field.get("type").asText()
                            : "");
                    columns.add(new Column(
                            field.has("column") ? field.get("column").asText()
                                    : field.has("name") ? field.get("name").asText() : "",
                            logicalType,
                            field.has("raw_type") ? field.get("raw_type").asText() : null,
                            field.has("nullable") ? field.get("nullable").asBoolean() : true,
                            field.has("default_value") ? field.get("default_value").asText() : null,
                            field.has("unique") ? field.get("unique").asBoolean() : false,
                            field.has("max_length") && !field.get("max_length").isNull()
                                    ? field.get("max_length").asLong()
                                    : null));
                }
            }
            tables.put(table, new Table(table, columns, List.of(), List.of()));
        }
        return tables;
    }

    /** Primary-key columns per table: unique + not-null fields; an "id" field
     *  that is not nullable also counts. First candidate wins per table. */
    private Map<String, Set<String>> primaryKeys(Map<String, Table> tables) {
        Map<String, Set<String>> pks = new LinkedHashMap<>();
        for (Map.Entry<String, Table> entry : tables.entrySet()) {
            String table = entry.getKey();
            Set<String> pk = new TreeSet<>();
            for (Column column : entry.getValue().columns()) {
                if (column.unique() && !column.nullable()) {
                    pk.add(column.name());
                }
            }
            if (pk.isEmpty()) {
                for (Column column : entry.getValue().columns()) {
                    if ("id".equalsIgnoreCase(column.name()) && !column.nullable()) {
                        pk.add(column.name());
                        break;
                    }
                }
            }
            pks.put(table, pk);
        }
        return pks;
    }

    // --- normal form analysis --------------------------------------------

    private NormalFormAnalysis analyzeNormalForm(String fds,
                                                 Map<String, Table> tables,
                                                 Map<String, Set<String>> primaryKeys) {
        if (fds.isBlank()) {
            return new NormalFormAnalysis(false, List.of(), List.of());
        }
        Map<String, Set<String>> tableColumns = new LinkedHashMap<>();
        for (Map.Entry<String, Table> entry : tables.entrySet()) {
            Set<String> cols = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (Column column : entry.getValue().columns()) {
                cols.add(column.name());
            }
            tableColumns.put(entry.getKey(), cols);
        }
        FunctionalDependencyAnalyzer.Result result = FunctionalDependencyAnalyzer.analyze(
                fds, null, primaryKeys, tableColumns);
        boolean allClean = result.violations().isEmpty();
        return new NormalFormAnalysis(allClean, result.violations(), result.warnings());
    }

    private ObjectNode tableVerdict(Table table, NormalFormAnalysis nf, Set<String> pk) {
        ObjectNode node = JsonUtils.MAPPER.createObjectNode();
        node.put("name", table.name());
        node.put("table", table.name());
        node.put("column_count", table.columns().size());
        ArrayNode pks = JsonUtils.MAPPER.createArrayNode();
        for (String k : pk) pks.add(k);
        node.set("primary_keys", pks);

        if (nf.allClean) {
            node.put("normal_form", "BCNF");
            node.put("verdict", "Already normalized — no changes proposed");
        } else if (nf.warnings.isEmpty() && nf.violations.isEmpty()) {
            node.put("normal_form", "unanalyzed");
            node.put("verdict", "No functional dependencies declared — normalization not proven");
        } else {
            String worst = worstNormalForm(nf.violations);
            node.put("normal_form", worst);
            node.put("verdict", "Needs normalization — " + worst + " violation(s) detected");
        }
        return node;
    }

    private String worstNormalForm(List<FunctionalDependencyAnalyzer.Violation> violations) {
        String worst = "1NF";
        for (FunctionalDependencyAnalyzer.Violation v : violations) {
            switch (v.normalForm()) {
                case "2NF" -> worst = "2NF";
                case "3NF" -> worst = "3NF";
                case "BCNF" -> worst = "BCNF";
            }
        }
        return worst;
    }

    // --- verification reasons --------------------------------------------

    private static String verificationReason(JsonNode field) {
        String confidence = field.has("confidence") ? field.get("confidence").asText("low") : "low";
        if (field.has("limitations") && field.get("limitations").isArray()
                && field.get("limitations").size() > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < field.get("limitations").size(); i++) {
                if (i > 0) sb.append("; ");
                sb.append(field.get("limitations").get(i).asText());
            }
            return sb.toString();
        }
        return switch (confidence.toLowerCase(Locale.ROOT)) {
            case "medium" -> "Inferred from usage — not explicitly declared as required or unique";
            case "low" -> "Heuristic guess — type could not be mapped to a logical type";
            default -> "Review recommended";
        };
    }

    // --- helpers ---------------------------------------------------------

    private static LogicalType logicalType(String raw) {
        if (raw == null || raw.isBlank()) {
            return LogicalType.UNKNOWN;
        }
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "STRING", "VARCHAR", "CHAR", "TEXT" -> LogicalType.STRING;
            case "INT", "INTEGER", "TINYINT", "SMALLINT", "MEDIUMINT", "BIGINT" ->
                    raw.toUpperCase(Locale.ROOT).equals("BIGINT") ? LogicalType.BIGINT : LogicalType.INT;
            case "DECIMAL", "NUMERIC" -> LogicalType.DECIMAL;
            case "BOOLEAN", "BOOL" -> LogicalType.BOOLEAN;
            case "DATE" -> LogicalType.DATE;
            case "TIMESTAMP", "DATETIME" -> LogicalType.TIMESTAMP;
            case "JSON" -> LogicalType.JSON;
            case "BLOB", "BINARY", "VARBINARY" -> LogicalType.BLOB;
            case "UUID" -> LogicalType.UUID;
            default -> LogicalType.fromRaw(raw);
        };
    }

    private static String tableName(String entityName) {
        return entityName.toLowerCase(Locale.ROOT) + "s";
    }

    private static String detectSourceType(String fileName, String source) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".prisma") || lower.contains("schema.prisma")) {
            return "prisma";
        }
        if (source.contains("schema.model(") || source.contains("mongoose")) {
            return "mongoose";
        }
        if (source.contains("DataTypes") || source.contains("define(")
                || source.contains("sequelize")) {
            return "sequelize";
        }
        if (source.toUpperCase(Locale.ROOT).contains("CREATE TABLE")) {
            return "sql";
        }
        String trimmed = source.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return "json";
        }
        return "json";
    }

    private ObjectNode buildEmptyResult(String sourceType) {
        ObjectNode result = JsonUtils.MAPPER.createObjectNode();
        result.put("success", true);
        result.put("source_type", sourceType);
        result.put("entity_count", 0);
        result.put("field_count", 0);
        result.put("pass_through", false);
        result.put("pass_through_message", (ObjectNode) null);
        result.put("proposed_ddl", "");
        result.set("tables", JsonUtils.MAPPER.createArrayNode());
        result.set("verification_list", JsonUtils.MAPPER.createArrayNode());
        result.set("limitations", JsonUtils.MAPPER.createArrayNode());
        return result;
    }

    /** Normal-form outcome for the whole model. */
    private static final class NormalFormAnalysis {
        final boolean allClean;
        final List<FunctionalDependencyAnalyzer.Violation> violations;
        final List<String> warnings;

        NormalFormAnalysis(boolean allClean,
                           List<FunctionalDependencyAnalyzer.Violation> violations,
                           List<String> warnings) {
            this.allClean = allClean;
            this.violations = List.copyOf(violations);
            this.warnings = List.copyOf(warnings);
        }
    }
}
