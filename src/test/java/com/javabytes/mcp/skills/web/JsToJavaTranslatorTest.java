package com.javabytes.mcp.skills.web;

import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsToJavaTranslatorTest {

    @Test
    void translatesTypedArithmeticIntoCompilableJava() throws Exception {
        String js = """
                function add(a: number, b: number): number {
                  return a + b;
                }
                """;

        JsToJavaTranslator.Result result = JsToJavaTranslator.translate(js, "MathOps", "com.example");

        assertEquals(1, result.report().path("functions_translated").asInt(),
                result.report().toString());
        assertTrue(result.javaSource().contains("public static double add(double a, double b)"));
        assertCompiles(result.javaSource());
    }

    @Test
    void translatesStringTemplatesAndEquals() throws Exception {
        String js = """
                function greet(name: string): string {
                  return `Hello ${name}`;
                }
                function same(a: string, b: string): boolean {
                  return a === b;
                }
                """;

        JsToJavaTranslator.Result result = JsToJavaTranslator.translate(js, "Greeter", "com.example");

        assertEquals(2, result.report().path("functions_translated").asInt(),
                result.report().toString());
        assertTrue(result.javaSource().contains("Objects.equals(a, b)"));
        assertTrue(result.javaSource().contains("\"Hello \" + (name)"));
        assertCompiles(result.javaSource());
    }

    @Test
    void infersNumericTypesForUntypedArithmetic() throws Exception {
        String js = """
                function multiply(a, b) {
                  return a * b;
                }
                """;

        JsToJavaTranslator.Result result = JsToJavaTranslator.translate(js, "MathOps", "com.example");

        assertEquals(1, result.report().path("functions_translated").asInt(),
                result.report().toString());
        assertTrue(result.javaSource().contains("double a, double b"));
        assertCompiles(result.javaSource());
    }

    @Test
    void reportsJsxInsteadOfEmittingBrokenJava() throws Exception {
        String js = """
                function Widget() {
                  return <div>Hello</div>;
                }
                """;

        JsToJavaTranslator.Result result = JsToJavaTranslator.translate(js, "Widgets", "com.example");

        assertEquals(0, result.report().path("functions_translated").asInt());
        assertEquals(1, result.report().path("functions_manual").asInt());
        assertTrue(result.report().path("manual").get(0).toString().contains("JSX"));
        assertCompiles(result.javaSource());
    }

    @Test
    void reportsDomAndObjectLiteralsInsteadOfGuessing() throws Exception {
        String js = """
                function readTitle() {
                  const state = { title: document.title };
                  return state;
                }
                """;

        JsToJavaTranslator.Result result = JsToJavaTranslator.translate(js, "Reader", "com.example");

        assertEquals(0, result.report().path("functions_translated").asInt());
        assertEquals(1, result.report().path("functions_manual").asInt());
        assertCompiles(result.javaSource());
    }

    @Test
    void genericPassThroughStillCompiles() throws Exception {
        String js = """
                function identity(value) {
                  return value;
                }
                """;

        JsToJavaTranslator.Result result = JsToJavaTranslator.translate(js, "Identity", "com.example");

        assertEquals(1, result.report().path("functions_translated").asInt(),
                result.report().toString());
        assertTrue(result.javaSource().contains("public static Object identity(Object value)"));
        assertCompiles(result.javaSource());
    }

    @Test
    void invalidClassAndPackageNamesFallBackSafely() {
        JsToJavaTranslator.Result result = JsToJavaTranslator.translate(
                "function f() { return 1; }", "bad-name", "not a package");

        assertEquals("TranslatedCode", result.report().path("class_name").asText());
        assertEquals("com.example", result.report().path("package_name").asText());
    }

    private static void assertCompiles(String source) throws Exception {
        Path dir = Files.createTempDirectory("javabytes-translate");
        Path packageDir = dir.resolve("com/example");
        Files.createDirectories(packageDir);

        java.util.regex.Matcher classMatcher =
                java.util.regex.Pattern.compile("public final class ([A-Za-z0-9_$]+)")
                        .matcher(source);
        assertTrue(classMatcher.find(), "Generated source has no public final class");
        Path file = packageDir.resolve(classMatcher.group(1) + ".java");
        Files.writeString(file, source);

        Path classes = dir.resolve("classes");
        Files.createDirectories(classes);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "A JDK is required to run this test");
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int result = compiler.run(null, null, errors,
                "-d", classes.toString(), file.toString());
        assertEquals(0, result, errors.toString(StandardCharsets.UTF_8));
    }
}
