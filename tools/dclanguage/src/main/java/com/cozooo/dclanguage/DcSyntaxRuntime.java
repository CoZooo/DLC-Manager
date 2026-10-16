package com.cozooo.dclanguage;

import com.cozooo.dclanguage.generated.DcGeneratedSyntax;
import com.cozooo.dclanguage.generated.DcGeneratedSyntax.StringRule;
import java.util.Comparator;
import java.util.List;

final class DcSyntaxRuntime {
    static final List<StringRule> STRINGS = DcGeneratedSyntax.STRING_RULES.stream()
        .sorted(Comparator.comparingInt((StringRule rule) -> rule.delimiter().length()).reversed()).toList();
    private DcSyntaxRuntime() { }
    static boolean policy(String name) { return DcGeneratedSyntax.booleanPolicy(name); }
    static int integer(String name) { return DcGeneratedSyntax.integerPolicy(name); }
    static StringRule stringAt(String text, int position) {
        for (StringRule rule : STRINGS) if (text.startsWith(rule.delimiter(), position)) return rule;
        return null;
    }
    static boolean commentAt(String text, int position) {
        return text.startsWith(DcGeneratedSyntax.TOKEN_COMMENT, position) || text.startsWith(DcGeneratedSyntax.TOKEN_LINE_COMMENT, position);
    }
    static int quotedEnd(String text, int position) {
        StringRule rule = stringAt(text, position);
        if (rule == null) return position;
        int at = position + rule.delimiter().length();
        while (at < text.length()) {
            if (text.startsWith(rule.delimiter(), at)) return at + rule.delimiter().length();
            if (!rule.multiline() && (text.charAt(at) == '\n' || text.charAt(at) == '\r')) return -1;
            if (rule.escaped() && text.startsWith(DcGeneratedSyntax.TOKEN_ESCAPE, at)) at += DcGeneratedSyntax.TOKEN_ESCAPE.length();
            at++;
        }
        return -1;
    }
    static String quote(String value, String original) {
        StringRule previous = stringAt(original, 0);
        if (previous != null && represents(previous, value)) return encode(previous, value);
        for (StringRule rule : STRINGS) if (!rule.multiline() && rule.escaped() && represents(rule, value)) return encode(rule, value);
        for (StringRule rule : STRINGS) if (represents(rule, value)) return encode(rule, value);
        throw new IllegalArgumentException("No generated string rule can represent this value");
    }
    private static boolean represents(StringRule rule, String value) {
        if (rule.escaped()) return true;
        return (rule.multiline() || !value.contains("\n") && !value.contains("\r"))
            && !value.contains(rule.delimiter()) && !value.endsWith(rule.delimiter().substring(0, 1));
    }
    private static String encode(StringRule rule, String value) {
        if (!rule.escaped()) return rule.delimiter() + value + rule.delimiter();
        StringBuilder result = new StringBuilder(rule.delimiter());
        for (int i = 0; i < value.length();) {
            char c = value.charAt(i);
            if (c >= 32 && rule.delimiter().indexOf(c) < 0 && DcGeneratedSyntax.TOKEN_ESCAPE.indexOf(c) < 0) {
                result.append(c);
                i++;
                continue;
            }
            String escape = null;
            int length = 0;
            for (var entry : DcGeneratedSyntax.ESCAPES.entrySet()) {
                if (!entry.getValue().isEmpty() && entry.getValue().length() > length && value.startsWith(entry.getValue(), i)) {
                    escape = entry.getKey();
                    length = entry.getValue().length();
                }
            }
            if (escape != null) {
                result.append(DcGeneratedSyntax.TOKEN_ESCAPE).append(escape);
                i += length;
            } else if (c < 32 || rule.delimiter().indexOf(c) >= 0 || DcGeneratedSyntax.TOKEN_ESCAPE.indexOf(c) >= 0) {
                result.append(DcGeneratedSyntax.TOKEN_ESCAPE).append(DcGeneratedSyntax.TOKEN_UNICODE_ESCAPE).append(String.format(java.util.Locale.ROOT, "%04x", (int) c));
                i++;
            } else { result.append(c); i++; }
        }
        return result.append(rule.delimiter()).toString();
    }
}
