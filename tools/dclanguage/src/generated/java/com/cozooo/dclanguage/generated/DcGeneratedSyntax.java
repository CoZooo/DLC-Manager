package com.cozooo.dclanguage.generated;

import java.util.List;
import java.util.Map;

public final class DcGeneratedSyntax {
    public static final String DEFINITION_SHA256 = "554537972652025e647f1733a6f20c4ac38e6fe8b94dca3c45f2ef77a1bf9d2d";
    public static final String TOKEN_ASSIGNMENT = "=";
    public static final String TOKEN_CATEGORY_OPEN = "[";
    public static final String TOKEN_CATEGORY_CLOSE = "]";
    public static final String TOKEN_CATEGORY_SEPARATOR = ".";
    public static final String TOKEN_BLOCK = ":";
    public static final String TOKEN_ARRAY_OPEN = "[";
    public static final String TOKEN_ARRAY_CLOSE = "]";
    public static final String TOKEN_OBJECT_OPEN = "{";
    public static final String TOKEN_OBJECT_CLOSE = "}";
    public static final String TOKEN_ITEM_SEPARATOR = ",";
    public static final String TOKEN_OBJECT_SEPARATOR = ":";
    public static final String TOKEN_ESCAPE = "\\";
    public static final String TOKEN_COMMENT = "#";
    public static final String TOKEN_LINE_COMMENT = "//";
    public static final String TOKEN_UNICODE_ESCAPE = "u";
    public static final String NUMBER_PATTERN = "[+-]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?";
    public static final String BARE_KEY_PATTERN = "[A-Za-z_][A-Za-z0-9_-]*";
    public static final String TRUE_LITERAL = "true";
    public static final String FALSE_LITERAL = "false";
    public static final String NULL_LITERAL = "null";
    public static final Map<String, String> TOKENS = Map.ofEntries(
        Map.entry("assignment", "="),
        Map.entry("categoryOpen", "["),
        Map.entry("categoryClose", "]"),
        Map.entry("categorySeparator", "."),
        Map.entry("block", ":"),
        Map.entry("arrayOpen", "["),
        Map.entry("arrayClose", "]"),
        Map.entry("objectOpen", "{"),
        Map.entry("objectClose", "}"),
        Map.entry("itemSeparator", ","),
        Map.entry("objectSeparator", ":"),
        Map.entry("escape", "\\"),
        Map.entry("comment", "#"),
        Map.entry("lineComment", "//"),
        Map.entry("unicodeEscape", "u")
    );
    public static final Map<String, String> ESCAPES = Map.ofEntries(
        Map.entry("n", "\n"),
        Map.entry("r", "\r"),
        Map.entry("t", "\t"),
        Map.entry("b", "\b"),
        Map.entry("f", "\f"),
        Map.entry("\"", "\""),
        Map.entry("\\", "\\"),
        Map.entry("/", "/")
    );
    public static final Map<String, String> POLICIES = Map.ofEntries(
        Map.entry("allowBareValues", "true"),
        Map.entry("allowEmptyAssignment", "true"),
        Map.entry("allowTrailingComma", "true"),
        Map.entry("allowNewlineSeparator", "true"),
        Map.entry("caseInsensitiveBooleans", "true"),
        Map.entry("allowDottedCategories", "true"),
        Map.entry("allowColonCategories", "true"),
        Map.entry("allowBareEntries", "true"),
        Map.entry("maxDepth", "256"),
        Map.entry("preserveUnknownEscapes", "true"),
        Map.entry("allowBom", "true"),
        Map.entry("indentWidth", "2")
    );
    public record StringRule(String name, String delimiter, boolean multiline, boolean escaped) { }
    public static final List<StringRule> STRING_RULES = List.of(
        new StringRule("multilineBasic", "\"\"\"", true, false),
        new StringRule("multilineLiteral", "'''", true, false),
        new StringRule("basic", "\"", false, true),
        new StringRule("literal", "'", false, false)
    );
    public static final List<String> VALUE_TYPES = List.of("string", "boolean", "number", "integer", "null", "object", "array", "any");
    public static boolean booleanPolicy(String name) { return Boolean.parseBoolean(requiredPolicy(name)); }
    public static int integerPolicy(String name) { return Integer.parseInt(requiredPolicy(name)); }
    private static String requiredPolicy(String name) { String value = POLICIES.get(name); if (value == null) throw new IllegalArgumentException("Unknown DC policy: " + name); return value; }
    private DcGeneratedSyntax() { }
}
