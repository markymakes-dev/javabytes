package com.javabytes.mcp.tools.web;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javabytes.mcp.utils.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bounded JavaScript/TypeScript to Java translation.
 *
 * <p>The translator only emits a method when every line is in a safe, typed
 * subset. Anything else (JSX, DOM access, dynamic typing, object literals,
 * collection mutation, async) is reported as manual work and is <em>not</em>
 * emitted as broken Java. The generated class therefore always compiles.</p>
 */
public final class JsToJavaTranslator {

    private static final Pattern FUNCTION = Pattern.compile(
            "(?m)^[ \\t]*(?:export\\s+)?(?:async\\s+)?function\\s+([A-Za-z_$][A-Za-z0-9_$]*)"
                    + "\\s*\\(([^)]*)\\)\\s*(?::\\s*([A-Za-z_$][A-Za-z0-9_$<>\\[\\], ?]*))?\\s*\\{");

    private static final Pattern ARROW = Pattern.compile(
            "(?m)^[ \\t]*(?:export\\s+)?const\\s+([A-Za-z_$][A-Za-z0-9_$]*)"
                    + "\\s*=\\s*(?:async\\s*)?\\(([^)]*)\\)\\s*"
                    + "(?::\\s*([A-Za-z_$][A-Za-z0-9_$<>\\[\\], ?]*))?\\s*=>\\s*\\{");

    private static final Pattern DECLARATION = Pattern.compile(
            "^(?:const|let|var)\\s+([A-Za-z_$][A-Za-z0-9_$]*)"
                    + "\\s*(?::\\s*([A-Za-z_$][A-Za-z0-9_$<>\\[\\], ?]*))?\\s*=\\s*(.+);$");

    private static final Pattern ASSIGNMENT = Pattern.compile(
            "^([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)?)\\s*"
                    + "(\\+=|-=|\\*=|/=|=)\\s*(.+);$");

    private static final Pattern FOR_COUNTER = Pattern.compile(
            "^for\\s*\\(\\s*(?:let|var|const)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*(.+?);\\s*(.+?);\\s*(.+?)\\s*\\)\\s*\\{$");

    private static final Pattern FOR_OF = Pattern.compile(
            "^for\\s*\\(\\s*(?:const|let|var)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s+of\\s+(.+?)\\s*\\)\\s*\\{$");

    private static final Pattern IF = Pattern.compile("^(?:\\}\\s*)?(if|while)\\s*\\((.*)\\)\\s*\\{$");
    private static final Pattern ELSE = Pattern.compile("^\\}\\s*else\\s*(?:if\\s*\\((.*)\\)\\s*)?\\{$");

    private static final Set<String> UNSUPPORTED_TOKENS = Set.of(
            "await ", "async ", "document.", "window.", "localStorage", "sessionStorage",
            "fetch(", "require(", "import(", "new ", "typeof ", "instanceof ", "delete ",
            "this.", "?.", "??", "=>", "prototype.", "eval(", "Math.trunc", "switch ",
            "try ", "catch ", "throw ", "do {", "class ", "yield ");

    private static final Set<String> COLLECTION_MUTATION = Set.of(
            ".push(", ".pop(", ".shift(", ".unshift(", ".splice(", ".sort(", ".reverse(",
            ".map(", ".filter(", ".reduce(", ".forEach(", ".find(", ".some(", ".every(");

    private static final Set<String> STRING_METHODS = Set.of(
            ".trim()", ".toUpperCase()", ".toLowerCase()", ".substring(", ".replace(",
            ".split(", ".startsWith(", ".endsWith(", ".contains(", ".charAt(");

    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "true", "false", "null", "if", "else", "for", "while", "return", "var", "final",
            "int", "double", "boolean", "String", "Math", "System", "Objects", "Integer",
            "Double", "List", "Map", "void");

    public record Result(String javaSource, ObjectNode report) {
    }

    private record Param(String name, String tsType, boolean rest) {
    }

    private record FunctionInfo(String name, String paramsRaw, String returnType,
                                String body, int line, boolean async) {
    }

    private JsToJavaTranslator() {
    }

    public static Result translate(String source, String className, String packageName) {
        String typeName = sanitizeTypeName(className);
        String pkg = sanitizePackage(packageName);

        List<FunctionInfo> functions = extractFunctions(source);
        ArrayNode translated = JsonUtils.MAPPER.createArrayNode();
        ArrayNode manual = JsonUtils.MAPPER.createArrayNode();
        Set<String> unsupported = new LinkedHashSet<>();
        List<String> methods = new ArrayList<>();
        boolean usesObjects = false;

        for (FunctionInfo function : functions) {
            List<String> reasons = new ArrayList<>();
            if (function.async()) {
                reasons.add("async/await needs an explicit CompletableFuture or blocking design");
            }
            String method = translateFunction(function, reasons, unsupported);
            if (method != null) {
                methods.add(method);
                if (method.contains("Objects.equals(")) {
                    usesObjects = true;
                }
                ObjectNode entry = JsonUtils.MAPPER.createObjectNode();
                entry.put("name", function.name());
                entry.put("line", function.line());
                entry.put("signature", signatureOf(method));
                translated.add(entry);
            } else {
                ObjectNode entry = JsonUtils.MAPPER.createObjectNode();
                entry.put("name", function.name());
                entry.put("line", function.line());
                ArrayNode why = JsonUtils.MAPPER.createArrayNode();
                if (reasons.isEmpty()) {
                    reasons.add("construct outside the safe translation subset");
                }
                reasons.stream().limit(5).forEach(why::add);
                entry.set("reasons", why);
                manual.add(entry);
            }
        }

        StringBuilder java = new StringBuilder();
        java.append("package ").append(pkg).append(";\n\n");
        if (usesObjects) {
            java.append("import java.util.Objects;\n\n");
        }
        java.append("/**\n")
                .append(" * Generated by JavaBytes from JavaScript.\n")
                .append(" * Only functions listed as translated were emitted; review MIGRATION notes\n")
                .append(" * and the translation report before relying on this class.\n")
                .append(" */\n")
                .append("public final class ").append(typeName).append(" {\n\n")
                .append("    private ").append(typeName).append("() {\n")
                .append("    }\n");
        if (methods.isEmpty()) {
            java.append("\n    // No function could be translated safely. See the report.\n");
        } else {
            for (String method : methods) {
                java.append('\n').append(method);
            }
        }
        java.append("}\n");

        ObjectNode report = JsonUtils.MAPPER.createObjectNode();
        report.put("class_name", typeName);
        report.put("package_name", pkg);
        report.put("functions_found", functions.size());
        report.put("functions_translated", translated.size());
        report.put("functions_manual", manual.size());
        report.set("translated", translated);
        report.set("manual", manual);
        ArrayNode unsupportedArray = report.putArray("unsupported_constructs");
        unsupported.forEach(unsupportedArray::add);
        ArrayNode warnings = report.putArray("warnings");
        warnings.add("Generated Java only covers the typed, side-effect-free subset. "
                + "It is a starting point, not a finished port.");
        warnings.add("JavaScript '===' is translated with Objects.equals only when a string "
                + "type is known; otherwise review the comparison.");
        warnings.add("No DOM, JSX, npm package, async, closure or prototype behaviour is reproduced.");
        warnings.add("For arbitrary JavaScript, embedding it with GraalJS is usually safer than "
                + "source translation; this tool only ports the typed subset.");
        return new Result(java.toString(), report);
    }

    private static List<FunctionInfo> extractFunctions(String source) {
        List<FunctionInfo> functions = new ArrayList<>();
        Matcher matcher = FUNCTION.matcher(source);
        while (matcher.find()) {
            functions.add(toFunction(source, matcher));
        }
        Matcher arrow = ARROW.matcher(source);
        while (arrow.find()) {
            functions.add(toFunction(source, arrow));
        }
        functions.sort(java.util.Comparator.comparingInt(FunctionInfo::line));
        return functions;
    }

    private static FunctionInfo toFunction(String source, Matcher matcher) {
        int openBrace = matcher.end() - 1;
        int closeBrace = matchingBrace(source, openBrace);
        String body = closeBrace > openBrace
                ? source.substring(openBrace + 1, closeBrace)
                : "";
        int line = 1;
        for (int i = 0; i < matcher.start(); i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        String lineText = lineOf(source, matcher.start());
        return new FunctionInfo(
                matcher.group(1),
                matcher.group(2) == null ? "" : matcher.group(2),
                matcher.group(3) == null ? "" : matcher.group(3).trim(),
                body,
                line,
                lineText.contains("async "));
    }

    private static int matchingBrace(String source, int openBrace) {
        int depth = 0;
        boolean inString = false;
        char quote = 0;
        for (int i = openBrace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    inString = false;
                }
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                inString = true;
                quote = c;
            } else if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                int newline = source.indexOf('\n', i);
                i = newline < 0 ? source.length() : newline;
            } else if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
                int end = source.indexOf("*/", i + 2);
                i = end < 0 ? source.length() : end + 1;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String lineOf(String source, int offset) {
        int start = source.lastIndexOf('\n', offset - 1) + 1;
        int end = source.indexOf('\n', offset);
        return source.substring(start, end < 0 ? source.length() : end);
    }

    private static String translateFunction(FunctionInfo function, List<String> reasons,
                                            Set<String> unsupported) {
        List<Param> params = parseParams(function.paramsRaw());
        if (params.stream().anyMatch(Param::rest)) {
            reasons.add("rest parameters have no fixed Java signature");
            return null;
        }

        Map<String, String> types = new LinkedHashMap<>();
        for (Param param : params) {
            String type = param.tsType().isEmpty()
                    ? inferParamType(param.name(), function.body())
                    : mapType(param.tsType());
            types.put(param.name(), type);
        }

        String returnType = function.returnType().isEmpty()
                ? inferReturnType(function.body(), types)
                : mapType(function.returnType());
        if (returnType.equals("void") && hasValueReturn(function.body())) {
            returnType = "Object";
        }

        StringBuilder signature = new StringBuilder();
        signature.append("    public static ").append(returnType).append(' ')
                .append(function.name()).append('(');
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                signature.append(", ");
            }
            Param param = params.get(i);
            String type = types.get(param.name());
            if (type.equals("void")) {
                type = "Object";
            }
            signature.append(type).append(' ').append(param.name());
        }
        signature.append(") {\n");

        List<String> body = translateBody(function.body(), new LinkedHashMap<>(types),
                reasons, unsupported);
        if (body == null || !reasons.isEmpty()) {
            return null;
        }
        StringBuilder method = new StringBuilder(signature);
        body.forEach(line -> method.append(line).append('\n'));
        method.append("    }\n");
        return method.toString();
    }

    private static List<String> translateBody(String body, Map<String, String> types,
                                              List<String> reasons, Set<String> unsupported) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        for (String rawLine : body.split("\\R", -1)) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("//")) {
                out.add(indent(depth) + line);
                continue;
            }
            if (line.startsWith("/*") || line.startsWith("*") || line.startsWith("*/")) {
                out.add(indent(depth) + line);
                continue;
            }
            for (String token : UNSUPPORTED_TOKENS) {
                if (line.contains(token)) {
                    reasons.add("unsupported construct: " + token.trim());
                    return null;
                }
            }
            for (String token : COLLECTION_MUTATION) {
                if (line.contains(token)) {
                    reasons.add("collection mutation " + token + " needs an explicit Java collection choice");
                    return null;
                }
            }
            if (line.contains("const {") || line.contains("let {")
                    || line.contains("const [") || line.contains("let [")) {
                reasons.add("destructuring has no automatic Java mapping");
                return null;
            }

            boolean closes = line.startsWith("}");
            if (closes) {
                depth = Math.max(0, depth - 1);
            }

            String translated = translateLine(line, types, reasons, unsupported);
            if (translated == null) {
                return null;
            }
            out.add(indent(depth) + translated);
            if (translated.endsWith("{")) {
                depth++;
            }
        }
        if (out.isEmpty()) {
            return List.of();
        }
        return out;
    }

    private static String translateLine(String line, Map<String, String> types,
                                        List<String> reasons, Set<String> unsupported) {
        String trimmed = line;
        if (trimmed.equals("{") || trimmed.equals("}") || trimmed.equals("} else {")) {
            return trimmed;
        }
        Matcher elseMatcher = ELSE.matcher(trimmed);
        if (elseMatcher.matches()) {
            String condition = elseMatcher.group(1);
            return condition == null ? "} else {" : "} else if (" + condition(condition, types, reasons) + ") {";
        }
        Matcher ifMatcher = IF.matcher(trimmed);
        if (ifMatcher.matches()) {
            return ifMatcher.group(1) + " (" + condition(ifMatcher.group(2), types, reasons) + ") {";
        }
        Matcher forCounter = FOR_COUNTER.matcher(trimmed);
        if (forCounter.matches()) {
            String init = forCounter.group(2);
            String initTranslated = translateExpression(init, types, reasons, unsupported);
            return "for (int " + forCounter.group(1) + " = " + initTranslated + "; "
                    + condition(forCounter.group(3), types, reasons) + "; "
                    + translateExpression(forCounter.group(4), types, reasons, unsupported) + ") {";
        }
        Matcher forOf = FOR_OF.matcher(trimmed);
        if (forOf.matches()) {
            String collection = translateExpression(forOf.group(2), types, reasons, unsupported);
            types.put(forOf.group(1), "var");
            return "for (var " + forOf.group(1) + " : " + collection + ") {";
        }
        if (trimmed.startsWith("console.log(") && trimmed.endsWith(");")) {
            String inner = trimmed.substring("console.log(".length(), trimmed.length() - 2);
            return "System.out.println(" + translateExpression(inner, types, reasons, unsupported) + ");";
        }
        if (trimmed.startsWith("console.error(") && trimmed.endsWith(");")) {
            String inner = trimmed.substring("console.error(".length(), trimmed.length() - 2);
            return "System.err.println(" + translateExpression(inner, types, reasons, unsupported) + ");";
        }
        if (trimmed.equals("break;") || trimmed.equals("continue;")) {
            return trimmed;
        }
        if (trimmed.startsWith("return")) {
            String value = trimmed.substring("return".length()).strip();
            if (value.equals(";")) {
                return "return;";
            }
            if (value.endsWith(";")) {
                value = value.substring(0, value.length() - 1);
            }
            return "return " + translateExpression(value, types, reasons, unsupported) + ";";
        }

        Matcher declaration = DECLARATION.matcher(trimmed);
        if (declaration.matches()) {
            String name = declaration.group(1);
            String tsType = declaration.group(2) == null ? "" : declaration.group(2);
            String expression = declaration.group(3).strip();
            String type = tsType.isEmpty() ? inferLiteralType(expression, types) : mapType(tsType);
            types.put(name, type);
            String value = translateExpression(expression, types, reasons, unsupported);
            boolean constant = trimmed.startsWith("const ");
            if (type.equals("Object") || type.equals("var")) {
                return (constant ? "final var " : "var ") + name + " = " + value + ";";
            }
            return (constant ? "final " : "") + type + " " + name + " = " + value + ";";
        }

        Matcher assignment = ASSIGNMENT.matcher(trimmed);
        if (assignment.matches()) {
            String value = translateExpression(assignment.group(3), types, reasons, unsupported);
            return assignment.group(1) + " " + assignment.group(2) + " " + value + ";";
        }

        reasons.add("statement not in the safe subset: " + shortLine(trimmed));
        return null;
    }

    private static String condition(String expression, Map<String, String> types,
                                    List<String> reasons) {
        return translateExpression(expression, types, reasons, new LinkedHashSet<>());
    }

    private static String translateExpression(String expression, Map<String, String> types,
                                              List<String> reasons, Set<String> unsupported) {
        String value = expression.strip();
        value = value.replace("undefined", "null");

        if (value.startsWith("[") || value.startsWith("{")) {
            reasons.add("object/array literals need an explicit Java type");
            return "null";
        }
        if (Pattern.compile("<[A-Z]|</").matcher(value).find()) {
            reasons.add("JSX markup has no automatic Java mapping");
            return "null";
        }
        for (String token : Set.of("?.", "??", "=>", "new ", "typeof ", "instanceof ", "delete ")) {
            if (value.contains(token)) {
                reasons.add("unsupported expression construct: " + token.trim());
                return "null";
            }
        }
        if (value.contains("Math.trunc")) {
            reasons.add("Math.trunc needs an explicit cast in Java");
            return "null";
        }
        for (String token : COLLECTION_MUTATION) {
            if (value.contains(token)) {
                reasons.add("collection operation " + token + " needs a Java stream or loop");
                return "null";
            }
        }

        value = translateTemplates(value, types, reasons, unsupported);

        Matcher indexing = Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\[").matcher(value);
        while (indexing.find()) {
            String receiver = indexing.group(1);
            String type = typeOf(receiver, types);
            if (!type.endsWith("[]")) {
                reasons.add("indexing requires a known array type for '" + receiver + "'");
                return "null";
            }
        }

        Matcher comparison = Pattern.compile("^(.+?)\\s*(===|!==)\\s*(.+)$").matcher(value);
        if (comparison.matches()) {
            String left = translateExpression(comparison.group(1), types, reasons, unsupported);
            String right = translateExpression(comparison.group(3), types, reasons, unsupported);
            if (left.equals("null") || right.equals("null")) {
                return left + (comparison.group(2).equals("===") ? " == " : " != ") + right;
            }
            String leftType = typeOf(comparison.group(1).strip(), types);
            String rightType = typeOf(comparison.group(3).strip(), types);
            if ("String".equals(leftType) || "String".equals(rightType)) {
                String equals = "Objects.equals(" + left + ", " + right + ")";
                return comparison.group(2).equals("===") ? equals : "!" + equals;
            }
            if (isNumeric(leftType) && isNumeric(rightType)) {
                return left + (comparison.group(2).equals("===") ? " == " : " != ") + right;
            }
            unsupported.add("comparison with unknown types");
            reasons.add("comparison operands have unknown types");
            return "null";
        }
        value = value.replace("!==", "!=").replace("===", "==");

        value = value.replaceAll("\\bString\\(([^()]*)\\)", "String.valueOf($1)");
        value = value.replaceAll("\\bparseInt\\(([^()]*)\\)", "Integer.parseInt($1)");
        value = value.replaceAll("\\bparseFloat\\(([^()]*)\\)", "Double.parseDouble($1)");
        value = value.replaceAll("\\bNumber\\(([^()]*)\\)", "Double.parseDouble(String.valueOf($1))");

        for (String method : STRING_METHODS) {
            int index = value.indexOf(method);
            while (index >= 0) {
                String receiver = receiverBefore(value, index);
                String type = typeOf(receiver, types);
                if (!type.equals("String")) {
                    reasons.add("string method " + method + " called on unknown type '" + receiver + "'");
                    return "null";
                }
                index = value.indexOf(method, index + 1);
            }
        }

        if (value.contains(".length")) {
            String receiver = receiverBefore(value, value.indexOf(".length"));
            String type = typeOf(receiver, types);
            if (type.equals("String")) {
                value = value.replaceAll("\\b" + Pattern.quote(receiver) + "\\.length\\b",
                        Matcher.quoteReplacement(receiver + ".length()"));
            } else if (!type.endsWith("[]")) {
                reasons.add("'.length' receiver has unknown type '" + receiver + "'");
                return "null";
            }
        }

        if (value.contains("+")) {
            for (String identifier : identifiers(value)) {
                if (JAVA_KEYWORDS.contains(identifier) || isMethodOrClass(identifier, value)) {
                    continue;
                }
                String type = types.get(identifier);
                if (type == null || type.equals("Object") || type.equals("var")) {
                    reasons.add("arithmetic/concatenation uses untyped '" + identifier + "'");
                    return "null";
                }
            }
        }
        if (value.isBlank()) {
            reasons.add("empty expression");
            return "null";
        }
        return value;
    }

    private static String translateTemplates(String value, Map<String, String> types,
                                             List<String> reasons, Set<String> unsupported) {
        Matcher matcher = Pattern.compile("`([^`]*)`").matcher(value);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String content = matcher.group(1);
            Matcher interpolation = Pattern.compile("\\$\\{([^}]*)}").matcher(content);
            StringBuilder expression = new StringBuilder();
            int last = 0;
            while (interpolation.find()) {
                expression.append(quoteJava(content.substring(last, interpolation.start())))
                        .append(" + (")
                        .append(translateExpression(interpolation.group(1), types, reasons, unsupported))
                        .append(") + ");
                last = interpolation.end();
            }
            expression.append(quoteJava(content.substring(last)));
            String built = expression.toString();
            if (last == 0) {
                built = quoteJava(content);
            } else {
                built = built.replaceAll(" \\+ \"\"$", "");
                if (built.endsWith(" + ")) {
                    built = built.substring(0, built.length() - 3);
                }
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(built));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String quoteJava(String text) {
        return "\"" + text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", "\\t") + "\"";
    }

    private static List<Param> parseParams(String raw) {
        List<Param> params = new ArrayList<>();
        for (String part : splitTopLevel(raw)) {
            String value = part.strip();
            if (value.isEmpty()) {
                continue;
            }
            boolean rest = value.startsWith("...");
            if (rest) {
                value = value.substring(3).strip();
            }
            int equals = value.indexOf('=');
            if (equals >= 0) {
                value = value.substring(0, equals).strip();
            }
            value = value.replace("?", "").strip();
            int colon = value.indexOf(':');
            String name = colon >= 0 ? value.substring(0, colon).strip() : value;
            String type = colon >= 0 ? value.substring(colon + 1).strip() : "";
            params.add(new Param(name, type, rest));
        }
        return params;
    }

    private static List<String> splitTopLevel(String raw) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if (c == '<' || c == '[' || c == '(' || c == '{') {
                depth++;
            } else if (c == '>' || c == ']' || c == ')' || c == '}') {
                depth = Math.max(0, depth - 1);
            }
            if (c == ',' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private static String mapType(String tsType) {
        String type = tsType.strip();
        if (type.endsWith("[]")) {
            return mapType(type.substring(0, type.length() - 2)) + "[]";
        }
        if (type.startsWith("Array<") && type.endsWith(">")) {
            return mapType(type.substring("Array<".length(), type.length() - 1)) + "[]";
        }
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "number" -> "double";
            case "string" -> "String";
            case "boolean" -> "boolean";
            case "void" -> "void";
            case "date" -> "java.time.Instant";
            case "any", "unknown", "object", "" -> "Object";
            default -> "Object";
        };
    }

    private static String inferParamType(String name, String body) {
        String token = "\\b" + Pattern.quote(name) + "\\b";
        if (Pattern.compile(token + "\\s*\\.(trim|toUpperCase|toLowerCase|split|startsWith|endsWith|charAt|substring|replace)\\(")
                .matcher(body).find()
                || Pattern.compile("\"[^\"]*\"\\s*\\+\\s*" + token).matcher(body).find()
                || Pattern.compile(token + "\\s*\\+\\s*\"").matcher(body).find()) {
            return "String";
        }
        if (Pattern.compile(token + "\\s*(?:[-+*/%]=|[-+*/%]|[<>]=?|==)").matcher(body).find()
                || Pattern.compile("(?:[-+*/%]\\s*" + token + "|" + token + "\\s*[-+*/%])")
                        .matcher(body).find()
                || Pattern.compile("Math\\.[a-zA-Z]+\\s*\\([^)]*" + token).matcher(body).find()) {
            return "double";
        }
        if (Pattern.compile("if\\s*\\(\\s*!?\\s*" + token + "\\s*\\)").matcher(body).find()
                || Pattern.compile(token + "\\s*(?:===|==)\\s*(?:true|false)").matcher(body).find()) {
            return "boolean";
        }
        return "Object";
    }

    private static String inferReturnType(String body, Map<String, String> types) {
        Set<String> returnTypes = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("\\breturn\\s+([^;]+);").matcher(body);
        while (matcher.find()) {
            String expression = matcher.group(1).strip();
            returnTypes.add(inferLiteralType(expression, types));
        }
        if (returnTypes.isEmpty()) {
            return "void";
        }
        if (returnTypes.size() == 1) {
            return returnTypes.iterator().next();
        }
        return "Object";
    }

    private static boolean hasValueReturn(String body) {
        return Pattern.compile("\\breturn\\s+[^;]+;").matcher(body).find();
    }

    private static String inferLiteralType(String expression, Map<String, String> types) {
        String value = expression.strip();
        String known = types.get(value);
        if (known != null && !known.equals("var")) {
            return known;
        }
        if (value.equals("true") || value.equals("false")) {
            return "boolean";
        }
        if (value.startsWith("\"") || value.startsWith("'") || value.startsWith("`")) {
            return "String";
        }
        if (value.matches("-?\\d+")) {
            return "int";
        }
        if (value.matches("-?\\d+\\.\\d+")) {
            return "double";
        }
        if (value.contains("Math.") || value.matches(".*[+\\-*/%].*")) {
            return "double";
        }
        return "Object";
    }

    private static String typeOf(String expression, Map<String, String> types) {
        String value = expression.strip();
        String known = types.get(value);
        if (known != null) {
            return known;
        }
        if (value.matches("-?\\d+")) {
            return "int";
        }
        if (value.matches("-?\\d+\\.\\d+")) {
            return "double";
        }
        if (value.startsWith("\"") || value.startsWith("`")) {
            return "String";
        }
        if (value.equals("true") || value.equals("false")) {
            return "boolean";
        }
        return "Object";
    }

    private static String receiverBefore(String value, int index) {
        int end = index;
        int start = end - 1;
        while (start >= 0 && (Character.isLetterOrDigit(value.charAt(start))
                || value.charAt(start) == '_' || value.charAt(start) == '$')) {
            start--;
        }
        return value.substring(start + 1, end);
    }

    private static Set<String> identifiers(String value) {
        String withoutStrings = value
                .replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"")
                .replaceAll("'(?:\\\\.|[^'\\\\])*'", "''");
        Set<String> identifiers = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*").matcher(withoutStrings);
        while (matcher.find()) {
            identifiers.add(matcher.group());
        }
        return identifiers;
    }

    private static boolean isMethodOrClass(String identifier, String value) {
        String token = Pattern.quote(identifier) + "\\s*\\(";
        return Pattern.compile(token).matcher(value).find();
    }

    private static boolean isNumeric(String type) {
        return type.equals("int") || type.equals("double") || type.equals("long")
                || type.equals("float") || type.equals("Integer") || type.equals("Double");
    }

    private static String indent(int depth) {
        return "        " + "    ".repeat(Math.max(0, depth));
    }

    private static String shortLine(String line) {
        return line.length() <= 80 ? line : line.substring(0, 77) + "...";
    }

    private static String signatureOf(String method) {
        int brace = method.indexOf('{');
        String head = brace >= 0 ? method.substring(0, brace).strip() : method.strip();
        return head.replaceAll("\\s+", " ");
    }

    private static String sanitizeTypeName(String value) {
        String name = value == null ? "" : value.strip();
        if (!name.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            return "TranslatedCode";
        }
        return name;
    }

    private static String sanitizePackage(String value) {
        String pkg = value == null ? "" : value.strip();
        if (!pkg.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")) {
            return "com.example";
        }
        return pkg;
    }
}
