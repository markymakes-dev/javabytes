package com.javabytes.mcp.database;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionalDependencyAnalyzerTest {

    @Test
    void parsesDependenciesAndComputesClosure() {
        FunctionalDependencyAnalyzer.Result result = FunctionalDependencyAnalyzer.analyze(
                "orders: customer_id -> customer_name\n"
                        + "orders: customer_name -> customer_email\n"
                        + "# comments are ignored\n"
                        + "\n",
                null, Map.of(), Map.of());

        assertEquals(2, result.dependencies().size());
        Set<String> closure = FunctionalDependencyAnalyzer.closure(
                Set.of("customer_id"), result.dependencies());
        assertEquals(Set.of("customer_id", "customer_name", "customer_email"), closure);
    }

    @Test
    void detectsPartialDependencyAs2NF() {
        FunctionalDependencyAnalyzer.Result result = FunctionalDependencyAnalyzer.analyze(
                "order_items: product_id -> product_name",
                null,
                Map.of("order_items", Set.of("order_id", "product_id")),
                Map.of("order_items",
                        Set.of("order_id", "product_id", "product_name", "quantity")));

        assertEquals(1, result.violations().size(), result.violations().toString());
        FunctionalDependencyAnalyzer.Violation violation = result.violations().get(0);
        assertEquals("2NF", violation.normalForm());
        assertEquals("high", violation.severity());
        assertEquals(Set.of("product_id"), violation.determinant());
        assertEquals(Set.of("product_name"), violation.dependent());
        assertTrue(violation.suggestedRelations().get(0).contains("product_name"));
        assertTrue(violation.suggestedRelations().get(1).contains("order_id"));
    }

    @Test
    void detectsTransitiveDependencyAs3NF() {
        FunctionalDependencyAnalyzer.Result result = FunctionalDependencyAnalyzer.analyze(
                "customers: sales_rep_id -> sales_rep_region",
                null,
                Map.of("customers", Set.of("id")),
                Map.of("customers", Set.of("id", "sales_rep_id", "sales_rep_region")));

        assertEquals(1, result.violations().size(), result.violations().toString());
        FunctionalDependencyAnalyzer.Violation violation = result.violations().get(0);
        assertEquals("3NF", violation.normalForm());
        assertEquals("medium", violation.severity());
        assertTrue(violation.evidence().contains("not a superkey"));
    }

    @Test
    void reportsNonSuperkeyDeterminantAsBcnf() {
        FunctionalDependencyAnalyzer.Result result = FunctionalDependencyAnalyzer.analyze(
                "t: a -> b\nt: c -> d",
                null,
                Map.of("t", Set.of("a", "b")),
                Map.of("t", Set.of("a", "b", "c", "d")));

        List<FunctionalDependencyAnalyzer.Violation> bcnf = result.violations().stream()
                .filter(violation -> violation.normalForm().equals("BCNF"))
                .toList();
        assertEquals(1, bcnf.size(), result.violations().toString());
        assertEquals(Set.of("a"), bcnf.get(0).determinant());
    }

    @Test
    void ignoresTrivialDependenciesAndWarnsAboutUnknownTables() {
        FunctionalDependencyAnalyzer.Result result = FunctionalDependencyAnalyzer.analyze(
                "ghost: id -> id",
                null, Map.of(), Map.of());

        assertTrue(result.violations().isEmpty());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("trivial")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("not found")));
    }

    @Test
    void appliesDefaultTableAndQuotedIdentifiers() {
        List<FunctionalDependencyAnalyzer.Dependency> dependencies =
                FunctionalDependencyAnalyzer.parse("`id`, \"email\" -> `name`", "users");

        assertEquals(1, dependencies.size());
        assertEquals("users", dependencies.get(0).table());
        assertEquals(Set.of("id", "email"), dependencies.get(0).determinant());
        assertEquals(Set.of("name"), dependencies.get(0).dependent());
    }

    @Test
    void rejectsMalformedDependencies() {
        assertThrows(IllegalArgumentException.class,
                () -> FunctionalDependencyAnalyzer.parse("id -> name", null));
        assertThrows(IllegalArgumentException.class,
                () -> FunctionalDependencyAnalyzer.parse("users id -> name", null));
        assertThrows(IllegalArgumentException.class,
                () -> FunctionalDependencyAnalyzer.parse("users: id name", null));
        assertThrows(IllegalArgumentException.class,
                () -> FunctionalDependencyAnalyzer.parse("users: -> name", null));
        assertThrows(IllegalArgumentException.class,
                () -> FunctionalDependencyAnalyzer.parse("users: id -> name -> email", null));
        assertThrows(IllegalArgumentException.class,
                () -> FunctionalDependencyAnalyzer.parse("users: id -> bad name", null));
    }

    @Test
    void warnsWhenDependencyReferencesMissingColumn() {
        FunctionalDependencyAnalyzer.Result result = FunctionalDependencyAnalyzer.analyze(
                "users: id -> nickname",
                null,
                Map.of("users", Set.of("id")),
                Map.of("users", Set.of("id", "email")));

        assertFalse(result.warnings().isEmpty());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("nickname")));
    }
}
