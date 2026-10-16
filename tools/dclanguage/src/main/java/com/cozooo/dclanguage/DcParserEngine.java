package com.cozooo.dclanguage;

import com.google.gson.*;
import static com.cozooo.dclanguage.generated.DcGeneratedSyntax.*;
import static com.cozooo.dclanguage.DcSyntaxRuntime.*;
import java.math.BigDecimal;
import java.util.*;

class DcParserEngine {
    protected DcParserEngine() { }
    enum Kind { VALUE, SECTION, ENTRY }
    record Binding(List<String> path, Kind kind, int start, int end, int valueStart, int valueEnd, int commentStart, String indent) { }
    static final class Section {
        final List<String> path;
        final int headerEnd;
        final String headerIndent;
        int insertAt;
        String bodyIndent;
        Section(List<String> path, int headerEnd, String headerIndent) {
            this.path = List.copyOf(path);
            this.headerEnd = headerEnd;
            this.headerIndent = headerIndent;
            this.insertAt = headerEnd;
        }
        String indent() { return bodyIndent != null ? bodyIndent : path.isEmpty() ? "" : headerIndent + " ".repeat(integer("indentWidth")); }
    }
    record Parsed(JsonObject root, String text, List<Binding> bindings, Map<List<String>, Section> sections, String newline) { }
    record Frame(int indent, List<String> path, JsonObject values, Section section, boolean block) { }

    static List<DcCategoryHeader> categoryHeaders(String text) throws DcParseException {
        List<DcCategoryHeader> headers = new ArrayList<>();
        int offset = 0;
        while (offset < text.length()) {
            int lineStart = offset;
            int lineEnd = lineEnd(text, offset);
            int end = afterLine(text, lineEnd);
            int start = lineStart;
            if (start == 0 && text.startsWith("\uFEFF")) {
                if (!policy("allowBom")) throw error("BOM is disabled by the language definition", text, 0);
                start++;
            }
            int indentStart = start;
            while (start < lineEnd && (text.charAt(start) == ' ' || text.charAt(start) == '\t')) start++;
            int contentEnd = commentStart(text, start, lineEnd);
            String content = text.substring(start, contentEnd).trim();
            if (content.startsWith(TOKEN_CATEGORY_OPEN)) {
                if (!content.endsWith(TOKEN_CATEGORY_CLOSE) || content.startsWith(TOKEN_CATEGORY_OPEN + TOKEN_CATEGORY_OPEN)) throw error("Invalid category header", text, start);
                headers.add(new DcCategoryHeader(keys(content.substring(TOKEN_CATEGORY_OPEN.length(), content.length() - TOKEN_CATEGORY_CLOSE.length()), text, start + TOKEN_CATEGORY_OPEN.length()),
                    start, start + content.length(), start - indentStart, true));
            } else if (!content.isEmpty()) {
                int eq = assignment(content);
                if (eq >= 0) {
                    int valueStart = start + eq + TOKEN_ASSIGNMENT.length();
                    while (valueStart < text.length() && (text.charAt(valueStart) == ' ' || text.charAt(valueStart) == '\t')) valueStart++;
                    ValueReader reader = new ValueReader(text, valueStart);
                    reader.value(false);
                    end = afterLine(text, lineEnd(text, reader.pos));
                } else if (content.endsWith(TOKEN_BLOCK)) {
                    headers.add(new DcCategoryHeader(List.of(key(content.substring(0, content.length() - TOKEN_BLOCK.length()), text, start)),
                        start, start + content.length(), start - indentStart, false));
                }
            }
            offset = end;
        }
        return List.copyOf(headers);
    }

    static Parsed parse(String text) throws DcParseException {
        JsonObject root = new JsonObject();
        List<Binding> bindings = new ArrayList<>();
        Map<List<String>, Section> sections = new LinkedHashMap<>();
        Section rootSection = new Section(List.of(), text.startsWith("\uFEFF") ? 1 : 0, "");
        sections.put(List.of(), rootSection);
        Deque<Frame> frames = new ArrayDeque<>();
        frames.push(new Frame(-1, List.of(), root, rootSection, false));
        Set<List<String>> assigned = new HashSet<>();
        int offset = 0;
        while (offset < text.length()) {
            int lineStart = offset;
            int lineEnd = lineEnd(text, offset);
            int end = afterLine(text, lineEnd);
            int start = lineStart;
            if (start == 0 && text.startsWith("\uFEFF")) {
                if (!policy("allowBom")) throw error("BOM is disabled by the language definition", text, 0);
                start++;
            }
            while (start < lineEnd && (text.charAt(start) == ' ' || text.charAt(start) == '\t')) start++;
            String indent = text.substring(lineStart == 0 && text.startsWith("\uFEFF") ? 1 : lineStart, start);
            int contentEnd = commentStart(text, start, lineEnd);
            String content = text.substring(start, contentEnd).trim();
            if (content.isEmpty()) { offset = end; continue; }
            if (content.startsWith(TOKEN_CATEGORY_OPEN)) {
                if (!content.endsWith(TOKEN_CATEGORY_CLOSE) || content.startsWith(TOKEN_CATEGORY_OPEN + TOKEN_CATEGORY_OPEN)) throw error("Invalid category header", text, start);
                List<String> names = keys(content.substring(TOKEN_CATEGORY_OPEN.length(), content.length() - TOKEN_CATEGORY_CLOSE.length()), text, start + TOKEN_CATEGORY_OPEN.length());
                if (names.size() > 1) while (frames.size() > 1) frames.pop();
                else while (frames.size() > 1 && frames.peek().indent() >= indent.length()) frames.pop();
                List<String> path = names.size() > 1 ? names : append(frames.peek().path(), names.get(0));
                JsonObject object = ensure(root, path, text, start);
                Section section = new Section(path, end, indent);
                sections.put(path, section);
                bindings.add(new Binding(path, Kind.SECTION, lineStart, end, -1, -1, contentEnd < lineEnd ? contentEnd : -1, indent));
                frames.push(new Frame(indent.length(), path, object, section, false));
                offset = end;
                continue;
            }
            closeBlocks(frames, indent.length());
            int eq = assignment(content);
            if (eq < 0) {
                boolean block = content.endsWith(TOKEN_BLOCK);
                if (block && !policy("allowColonCategories")) throw error("Object blocks are disabled", text, start);
                if (!block && !policy("allowBareEntries")) throw error("Bare entries are disabled", text, start);
                boolean trailing = !block && content.endsWith(TOKEN_ITEM_SEPARATOR);
                if (trailing && !policy("allowTrailingComma")) throw error("Trailing separators are disabled", text, start);
                String label = block ? content.substring(0, content.length() - TOKEN_BLOCK.length())
                    : trailing ? content.substring(0, content.length() - TOKEN_ITEM_SEPARATOR.length()) : content;
                String name = key(label, text, start);
                Frame parent = frames.peek();
                List<String> path = append(parent.path(), name);
                JsonObject object = ensure(root, path, text, start);
                Kind kind = block ? Kind.SECTION : Kind.ENTRY;
                if (!block && !assigned.add(path)) throw error("Duplicate entry: " + name, text, start);
                bindings.add(new Binding(path, kind, lineStart, end, -1, -1, contentEnd < lineEnd ? contentEnd : -1, indent));
                if (block) {
                    Section section = new Section(path, end, indent);
                    sections.put(path, section);
                    frames.push(new Frame(indent.length(), path, object, section, true));
                } else {
                    parent.section().insertAt = end;
                    parent.section().bodyIndent = indent;
                }
                offset = end;
                continue;
            }
            String name = key(content.substring(0, eq), text, start);
            Frame frame = frames.peek();
            List<String> path = append(frame.path(), name);
            if (!assigned.add(path) || frame.values().has(name)) throw error("Duplicate field: " + name, text, start);
            int valueStart = start + eq + TOKEN_ASSIGNMENT.length();
            while (valueStart < text.length() && (text.charAt(valueStart) == ' ' || text.charAt(valueStart) == '\t')) valueStart++;
            ValueReader reader = new ValueReader(text, valueStart);
            JsonElement value = reader.value(false);
            int valueEnd = reader.pos;
            int valueLineEnd = lineEnd(text, valueEnd);
            int tail = valueEnd;
            while (tail < valueLineEnd && Character.isWhitespace(text.charAt(tail))) tail++;
            if (tail < valueLineEnd && !isComment(text, tail)) throw error("Unexpected text after value", text, tail);
            end = afterLine(text, valueLineEnd);
            frame.values().add(name, value);
            bindings.add(new Binding(path, Kind.VALUE, lineStart, end, valueStart, valueEnd, tail < valueLineEnd ? tail : -1, indent));
            frame.section().insertAt = end;
            frame.section().bodyIndent = indent;
            offset = end;
        }
        return new Parsed(root, text, List.copyOf(bindings), sections, text.contains("\r\n") ? "\r\n" : text.indexOf('\n') < 0 && text.indexOf('\r') >= 0 ? "\r" : "\n");
    }

    private static void closeBlocks(Deque<Frame> frames, int indent) {
        Frame block = null;
        for (Frame frame : frames) {
            if (frame.block() && frame.indent() >= indent) block = frame;
        }
        if (block != null) {
            while (frames.pop() != block) { }
        }
    }

    static List<String> append(List<String> path, String name) {
        List<String> result = new ArrayList<>(path);
        result.add(name);
        return List.copyOf(result);
    }
    private static JsonObject ensure(JsonObject root, List<String> path, String text, int offset) throws DcParseException {
        if (path.size() > integer("maxDepth")) throw error("Category nesting exceeds " + integer("maxDepth") + " levels", text, offset);
        JsonObject current = root;
        for (String name : path) {
            JsonElement value = current.get(name);
            if (value != null && !value.isJsonObject()) throw error("Category collides with a field: " + name, text, offset);
            if (value == null) current.add(name, new JsonObject());
            current = current.getAsJsonObject(name);
        }
        return current;
    }
    private static int assignment(String text) {
        for (int i = 0; i < text.length();) {
            if (stringAt(text, i) != null) {
                int end = quotedEnd(text, i);
                if (end < 0) return -1;
                i = end;
            } else if (text.startsWith(TOKEN_ASSIGNMENT, i)) return i;
            else i++;
        }
        return -1;
    }
    private static List<String> keys(String raw, String text, int offset) throws DcParseException {
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < raw.length();) {
            if (stringAt(raw, i) != null) {
                int end = quotedEnd(raw, i);
                if (end < 0) throw error("Unterminated category name", text, offset + i);
                i = end;
            } else if (raw.startsWith(TOKEN_CATEGORY_SEPARATOR, i)) {
                if (!policy("allowDottedCategories")) throw error("Qualified categories are disabled", text, offset + i);
                parts.add(key(raw.substring(start, i), text, offset + start));
                i += TOKEN_CATEGORY_SEPARATOR.length();
                start = i;
            } else i++;
        }
        parts.add(key(raw.substring(start), text, offset + start));
        return parts;
    }
    private static String key(String raw, String text, int offset) throws DcParseException {
        int leading = 0;
        while (leading < raw.length() && Character.isWhitespace(raw.charAt(leading))) leading++;
        offset += leading;
        raw = raw.trim();
        if (raw.isEmpty()) throw error("Empty name", text, offset);
        var rule = stringAt(raw, 0);
        if (rule != null) {
            if (rule.multiline()) throw error("Multiline names are not allowed", text, offset);
            ValueReader reader = new ValueReader(text, offset);
            String name = reader.string();
            if (reader.pos != offset + raw.length() || name.isEmpty()) throw error("Invalid quoted name", text, offset);
            return name;
        }
        for (int i = 0; i < raw.length(); i++) {
            if (stringAt(raw, i) != null || startsAny(raw, i, TOKEN_CATEGORY_OPEN, TOKEN_CATEGORY_CLOSE,
                TOKEN_OBJECT_OPEN, TOKEN_OBJECT_CLOSE, TOKEN_ASSIGNMENT, TOKEN_ITEM_SEPARATOR)) throw error("Invalid name", text, offset + i);
        }
        return raw;
    }
    private static int lineEnd(String text, int start) {
        int end = start;
        while (end < text.length() && text.charAt(end) != '\n' && text.charAt(end) != '\r') end++;
        return end;
    }
    private static int afterLine(String text, int end) {
        if (end < text.length() && text.charAt(end) == '\r') end++;
        if (end < text.length() && text.charAt(end) == '\n') end++;
        return end;
    }
    private static boolean isComment(String text, int at) { return commentAt(text, at); }
    private static int commentStart(String text, int start, int end) {
        for (int i = start; i < end;) {
            if (stringAt(text, i) != null) {
                int after = quotedEnd(text, i);
                if (after < 0 || after > end) return end;
                i = after;
            } else if (isComment(text, i)) return i;
            else i++;
        }
        return end;
    }
    private static boolean startsAny(String text, int offset, String... tokens) {
        for (String token : tokens) if (text.startsWith(token, offset)) return true;
        return false;
    }
    private static DcParseException error(String message, String text, int offset) { return new DcParseException(message, text, offset); }

    private static final class ValueReader {
        private final String text;
        int pos;
        private int depth;
        ValueReader(String text, int start) { this.text = text; this.pos = start; }
        JsonElement value(boolean nested) throws DcParseException {
            if (pos >= text.length() || text.charAt(pos) == '\n' || text.charAt(pos) == '\r' || isComment(text, pos)) {
                if (nested || !policy("allowEmptyAssignment")) throw error("Missing value", text, pos);
                requireType("string");
                return new JsonPrimitive("");
            }
            if (stringAt(text, pos) != null) { requireType("string"); return new JsonPrimitive(string()); }
            if (startsAny(text, pos, TOKEN_ARRAY_OPEN, TOKEN_OBJECT_OPEN)) {
                if (++depth > integer("maxDepth")) throw error("Container nesting exceeds " + integer("maxDepth") + " levels", text, pos);
                boolean array = text.startsWith(TOKEN_ARRAY_OPEN, pos);
                requireType(array ? "array" : "object");
                JsonElement result = array ? array() : object();
                depth--;
                return result;
            }
            if (startsAny(text, pos, TOKEN_ARRAY_CLOSE, TOKEN_OBJECT_CLOSE) || nested && text.startsWith(TOKEN_ITEM_SEPARATOR, pos)) throw error("Unexpected delimiter", text, pos);
            int start = pos;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == '\n' || c == '\r' || isComment(text, pos) || nested && startsAny(text, pos, TOKEN_ITEM_SEPARATOR, TOKEN_ARRAY_CLOSE, TOKEN_OBJECT_CLOSE)) break;
                if (startsAny(text, pos, TOKEN_ARRAY_OPEN, TOKEN_ARRAY_CLOSE, TOKEN_OBJECT_OPEN, TOKEN_OBJECT_CLOSE) || stringAt(text, pos) != null) throw error("Quote strings containing delimiters", text, pos);
                pos++;
            }
            int end = pos;
            while (end > start && Character.isWhitespace(text.charAt(end - 1))) end--;
            pos = end;
            String raw = text.substring(start, end);
            if (raw.isEmpty()) throw error("Missing value", text, start);
            boolean trueValue = policy("caseInsensitiveBooleans") ? raw.equalsIgnoreCase(TRUE_LITERAL) : raw.equals(TRUE_LITERAL);
            boolean falseValue = policy("caseInsensitiveBooleans") ? raw.equalsIgnoreCase(FALSE_LITERAL) : raw.equals(FALSE_LITERAL);
            if (trueValue || falseValue) { requireType("boolean"); return new JsonPrimitive(trueValue); }
            if (raw.equals(NULL_LITERAL)) { requireType("null"); return JsonNull.INSTANCE; }
            if (raw.matches(NUMBER_PATTERN)) {
                requireType("number");
                try { return new JsonPrimitive(new BigDecimal(raw)); }
                catch (NumberFormatException exception) { throw error("Number is out of range", text, start); }
            }
            if (!policy("allowBareValues")) throw error("Bare values are disabled by the language definition", text, start);
            requireType("string");
            return new JsonPrimitive(raw);
        }
        private void requireType(String type) throws DcParseException {
            if (!VALUE_TYPES.contains(type)) throw error("Value type is disabled: " + type, text, pos);
        }
        private String string() throws DcParseException {
            int start = pos;
            var rule = stringAt(text, pos);
            if (rule == null) throw error("Expected quoted string", text, pos);
            pos += rule.delimiter().length();
            StringBuilder out = new StringBuilder();
            while (pos < text.length()) {
                if (text.startsWith(rule.delimiter(), pos)) { pos += rule.delimiter().length(); return out.toString(); }
                char c = text.charAt(pos);
                if (!rule.multiline() && (c == '\n' || c == '\r')) throw error("Unterminated string", text, start);
                if (rule.escaped() && text.startsWith(TOKEN_ESCAPE, pos)) {
                    pos += TOKEN_ESCAPE.length();
                    if (pos >= text.length()) throw error("Incomplete escape", text, pos);
                    if (text.startsWith(TOKEN_UNICODE_ESCAPE, pos)) {
                        pos += TOKEN_UNICODE_ESCAPE.length();
                        if (pos + 4 > text.length()) throw error("Incomplete Unicode escape", text, pos);
                        try { out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16)); }
                        catch (NumberFormatException exception) { throw error("Invalid Unicode escape", text, pos); }
                        pos += 4;
                    } else {
                        String match = null;
                        for (String escape : ESCAPES.keySet()) {
                            if (text.startsWith(escape, pos) && (match == null || escape.length() > match.length())) match = escape;
                        }
                        if (match != null) { out.append(ESCAPES.get(match)); pos += match.length(); }
                        else if (policy("preserveUnknownEscapes")) { out.append(TOKEN_ESCAPE).append(text.charAt(pos++)); }
                        else throw error("Unknown escape", text, pos);
                    }
                } else { out.append(c); pos++; }
            }
            throw error(rule.multiline() ? "Unterminated multiline string" : "Unterminated string", text, start);
        }
        private JsonArray array() throws DcParseException {
            int start = pos;
            pos += TOKEN_ARRAY_OPEN.length();
            JsonArray result = new JsonArray();
            whitespace();
            while (pos < text.length() && !text.startsWith(TOKEN_ARRAY_CLOSE, pos)) {
                if (text.startsWith(TOKEN_ITEM_SEPARATOR, pos)) throw error("Missing array item", text, pos);
                if (text.startsWith(TOKEN_OBJECT_CLOSE, pos)) throw error("Expected closing array delimiter", text, pos);
                result.add(value(true));
                separator(TOKEN_ARRAY_CLOSE);
            }
            if (pos >= text.length()) throw error("Unterminated array", text, start);
            pos += TOKEN_ARRAY_CLOSE.length();
            return result;
        }
        private JsonObject object() throws DcParseException {
            int start = pos;
            pos += TOKEN_OBJECT_OPEN.length();
            JsonObject result = new JsonObject();
            whitespace();
            while (pos < text.length() && !text.startsWith(TOKEN_OBJECT_CLOSE, pos)) {
                String name;
                if (stringAt(text, pos) != null) name = string();
                else {
                    int keyStart = pos;
                    while (pos < text.length() && !startsAny(text, pos, TOKEN_OBJECT_SEPARATOR, TOKEN_ASSIGNMENT, TOKEN_OBJECT_CLOSE) && !Character.isWhitespace(text.charAt(pos))) pos++;
                    name = key(text.substring(keyStart, pos), text, keyStart);
                }
                whitespace();
                if (name.isEmpty() || pos >= text.length() || !startsAny(text, pos, TOKEN_OBJECT_SEPARATOR, TOKEN_ASSIGNMENT)) throw error("Expected object key and separator", text, pos);
                String separator = List.of(TOKEN_ASSIGNMENT, TOKEN_OBJECT_SEPARATOR).stream()
                    .filter(token -> text.startsWith(token, pos)).max(Comparator.comparingInt(String::length)).orElseThrow();
                pos += separator.length();
                whitespace();
                if (pos >= text.length() || startsAny(text, pos, TOKEN_OBJECT_CLOSE, TOKEN_ITEM_SEPARATOR)) throw error("Missing object value", text, pos);
                if (result.has(name)) throw error("Duplicate object key: " + name, text, pos);
                result.add(name, value(true));
                separator(TOKEN_OBJECT_CLOSE);
            }
            if (pos >= text.length()) throw error("Unterminated object", text, start);
            pos += TOKEN_OBJECT_CLOSE.length();
            return result;
        }
        private void separator(String close) throws DcParseException {
            boolean newline = whitespace();
            if (pos < text.length() && text.startsWith(TOKEN_ITEM_SEPARATOR, pos)) {
                pos += TOKEN_ITEM_SEPARATOR.length();
                whitespace();
                if (text.startsWith(close, pos) && !policy("allowTrailingComma")) throw error("Trailing separators are disabled", text, pos);
            } else if (pos < text.length() && !text.startsWith(close, pos) && !(newline && policy("allowNewlineSeparator"))) throw error("Expected item separator", text, pos);
        }
        private boolean whitespace() {
            boolean newline = false;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (Character.isWhitespace(c)) { if (c == '\n' || c == '\r') newline = true; pos++; continue; }
                if (isComment(text, pos)) { pos = lineEnd(text, pos); continue; }
                break;
            }
            return newline;
        }
    }
}
