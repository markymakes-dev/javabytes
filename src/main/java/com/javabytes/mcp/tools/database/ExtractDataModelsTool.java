package com.javabytes.mcp.tools.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.config.AppConfig;
import com.javabytes.mcp.schema.LogicalType;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;
import com.javabytes.mcp.utils.PathValidator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses source-app data declarations into schema-model facts.
 *
 * <p>Supported sources: mongoose models, prisma {@code schema.prisma},
 * sequelize models, plain SQL {@code CREATE TABLE}, and JSON stores. Pure
 * Java (regex/string handling) — no new dependencies. Output is an honest
 * subset: every field carries a confidence level (high/medium/low, never
 * numeric) and the result reports a {@code limitations} array rather than
 * guessing at constructs it cannot parse.</p>
 */
public class ExtractDataModelsTool implements Tool {

    private static final String NAME = "extract_data_models";

    private static final Pattern MONGOOSE_CALL =
            Pattern.compile("(?:mongoose\\.)?model\\s*\\(\\s*[\"']([^\"']+)[\"']\\s*,");
    private static final Pattern MONGOOSE_FIELD =
            Pattern.compile("([A-Za-z_$][\\w$]*)\\s*:\\s*\\{([^}]*)\\}");
    private static final Pattern MONGOOSE_TYPE =
            Pattern.compile("type\\s*:\\s*([A-Za-z]+)");
    private static final Pattern MONGOOSE_REQUIRED = Pattern.compile("required\\s*:\\s*true");
    private static final Pattern MONGOOSE_UNIQUE = Pattern.compile("unique\\s*:\\s*true");
    private static final Pattern MONGOOSE_DEFAULT = Pattern.compile("default\\s*:\\s*([^,\\}]+)");

    private static final Pattern PRISMA_MODEL =
            Pattern.compile("model\\s+([A-Za-z_][\\w]*)\\s*\\{");
    private static final Pattern PRISMA_FIELD =
            Pattern.compile("^\\s*([A-Za-z_][\\w]*)\\s+([A-Za-z_][\\w]*)(.*)$",
                    Pattern.MULTILINE);

    private static final Pattern SEQUELIZE_CALL =
            Pattern.compile("define\\s*\\(\\s*[\"']([^\"']+)[\"']\\s*,");
    private static final Pattern SEQUELIZE_FIELD =
            Pattern.compile("([A-Za-z_$][\\w$]*)\\s*:\\s*\\{([^}]*)\\}");
    private static final Pattern SEQUELIZE_TYPE =
            Pattern.compile("type\\s*:\\s*(DataTypes\\.)?([A-Za-z]+)");
    private static final Pattern SEQUELIZE_ALLOW_NULL_FALSE =
            Pattern.compile("allowNull\\s*:\\s*false");
    private static final Pattern SEQUELIZE_PRIMARY_KEY =
            Pattern.compile("primaryKey\\s*:\\s*true");
    private static final Pattern SEQUELIZE_UNIQUE = Pattern.compile("unique\\s*:\\s*true");
    private static final Pattern SEQUELIZE_DEFAULT = Pattern.compile("defaultValue\\s*:\\s*([^,\\}]+)");

    private static final Pattern SQL_CREATE_TABLE =
            Pattern.compile("CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[`\"]?([A-Za-z_][\\w]*)[`\"]?\\s*\\(([^;]*)\\)",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SQL_COLUMN =
            Pattern.compile("^\\s*[`\"]?([A-Za-z_][\\w]*)[`\"]?\\s+([A-Za-z]+(?:\\([^)]*\\))?)(.*)$",
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern SQL_NOT_NULL = Pattern.compile("\\bNOT\\s+NULL\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_NULL = Pattern.compile("\\bNULL\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_DEFAULT = Pattern.compile("\\bDEFAULT\\s+([^,\\)]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_UNIQUE = Pattern.compile("\\bUNIQUE\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_PRIMARY_KEY = Pattern.compile("\\bPRIMARY\\s+KEY\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern JSON_KEY_VALUE =
            Pattern.compile("\"([A-Za-z_][\\w]*)\"\\s*:\\s*(\"[^\"]*\"|[0-9.]+|true|null|false|\\[|\\{)");

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public JsonNode execute(JsonNode params) {
        String pathParam = params.has("path") ? params.get("path").asText().trim() : "";
        String code = params.has("code") && !params.get("code").isNull()
                ? params.get("code").asText()
                : null;
        String sourceType = params.has("source_type") && !params.get("source_type").isNull()
                ? params.get("source_type").asText().trim().toLowerCase(Locale.ROOT)
                : "";
        String fileName = params.has("file_name") && !params.get("file_name").isNull()
                ? params.get("file_name").asText("input")
                : "input";

        try {
            String source;
            if (code != null) {
                source = code;
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
                source = Files.readString(path);
                if (sourceType.isEmpty()) {
                    sourceType = detectSourceType(fileName, source);
                }
            } else {
                return ErrorHelper.error("MISSING_PARAM",
                        "Provide either 'path' (file inside an allowed root) or 'code'");
            }

            if (sourceType.isEmpty()) {
                sourceType = detectSourceType(fileName, source);
            }

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("source_type", sourceType);

            ArrayNode entities = JsonUtils.MAPPER.createArrayNode();
            List<String> limitations = new ArrayList<>();
            int fieldCount = 0;

            switch (sourceType) {
                case "mongoose" -> {
                    ParseResult parsed = parseMongoose(source);
                    parsed.entities().forEach(e -> entities.add(toEntityNode(e)));
                    limitations.addAll(parsed.limitations());
                    fieldCount = parsed.fieldCount();
                }
                case "prisma" -> {
                    ParseResult parsed = parsePrisma(source);
                    parsed.entities().forEach(e -> entities.add(toEntityNode(e)));
                    limitations.addAll(parsed.limitations());
                    fieldCount = parsed.fieldCount();
                }
                case "sequelize" -> {
                    ParseResult parsed = parseSequelize(source);
                    parsed.entities().forEach(e -> entities.add(toEntityNode(e)));
                    limitations.addAll(parsed.limitations());
                    fieldCount = parsed.fieldCount();
                }
                case "sql" -> {
                    ParseResult parsed = parseSql(source);
                    parsed.entities().forEach(e -> entities.add(toEntityNode(e)));
                    limitations.addAll(parsed.limitations());
                    fieldCount = parsed.fieldCount();
                }
                case "json" -> {
                    ParseResult parsed = parseJson(source);
                    parsed.entities().forEach(e -> entities.add(toEntityNode(e)));
                    limitations.addAll(parsed.limitations());
                    fieldCount = parsed.fieldCount();
                }
                default -> {
                    return ErrorHelper.error("UNSUPPORTED_SOURCE",
                            "Unsupported source_type '" + sourceType + "'. Use mongoose, prisma, sequelize, sql or json.");
                }
            }

            result.set("entities", entities);
            result.put("entity_count", entities.size());
            result.put("field_count", fieldCount);
            ArrayNode limNode = JsonUtils.MAPPER.createArrayNode();
            for (String lim : limitations) {
                limNode.add(lim);
            }
            result.set("limitations", limNode);
            return result;
        } catch (Exception e) {
            return ErrorHelper.error("EXTRACT_ERROR", "Error extracting data models: " + e.getMessage());
        }
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("path", new Tool.StringSchema()
                .description("Source file (mongoose model, schema.prisma, sequelize model, SQL or JSON) inside an allowed root"));
        schema.prop("code", new Tool.StringSchema()
                .description("Inline source to parse instead of reading a path"));
        schema.prop("source_type", new Tool.StringSchema()
                .description("Source type: mongoose, prisma, sequelize, sql or json (auto-detected when omitted)"));
        schema.prop("file_name", new Tool.StringSchema()
                .description("Virtual file name for inline code, used for auto-detection (default input)"));
        return schema;
    }

    // --- detection -------------------------------------------------------

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
        String trimmed = source.trim();
        if (Pattern.compile("(?i)\\bCREATE\\s+TABLE\\b").matcher(trimmed).find()) {
            return "sql";
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return "json";
        }
        return "json";
    }

    // --- entity model ----------------------------------------------------

    private record Field(String name, String column, LogicalType logicalType, String rawType,
                         boolean nullable, String defaultValue, boolean unique, Long maxLength,
                         String confidence, List<String> limitations) {
    }

    private record Entity(String name, String table, List<Field> fields, List<String> limitations) {
    }

    /** Returns the text inside the balanced braces starting at {@code openIndex} (a '{'), or null. */
    private static String balancedBody(String source, int openIndex) {
        int depth = 0;
        boolean inString = false;
        char quote = 0;
        for (int i = openIndex; i < source.length(); i++) {
            char c = source.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                    continue;
                }
                if (c == quote) {
                    inString = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inString = true;
                quote = c;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openIndex + 1, i);
                }
            }
        }
        return null;
    }

    /** Locate the opening brace of the object literal that follows a call match. */
    private static int objectLiteralStart(String source, int afterMatch) {
        int i = afterMatch;
        while (i < source.length()) {
            if (source.charAt(i) == '{') {
                return i;
            }
            i++;
        }
        return -1;
    }

    // --- mongoose --------------------------------------------------------

    private ParseResult parseMongoose(String source) {
        List<Entity> entities = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        int fieldCount = 0;

        Matcher callMatcher = MONGOOSE_CALL.matcher(source);
        while (callMatcher.find()) {
            String name = callMatcher.group(1);
            int bodyStart = objectLiteralStart(source, callMatcher.end());
            if (bodyStart < 0) {
                limitations.add("mongoose entity '" + name + "' has no schema object literal");
                continue;
            }
            String body = balancedBody(source, bodyStart);
            if (body == null) {
                limitations.add("mongoose entity '" + name + "' has an unbalanced schema object");
                continue;
            }

            List<Field> fields = new ArrayList<>();
            List<String> entLimitations = new ArrayList<>();

            Matcher fieldMatcher = MONGOOSE_FIELD.matcher(body);
            while (fieldMatcher.find()) {
                String fieldName = fieldMatcher.group(1);
                String opts = fieldMatcher.group(2);
                Matcher typeMatcher = MONGOOSE_TYPE.matcher(opts);
                String rawType = typeMatcher.find() ? typeMatcher.group(1) : "Mixed";
                LogicalType logicalType = mongooseLogicalType(rawType);
                boolean required = MONGOOSE_REQUIRED.matcher(opts).find();
                boolean unique = MONGOOSE_UNIQUE.matcher(opts).find();
                Matcher defaultMatcher = MONGOOSE_DEFAULT.matcher(opts);
                String defaultValue = defaultMatcher.find() ? defaultMatcher.group(1).trim() : null;

                List<String> fieldLimitations = new ArrayList<>();
                String confidence;
                if ("Mixed".equals(rawType) || logicalType == LogicalType.UNKNOWN) {
                    confidence = "low";
                    fieldLimitations.add("type '" + rawType + "' not mapped to a logical type");
                } else if (required) {
                    confidence = "high";
                } else {
                    confidence = "medium";
                }

                fields.add(new Field(fieldName, fieldName, logicalType, rawType,
                        !required, defaultValue, unique, null, confidence, fieldLimitations));
                fieldCount++;
            }

            if (fields.isEmpty()) {
                entLimitations.add("no parseable fields declared");
                limitations.add("mongoose entity '" + name + "' has no parseable fields");
            }
            entities.add(new Entity(name, tableName(name), fields, entLimitations));
        }

        if (entities.isEmpty()) {
            limitations.add("no mongoose schema.model() declarations found");
        }

        return new ParseResult(entities, limitations, fieldCount);
    }

    // --- prisma ----------------------------------------------------------

    private ParseResult parsePrisma(String source) {
        List<Entity> entities = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        int fieldCount = 0;

        Matcher modelMatcher = PRISMA_MODEL.matcher(source);
        while (modelMatcher.find()) {
            String name = modelMatcher.group(1);
            int bodyStart = modelMatcher.end() - 1; // the '{' consumed by the pattern
            String body = balancedBody(source, bodyStart);
            if (body == null) {
                limitations.add("prisma model '" + name + "' has an unbalanced body");
                continue;
            }

            List<Field> fields = new ArrayList<>();
            List<String> entLimitations = new ArrayList<>();

            Matcher fieldMatcher = PRISMA_FIELD.matcher(body);
            while (fieldMatcher.find()) {
                String fieldName = fieldMatcher.group(1);
                String type = fieldMatcher.group(2);
                String attrs = fieldMatcher.group(3);

                boolean isArray = attrs.contains("[]");
                boolean required = !attrs.trim().startsWith("?");
                String rawType = type + (isArray ? "[]" : "");
                LogicalType logicalType = prismaLogicalType(type, isArray);

                List<String> fieldLimitations = new ArrayList<>();
                String confidence;
                if (logicalType == LogicalType.UNKNOWN) {
                    confidence = "low";
                    fieldLimitations.add("type '" + type + "' not mapped to a logical type");
                } else if (isArray) {
                    confidence = "medium";
                    fieldLimitations.add("array field mapped to a scalar logical type");
                } else {
                    confidence = required ? "high" : "medium";
                }

                String defaultValue = null;
                if (attrs.contains("@default")) {
                    Matcher dm = Pattern.compile("@default\\(([^)]*)\\)").matcher(attrs);
                    if (dm.find()) {
                        defaultValue = dm.group(1).trim();
                    }
                }
                boolean unique = attrs.contains("@unique") || attrs.contains("@id");
                Long maxLength = null;
                Matcher len = Pattern.compile("@" + type + "\\((\\d+)\\)").matcher(attrs);
                if (len.find()) {
                    maxLength = Long.parseLong(len.group(1));
                }

                fields.add(new Field(fieldName, fieldName, logicalType, rawType,
                        !required, defaultValue, unique, maxLength, confidence, fieldLimitations));
                fieldCount++;
            }

            if (fields.isEmpty()) {
                entLimitations.add("no parseable fields declared");
                limitations.add("prisma model '" + name + "' has no parseable fields");
            }
            entities.add(new Entity(name, tableName(name), fields, entLimitations));
        }

        if (entities.isEmpty()) {
            limitations.add("no prisma model declarations found");
        }

        return new ParseResult(entities, limitations, fieldCount);
    }

    // --- sequelize -------------------------------------------------------

    private ParseResult parseSequelize(String source) {
        List<Entity> entities = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        int fieldCount = 0;

        Matcher callMatcher = SEQUELIZE_CALL.matcher(source);
        while (callMatcher.find()) {
            String name = callMatcher.group(1);
            int bodyStart = objectLiteralStart(source, callMatcher.end());
            if (bodyStart < 0) {
                limitations.add("sequelize model '" + name + "' has no attributes object literal");
                continue;
            }
            String body = balancedBody(source, bodyStart);
            if (body == null) {
                limitations.add("sequelize model '" + name + "' has an unbalanced attributes object");
                continue;
            }

            List<Field> fields = new ArrayList<>();
            List<String> entLimitations = new ArrayList<>();

            Matcher fieldMatcher = SEQUELIZE_FIELD.matcher(body);
            while (fieldMatcher.find()) {
                String fieldName = fieldMatcher.group(1);
                String opts = fieldMatcher.group(2);
                Matcher typeMatcher = SEQUELIZE_TYPE.matcher(opts);
                String rawType = typeMatcher.find() ? typeMatcher.group(2) : "Mixed";
                LogicalType logicalType = sequelizeLogicalType(rawType);

                boolean allowNullFalse = SEQUELIZE_ALLOW_NULL_FALSE.matcher(opts).find();
                boolean primaryKey = SEQUELIZE_PRIMARY_KEY.matcher(opts).find();
                boolean unique = SEQUELIZE_UNIQUE.matcher(opts).find();
                Matcher defaultMatcher = SEQUELIZE_DEFAULT.matcher(opts);
                String defaultValue = defaultMatcher.find() ? defaultMatcher.group(1).trim() : null;

                List<String> fieldLimitations = new ArrayList<>();
                String confidence;
                if (logicalType == LogicalType.UNKNOWN) {
                    confidence = "low";
                    fieldLimitations.add("type '" + rawType + "' not mapped to a logical type");
                } else if (primaryKey) {
                    confidence = "high";
                } else {
                    confidence = allowNullFalse ? "high" : "medium";
                }

                fields.add(new Field(fieldName, fieldName, logicalType, rawType,
                        !allowNullFalse, defaultValue, unique || primaryKey, null,
                        confidence, fieldLimitations));
                fieldCount++;
            }

            if (fields.isEmpty()) {
                entLimitations.add("no parseable fields declared");
                limitations.add("sequelize model '" + name + "' has no parseable fields");
            }
            entities.add(new Entity(name, tableName(name), fields, entLimitations));
        }

        if (entities.isEmpty()) {
            limitations.add("no sequelize define() declarations found");
        }

        return new ParseResult(entities, limitations, fieldCount);
    }

    // --- sql -------------------------------------------------------------

    private ParseResult parseSql(String source) {
        List<Entity> entities = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        int fieldCount = 0;

        Matcher createMatcher = SQL_CREATE_TABLE.matcher(source);
        while (createMatcher.find()) {
            String name = createMatcher.group(1);
            String body = createMatcher.group(2);
            List<Field> fields = new ArrayList<>();
            List<String> entLimitations = new ArrayList<>();

            Matcher columnMatcher = SQL_COLUMN.matcher(body);
            while (columnMatcher.find()) {
                String columnName = columnMatcher.group(1);
                String rawType = columnMatcher.group(2);
                String rest = columnMatcher.group(3);
                LogicalType logicalType = LogicalType.fromRaw(rawType);

                boolean notNull = SQL_NOT_NULL.matcher(rest).find();
                boolean nullable = !notNull;
                if (SQL_NULL.matcher(rest).find() && !notNull) {
                    nullable = true;
                }
                Matcher defaultMatcher = SQL_DEFAULT.matcher(rest);
                String defaultValue = defaultMatcher.find() ? defaultMatcher.group(1).trim() : null;
                boolean unique = SQL_UNIQUE.matcher(rest).find();
                boolean primaryKey = SQL_PRIMARY_KEY.matcher(rest).find();

                Long maxLength = null;
                Matcher len = Pattern.compile("\\((\\d+)").matcher(rawType);
                if (len.find()) {
                    maxLength = Long.parseLong(len.group(1));
                }

                List<String> fieldLimitations = new ArrayList<>();
                String confidence;
                if (logicalType == LogicalType.UNKNOWN) {
                    confidence = "low";
                    fieldLimitations.add("type '" + rawType + "' not mapped to a logical type");
                } else if (primaryKey || notNull) {
                    confidence = "high";
                } else {
                    confidence = "medium";
                }

                fields.add(new Field(columnName, columnName, logicalType, rawType, nullable,
                        defaultValue, unique, maxLength, confidence, fieldLimitations));
                fieldCount++;
            }

            if (fields.isEmpty()) {
                entLimitations.add("no parseable columns declared");
                limitations.add("SQL table '" + name + "' has no parseable columns");
            }
            entities.add(new Entity(name, name, fields, entLimitations));
        }

        if (entities.isEmpty()) {
            limitations.add("no CREATE TABLE statements found");
        }

        return new ParseResult(entities, limitations, fieldCount);
    }

    // --- json ------------------------------------------------------------

    private ParseResult parseJson(String source) {
        List<Entity> entities = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        int fieldCount = 0;

        String trimmed = source.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            limitations.add("input is not a JSON object or array");
            return new ParseResult(entities, limitations, 0);
        }

        List<Field> fields = new ArrayList<>();
        Matcher matcher = JSON_KEY_VALUE.matcher(trimmed);
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = matcher.group(2);
            LogicalType logicalType;
            String rawType;
            String confidence;
            List<String> fieldLimitations = new ArrayList<>();

            if (value.startsWith("\"")) {
                logicalType = LogicalType.STRING;
                rawType = "string";
                confidence = "high";
            } else if (value.equals("true") || value.equals("false")) {
                logicalType = LogicalType.BOOLEAN;
                rawType = "boolean";
                confidence = "high";
            } else if (value.equals("null")) {
                logicalType = LogicalType.UNKNOWN;
                rawType = "null";
                confidence = "low";
                fieldLimitations.add("null value carries no type information");
            } else if (value.startsWith("[")) {
                logicalType = LogicalType.JSON;
                rawType = "array";
                confidence = "medium";
                fieldLimitations.add("array shape not inspected");
            } else if (value.startsWith("{")) {
                logicalType = LogicalType.JSON;
                rawType = "object";
                confidence = "medium";
                fieldLimitations.add("object shape not inspected");
            } else {
                logicalType = value.contains(".") ? LogicalType.DECIMAL : LogicalType.INT;
                rawType = "number";
                confidence = "high";
            }

            fields.add(new Field(name, name, logicalType, rawType, true, null, false, null,
                    confidence, fieldLimitations));
            fieldCount++;
        }

        if (fields.isEmpty()) {
            limitations.add("no JSON key/value fields found");
        }
        entities.add(new Entity("root", tableName("root"), fields, List.copyOf(limitations)));

        return new ParseResult(entities, new ArrayList<>(), fieldCount);
    }

    // --- type mapping ----------------------------------------------------

    private static LogicalType mongooseLogicalType(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "string" -> LogicalType.STRING;
            case "number" -> LogicalType.INT;
            case "date" -> LogicalType.TIMESTAMP;
            case "boolean" -> LogicalType.BOOLEAN;
            case "objectid" -> LogicalType.UUID;
            case "mixed" -> LogicalType.UNKNOWN;
            default -> LogicalType.UNKNOWN;
        };
    }

    private static LogicalType prismaLogicalType(String type, boolean isArray) {
        String base = type.toLowerCase(Locale.ROOT);
        return switch (base) {
            case "string", "uuid" -> LogicalType.STRING;
            case "int" -> LogicalType.INT;
            case "bigint" -> LogicalType.BIGINT;
            case "float", "decimal" -> LogicalType.DECIMAL;
            case "boolean" -> LogicalType.BOOLEAN;
            case "datetime" -> LogicalType.TIMESTAMP;
            case "date" -> LogicalType.DATE;
            case "json" -> LogicalType.JSON;
            case "bytes" -> LogicalType.BLOB;
            default -> LogicalType.UNKNOWN;
        };
    }

    private static LogicalType sequelizeLogicalType(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "string", "text", "char" -> LogicalType.STRING;
            case "integer", "int" -> LogicalType.INT;
            case "bigint" -> LogicalType.BIGINT;
            case "decimal", "float", "double", "real" -> LogicalType.DECIMAL;
            case "boolean", "bool" -> LogicalType.BOOLEAN;
            case "date" -> LogicalType.TIMESTAMP;
            case "json", "jsonb" -> LogicalType.JSON;
            case "blob" -> LogicalType.BLOB;
            case "uuid" -> LogicalType.UUID;
            default -> LogicalType.UNKNOWN;
        };
    }

    // --- helpers ---------------------------------------------------------

    private static String tableName(String entityName) {
        return entityName.toLowerCase(Locale.ROOT) + "s";
    }

    private ObjectNode toEntityNode(Entity entity) {
        ObjectNode node = JsonUtils.MAPPER.createObjectNode();
        node.put("name", entity.name());
        node.put("table", entity.table());
        ArrayNode fields = JsonUtils.MAPPER.createArrayNode();
        for (Field f : entity.fields()) {
            ObjectNode fn = JsonUtils.MAPPER.createObjectNode();
            fn.put("name", f.name());
            fn.put("column", f.column());
            fn.put("type", f.logicalType().name());
            fn.put("raw_type", f.rawType());
            fn.put("nullable", f.nullable());
            if (f.defaultValue() != null) {
                fn.put("default_value", f.defaultValue());
            }
            fn.put("unique", f.unique());
            if (f.maxLength() != null) {
                fn.put("max_length", f.maxLength());
            }
            fn.put("confidence", f.confidence());
            ArrayNode fl = JsonUtils.MAPPER.createArrayNode();
            for (String lim : f.limitations()) {
                fl.add(lim);
            }
            fn.set("limitations", fl);
            fields.add(fn);
        }
        node.set("fields", fields);
        node.put("field_count", fields.size());
        ArrayNode el = JsonUtils.MAPPER.createArrayNode();
        for (String lim : entity.limitations()) {
            el.add(lim);
        }
        node.set("limitations", el);
        return node;
    }

    private record ParseResult(List<Entity> entities, List<String> limitations, int fieldCount) {
    }
}
