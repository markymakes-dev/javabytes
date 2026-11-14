package com.javabytes.mcp.tools.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.tools.Tool;
import com.javabytes.mcp.tools.ToolSpec;
import com.javabytes.mcp.utils.ErrorHelper;
import com.javabytes.mcp.utils.JsonUtils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Generates a pair of JPA entities for a relationship.
 *
 * <p>The owning side is generated explicitly for every cardinality so the
 * mapping is valid: {@code List} only for to-many sides, {@code mappedBy}
 * pointing at the owning field, and a join table for many-to-many.</p>
 */
public class GenerateSpringBootRelationshipTool implements Tool {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Pattern PACKAGE = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");

    @Override
    public String getName() { return "springboot_generate_relationship"; }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "springboot_generate_relationship",
                "Generate JPA Relationships",
                "Generate JPA entity relationships (one-to-many, many-to-many) with correct mappings.",
                "springboot",
                false,
                true,
                false,
                true,
                false);
    }

    @Override
    public JsonNode execute(JsonNode params) {
        String entity1 = params.has("entity1") ? params.get("entity1").asText() : "Parent";
        String entity2 = params.has("entity2") ? params.get("entity2").asText() : "Child";
        String relationshipType = params.has("relationshipType") ? params.get("relationshipType").asText() : "OneToMany";
        String packageName = params.has("packageName") ? params.get("packageName").asText() : "com.example.app.entity";

        try {
            if (!IDENTIFIER.matcher(entity1).matches() || !IDENTIFIER.matcher(entity2).matches()) {
                return ErrorHelper.error("INVALID_ENTITY", "Entity names must be valid Java identifiers");
            }
            if (entity1.equals(entity2)) {
                return ErrorHelper.error("INVALID_ENTITY", "entity1 and entity2 must be different");
            }
            if (!PACKAGE.matcher(packageName).matches()) {
                return ErrorHelper.error("INVALID_PACKAGE", "Invalid Java package name: " + packageName);
            }
            String type = canonicalType(relationshipType);
            if (type == null) {
                return ErrorHelper.error("INVALID_RELATIONSHIP",
                        "relationshipType must be OneToMany, ManyToOne, OneToOne or ManyToMany");
            }

            String code = generateRelationshipCode(entity1, entity2, type, packageName);

            ObjectNode result = JsonUtils.MAPPER.createObjectNode();
            result.put("success", true);
            result.put("entity1", entity1);
            result.put("entity2", entity2);
            result.put("relationshipType", type);
            result.put("packageName", packageName);
            result.put("code", code);
            result.put("message", "Generated JPA relationship: " + entity1 + " " + type + " " + entity2);
            return result;

        } catch (Exception e) {
            return ErrorHelper.error("GENERATE_RELATIONSHIP_ERROR", "Error generating relationship: " + e.getMessage());
        }
    }

    private String canonicalType(String value) {
        return switch (value == null ? "" : value.toLowerCase(Locale.ROOT)) {
            case "onetomany", "one-to-many", "1:n", "1n" -> "OneToMany";
            case "manytoone", "many-to-one", "n:1", "n1" -> "ManyToOne";
            case "onetoone", "one-to-one", "1:1", "11" -> "OneToOne";
            case "manytomany", "many-to-many", "n:m", "nm" -> "ManyToMany";
            default -> null;
        };
    }

    private String generateRelationshipCode(String a, String b, String type, String packageName) {
        String fieldA = decapitalize(a);
        String fieldB = decapitalize(b);
        String collectionA = plural(fieldA);
        String collectionB = plural(fieldB);
        String tableA = plural(fieldA.toLowerCase(Locale.ROOT));
        String tableB = plural(fieldB.toLowerCase(Locale.ROOT));

        StringBuilder code = new StringBuilder();
        code.append("package ").append(packageName).append(";\n\n");
        code.append("import jakarta.persistence.*;\n\n");
        if (type.equals("OneToMany") || type.equals("ManyToMany")) {
            code.append("import java.util.ArrayList;\n");
            code.append("import java.util.List;\n\n");
        }

        // ----- Entity A -----
        code.append("@Entity\n");
        code.append("@Table(name = \"").append(tableA).append("\")\n");
        code.append("public class ").append(a).append(" {\n\n");
        code.append("    @Id\n");
        code.append("    @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
        code.append("    private Long id;\n\n");
        code.append("    private String name;\n");

        switch (type) {
            case "OneToMany" -> {
                code.append("\n    @OneToMany(mappedBy = \"").append(fieldA)
                        .append("\", cascade = CascadeType.ALL, orphanRemoval = true)\n");
                code.append("    private List<").append(b).append("> ").append(collectionB)
                        .append(" = new ArrayList<>();\n");
            }
            case "ManyToOne" -> {
                code.append("\n    @ManyToOne(fetch = FetchType.LAZY, optional = false)\n");
                code.append("    @JoinColumn(name = \"").append(fieldB).append("_id\", nullable = false)\n");
                code.append("    private ").append(b).append(' ').append(fieldB).append(";\n");
            }
            case "OneToOne" -> {
                code.append("\n    @OneToOne(fetch = FetchType.LAZY)\n");
                code.append("    @JoinColumn(name = \"").append(fieldB)
                        .append("_id\", unique = true)\n");
                code.append("    private ").append(b).append(' ').append(fieldB).append(";\n");
            }
            case "ManyToMany" -> {
                code.append("\n    @ManyToMany(cascade = CascadeType.ALL)\n");
                code.append("    @JoinTable(name = \"").append(tableA).append('_').append(tableB).append("\",\n");
                code.append("            joinColumns = @JoinColumn(name = \"").append(fieldA).append("_id\"),\n");
                code.append("            inverseJoinColumns = @JoinColumn(name = \"").append(fieldB).append("_id\"))\n");
                code.append("    private List<").append(b).append("> ").append(collectionB)
                        .append(" = new ArrayList<>();\n");
            }
            default -> { }
        }

        appendAccessors(code, a, "id", "Long");
        appendAccessors(code, a, "name", "String");
        if (type.equals("ManyToOne") || type.equals("OneToOne")) {
            appendAccessors(code, a, fieldB, b);
        }
        code.append("}\n\n");

        // ----- Entity B -----
        code.append("@Entity\n");
        code.append("@Table(name = \"").append(tableB).append("\")\n");
        code.append("public class ").append(b).append(" {\n\n");
        code.append("    @Id\n");
        code.append("    @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
        code.append("    private Long id;\n\n");
        code.append("    private String name;\n");

        switch (type) {
            case "OneToMany" -> {
                code.append("\n    @ManyToOne(fetch = FetchType.LAZY, optional = false)\n");
                code.append("    @JoinColumn(name = \"").append(fieldA).append("_id\", nullable = false)\n");
                code.append("    private ").append(a).append(' ').append(fieldA).append(";\n");
            }
            case "ManyToOne" -> {
                code.append("\n    @OneToMany(mappedBy = \"").append(fieldB)
                        .append("\", cascade = CascadeType.ALL, orphanRemoval = true)\n");
                code.append("    private List<").append(a).append("> ").append(collectionA)
                        .append(" = new ArrayList<>();\n");
            }
            case "OneToOne" -> {
                code.append("\n    @OneToOne(mappedBy = \"").append(fieldB)
                        .append("\", fetch = FetchType.LAZY)\n");
                code.append("    private ").append(a).append(' ').append(fieldA).append(";\n");
            }
            case "ManyToMany" -> {
                code.append("\n    @ManyToMany(mappedBy = \"").append(collectionB).append("\")\n");
                code.append("    private List<").append(a).append("> ").append(collectionA)
                        .append(" = new ArrayList<>();\n");
            }
            default -> { }
        }

        appendAccessors(code, b, "id", "Long");
        appendAccessors(code, b, "name", "String");
        code.append("}\n");
        return code.toString();
    }

    private void appendAccessors(StringBuilder code, String type, String field, String fieldType) {
        String cap = Character.toUpperCase(field.charAt(0)) + field.substring(1);
        code.append("\n    public ").append(fieldType).append(" get").append(cap).append("() {\n");
        code.append("        return ").append(field).append(";\n");
        code.append("    }\n\n");
        code.append("    public void set").append(cap).append('(').append(fieldType)
                .append(' ').append(field).append(") {\n");
        code.append("        this.").append(field).append(" = ").append(field).append(";\n");
        code.append("    }\n");
    }

    private String decapitalize(String value) {
        return value.isEmpty() ? value : Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private String plural(String value) {
        if (value.endsWith("y") && value.length() > 1 && !"aeiou".contains(value.substring(value.length() - 2, value.length() - 1))) {
            return value.substring(0, value.length() - 1) + "ies";
        }
        if (value.endsWith("s") || value.endsWith("x") || value.endsWith("ch") || value.endsWith("sh")) {
            return value + "es";
        }
        return value + "s";
    }

    @Override
    public Tool.JsonSchema getInputSchema() {
        Tool.JsonSchema schema = new Tool.JsonSchema();
        schema.prop("entity1", new Tool.StringSchema().description("First entity class name (default: Parent)"));
        schema.prop("entity2", new Tool.StringSchema().description("Second entity class name (default: Child)"));
        schema.prop("relationshipType", new Tool.StringSchema().description("OneToMany, ManyToOne, OneToOne or ManyToMany"));
        schema.prop("packageName", new Tool.StringSchema().description("Entity package name"));
        schema.required("entity1");
        schema.required("entity2");
        schema.required("relationshipType");
        return schema;
    }
}
