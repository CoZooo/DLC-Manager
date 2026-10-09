package com.cozooo.dclanguage;

import com.google.gson.*;
import static com.cozooo.dclanguage.generated.DcGeneratedSyntax.*;
import java.util.*;

public final class DcLanguageService {
    public record Position(int line, int character) {
        public Position { if (line < 0 || character < 0) throw new IllegalArgumentException("Position must not be negative"); }
    }
    public record Range(Position start, Position end) { }
    public record Diagnostic(Range range, int severity, String code, String message) { }
    public record Completion(String label, String detail, String insertText, Range range, int kind) { }
    public record Hover(String markdown, Range range) { }
    public record TextEdit(Range range, String newText) { }
    private record Edit(int start, int end, String text) { }
    private final DcSchemaCatalog catalog;
    public DcLanguageService() { this(DcSchemaCatalog.loadDefault()); }
    public DcLanguageService(DcSchemaCatalog catalog) { this.catalog = Objects.requireNonNull(catalog); }
    public DcSchemaCatalog catalog() { return catalog; }

    public List<Diagnostic> diagnostics(String uri, String text) { return diagnostics(uri, text, null); }
    public List<Diagnostic> diagnostics(String uri, String text, String profile) {
        try {
            DcDocument document = DcDocuments.readText(text);
            var selected = catalog.select(uri, document.root(), profile);
            List<Diagnostic> issues = new ArrayList<>();
            for (DcIssue issue : catalog.language(selected, document.root()).validate(document).issues()) {
                DcParser.Binding binding = binding(document, issue.path());
                Range range = binding == null ? range(text, 0, 0) : range(text, binding.start(), Math.min(binding.end(), lineEnd(text, binding.start())));
                String message = issue.message();
                if (selected != null) for (var category : selected.categories()) for (var field : category.fields()) {
                    String path = String.join(".", category.path());
                    path = path.isEmpty() ? field.name() : path + "." + field.name();
                    if (path.equals(issue.path()) && !field.enumValues().isEmpty() && message.startsWith("Expected")) message += "; allowed values: " + String.join(", ", field.enumValues());
                }
                issues.add(new Diagnostic(range, 1, "schema", issue.path() + ": " + message));
            }
            return List.copyOf(issues);
        } catch (DcParseException exception) {
            Position start = new Position(exception.line() - 1, exception.column() - 1);
            int offset = offset(text, start);
            int end = offset < text.length() && text.charAt(offset) != '\r' && text.charAt(offset) != '\n' ? offset + Character.charCount(text.codePointAt(offset)) : offset;
            return List.of(new Diagnostic(range(text, offset, end), 1, "syntax", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return List.of(new Diagnostic(range(text, 0, 0), 1, "schema", exception.getMessage()));
        }
    }

    public List<Completion> completions(String uri, String text, Position position) { return completions(uri, text, position, null); }
    public List<Completion> completions(String uri, String text, Position position, String profile) {
        int cursor = offset(text, position);
        int start = lineStart(text, cursor);
        int end = lineEnd(text, cursor);
        String before = text.substring(start, cursor);
        if (DcSyntaxRuntime.commentAt(before.stripLeading(), 0)) return List.of();
        DcDocument parsed = parse(text);
        var selected = catalog.select(uri, parsed == null ? null : parsed.root(), profile);
        if (selected == null) selected = catalog.suggestions();
        List<Completion> results = new ArrayList<>();
        int indent = 0;
        while (indent < before.length() && Character.isWhitespace(before.charAt(indent))) indent++;
        String trimmed = before.substring(indent);
        if (trimmed.startsWith(TOKEN_CATEGORY_OPEN)) {
            String prefix = unquoted(trimmed.substring(TOKEN_CATEGORY_OPEN.length())).toLowerCase(Locale.ROOT);
            int close = text.indexOf(TOKEN_CATEGORY_CLOSE, cursor);
            int replaceEnd = close >= 0 && close < end ? close + TOKEN_CATEGORY_CLOSE.length() : cursor;
            Range replace = range(text, DcSyntaxRuntime.policy("allowDottedCategories") ? start + indent : start, replaceEnd);
            for (var category : selected.categories()) {
                if (category.path().isEmpty() || category.path().contains("*")) continue;
                String name = String.join(TOKEN_CATEGORY_SEPARATOR, category.path());
                if (!name.toLowerCase(Locale.ROOT).startsWith(prefix)) continue;
                results.add(new Completion(TOKEN_CATEGORY_OPEN + name + TOKEN_CATEGORY_CLOSE, category.description(), header(category.path()), replace, 9));
            }
            return List.copyOf(results);
        }
        List<String> categoryPath = context(text.substring(0, start), before.substring(0, indent));
        if (categoryPath == null) return List.of();
        var category = catalog.category(selected, categoryPath);
        if (category == null) return List.of();
        int equals = before.indexOf(TOKEN_ASSIGNMENT);
        if (equals >= 0) {
            String name = fieldName(before.substring(indent, equals));
            var field = catalog.field(selected, categoryPath, name);
            if (field == null) return List.of();
            int valueStart = start + equals + TOKEN_ASSIGNMENT.length();
            while (valueStart < end && (text.charAt(valueStart) == ' ' || text.charAt(valueStart) == '\t')) valueStart++;
            int valueEnd = scalarEnd(text, valueStart, end);
            String prefix = unquoted(text.substring(Math.min(valueStart, cursor), cursor));
            Map<String, String> candidates = new LinkedHashMap<>();
            for (String value : field.enumValues()) candidates.put(value, DcSyntaxRuntime.quote(value, ""));
            if (candidates.isEmpty() && (field.type().equals("boolean") || field.type().equals("bool"))) {
                candidates.put(TRUE_LITERAL, TRUE_LITERAL); candidates.put(FALSE_LITERAL, FALSE_LITERAL);
            }
            for (var candidate : candidates.entrySet()) {
                String label = candidate.getKey();
                if (label.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) results.add(new Completion(label, field.description(), candidate.getValue(), range(text, valueStart, valueEnd), 12));
            }
        } else {
            String prefix = trimmed.toLowerCase(Locale.ROOT);
            int followingAssignment = text.indexOf(TOKEN_ASSIGNMENT, cursor);
            boolean existingValue = followingAssignment >= cursor && followingAssignment < end;
            int keyEnd = existingValue ? followingAssignment : cursor;
            while (keyEnd > start + indent && Character.isWhitespace(text.charAt(keyEnd - 1))) keyEnd--;
            for (var field : category.fields()) {
                if (field.name().equals("*") || !field.name().toLowerCase(Locale.ROOT).startsWith(prefix)) continue;
                String detail = field.type() + (field.required() ? " · required" : " · optional") + (field.description().isBlank() ? "" : " — " + field.description());
                String insertion = key(field.name()) + (existingValue ? "" : " " + TOKEN_ASSIGNMENT + " " + initialValue(field));
                results.add(new Completion(field.name(), detail, insertion, range(text, start + indent, keyEnd), 10));
            }
        }
        return List.copyOf(results);
    }

    public Hover hover(String uri, String text, Position position) { return hover(uri, text, position, null); }
    public Hover hover(String uri, String text, Position position, String profile) {
        DcDocument document = parse(text);
        if (document == null) return null;
        var selected = catalog.select(uri, document.root(), profile);
        if (selected == null) selected = catalog.suggestions();
        int offset = offset(text, position);
        for (DcParser.Binding binding : document.source.bindings()) {
            if (offset < binding.start() || offset >= binding.end()) continue;
            if (binding.kind() == DcParser.Kind.SECTION) {
                var category = catalog.category(selected, binding.path());
                return category == null ? null : new Hover("`" + String.join(".", category.path()) + "`" + (category.required() ? " — required category" : " — optional category") + "\n\n" + category.description(), range(text, binding.start(), Math.min(binding.end(), lineEnd(text, binding.start()))));
            }
            List<String> path = binding.path();
            var field = catalog.field(selected, path.subList(0, path.size() - 1), path.get(path.size() - 1));
            if (field == null) return null;
            String markdown = "`" + path.get(path.size() - 1) + "` · `" + field.type() + "` · " + (field.required() ? "required" : "optional") + "\n\n" + field.description();
            if (!field.enumValues().isEmpty()) markdown += "\n\nAllowed values: " + String.join(", ", field.enumValues().stream().map(value -> "`" + value + "`").toList());
            return new Hover(markdown, range(text, binding.start(), Math.min(binding.end(), lineEnd(text, binding.start()))));
        }
        return null;
    }

    public List<TextEdit> format(String text, int tabSize, boolean insertSpaces) {
        DcDocument document = parse(text);
        if (document == null) return List.of();
        int width = Math.max(1, Math.min(16, tabSize));
        List<Edit> edits = new ArrayList<>();
        for (DcParser.Binding binding : document.source.bindings()) {
            if (binding.kind() != DcParser.Kind.VALUE) continue;
            int start = binding.start();
            if (start == 0 && text.startsWith("\uFEFF")) start++;
            String prefix = text.substring(start, binding.valueStart());
            int equals = prefix.lastIndexOf(TOKEN_ASSIGNMENT);
            if (equals < 0) continue;
            String name = prefix.substring(0, equals).strip();
            String indent = indent(binding.indent(), width, insertSpaces);
            String replacement = indent + name + " " + TOKEN_ASSIGNMENT + " ";
            if (!prefix.equals(replacement)) edits.add(new Edit(start, binding.valueStart(), replacement));
        }
        try {
            for (DcCategoryHeader category : DcDocuments.categoryHeaders(text)) {
                int start = category.start() - category.indentation();
                String original = text.substring(start, category.end());
                String header = text.substring(category.start(), category.end());
                String replacement = indent(text.substring(start, category.start()), width, insertSpaces) +
                    (category.bracketed() ? TOKEN_CATEGORY_OPEN + header.substring(TOKEN_CATEGORY_OPEN.length(), header.length() - TOKEN_CATEGORY_CLOSE.length()).trim() + TOKEN_CATEGORY_CLOSE : header.substring(0, header.length() - TOKEN_BLOCK.length()).stripTrailing() + TOKEN_BLOCK);
                if (!original.equals(replacement)) edits.add(new Edit(start, category.end(), replacement));
            }
            edits.sort(Comparator.comparingInt(Edit::start));
            StringBuilder formatted = new StringBuilder();
            int copied = 0;
            for (Edit edit : edits) { formatted.append(text, copied, edit.start()).append(edit.text()); copied = edit.end(); }
            formatted.append(text, copied, text.length());
            String result = formatted.toString();
            if (result.equals(text) || !DcValues.same(document.root(), DcDocuments.readText(result).root())) return List.of();
            return List.of(new TextEdit(range(text, 0, text.length()), result));
        } catch (DcParseException exception) { return List.of(); }
    }

    public static int offset(String text, Position position) {
        int line = 0, start = 0;
        for (int index = 0; index < text.length() && line < position.line(); index++) {
            char next = text.charAt(index);
            if (next == '\r' || next == '\n') {
                if (next == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') index++;
                line++;
                start = index + 1;
            }
        }
        if (line < position.line()) return text.length();
        return start + Math.min(position.character(), lineEnd(text, start) - start);
    }
    public static Position position(String text, int offset) {
        int limit = Math.max(0, Math.min(offset, text.length())), line = 0, start = 0;
        for (int index = 0; index < limit; index++) {
            char next = text.charAt(index);
            if (next == '\r' || next == '\n') {
                if (next == '\r' && index + 1 < limit && text.charAt(index + 1) == '\n') index++;
                line++;
                start = index + 1;
            }
        }
        return new Position(line, limit - start);
    }
    private static Range range(String text, int start, int end) { return new Range(position(text, start), position(text, end)); }
    private static DcDocument parse(String text) { try { return DcDocuments.readText(text); } catch (DcParseException exception) { return null; } }
    private static DcParser.Binding binding(DcDocument document, String path) {
        DcParser.Binding closest = null;
        for (DcParser.Binding binding : document.source.bindings()) {
            String candidate = String.join(".", binding.path());
            if (candidate.equalsIgnoreCase(path)) return binding;
            if (binding.kind() == DcParser.Kind.SECTION && path.toLowerCase(Locale.ROOT).startsWith(candidate.toLowerCase(Locale.ROOT) + ".")) closest = binding;
        }
        return closest;
    }
    private static List<String> context(String prefix, String indent) {
        String marker = "__dc_completion_context__";
        DcDocument document = parse(prefix + indent + marker + " " + TOKEN_ASSIGNMENT + " " + TRUE_LITERAL + "\n");
        if (document == null) return null;
        for (DcParser.Binding binding : document.source.bindings()) if (binding.path().get(binding.path().size() - 1).equals(marker)) return binding.path().subList(0, binding.path().size() - 1);
        return null;
    }
    private static String fieldName(String text) {
        DcDocument document = parse(text.strip() + " " + TOKEN_ASSIGNMENT + " " + TRUE_LITERAL);
        return document == null || document.root().size() != 1 ? text.strip() : document.root().keySet().iterator().next();
    }
    private String header(List<String> path) { return DcWriter.formatHeader(path); }
    private String key(String name) { return DcWriter.formatKey(name); }
    private static String initialValue(DcSchemaCatalog.Field field) {
        if (field.defaultValue() != null) return DcWriter.formatValue(field.defaultValue(), "");
        if (!field.enumValues().isEmpty()) return DcSyntaxRuntime.quote(field.enumValues().get(0), "");
        return switch (field.type().split("\\|")[0]) {
            case "boolean", "bool" -> FALSE_LITERAL;
            case "number", "integer", "int" -> "0";
            case "array", "list" -> TOKEN_ARRAY_OPEN + TOKEN_ARRAY_CLOSE;
            case "object", "localized" -> TOKEN_OBJECT_OPEN + TOKEN_OBJECT_CLOSE;
            default -> DcSyntaxRuntime.quote("", "");
        };
    }
    private static String unquoted(String text) {
        for (var rule : STRING_RULES) if (text.startsWith(rule.delimiter())) {
            String value = text.substring(rule.delimiter().length());
            return value.endsWith(rule.delimiter()) ? value.substring(0, value.length() - rule.delimiter().length()) : value;
        }
        return text;
    }
    private static String indent(String original, int tabSize, boolean spaces) {
        int columns = 0;
        for (int index = 0; index < original.length(); index++) columns += original.charAt(index) == '\t' ? tabSize - columns % tabSize : 1;
        return spaces ? " ".repeat(columns) : "\t".repeat(columns / tabSize) + " ".repeat(columns % tabSize);
    }
    private static int scalarEnd(String text, int start, int lineEnd) {
        int end = lineEnd;
        for (int index = start; index < lineEnd;) {
            if (DcSyntaxRuntime.stringAt(text, index) != null) {
                int quotedEnd = DcSyntaxRuntime.quotedEnd(text, index);
                if (quotedEnd < 0 || quotedEnd > lineEnd) break;
                index = quotedEnd;
            } else if (DcSyntaxRuntime.commentAt(text, index)) { end = index; break; }
            else index++;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) end--;
        return end;
    }
    private static int lineStart(String text, int offset) { int start = offset; while (start > 0 && text.charAt(start - 1) != '\n' && text.charAt(start - 1) != '\r') start--; return start; }
    private static int lineEnd(String text, int offset) { int end = offset; while (end < text.length() && text.charAt(end) != '\n' && text.charAt(end) != '\r') end++; return end; }
}
