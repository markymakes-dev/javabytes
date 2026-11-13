package com.javabytes.mcp.schema;

import java.util.List;

/**
 * The shared representation of "a data model" passed between conversion
 * pipeline stages: a database's tables and foreign-key relations.
 *
 * @param database database (schema) name the model was read from, or null when
 *        no schema was selected
 * @param tables tables in the order the source reported them
 * @param relations foreign-key rows in the order the source reported them
 */
public record SchemaModel(String database, List<Table> tables, List<Relation> relations) {

    public SchemaModel {
        tables = List.copyOf(tables);
        relations = List.copyOf(relations);
    }
}
