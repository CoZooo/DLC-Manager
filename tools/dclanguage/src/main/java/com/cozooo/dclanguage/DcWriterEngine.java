package com.cozooo.dclanguage;

import com.google.gson.JsonElement;
import static com.cozooo.dclanguage.generated.DcGeneratedSyntax.*;
import static com.cozooo.dclanguage.DcSyntaxRuntime.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

class DcWriterEngine {
    private final DcDocument document;
    private final String text;
    private final String newline;
    private final Map<List<String>, DcParser.Binding> values = new LinkedHashMap<>();
    private final List<Edit> edits = new ArrayList<>();
    private final Map<List<String>, JsonElement> originals = new LinkedHashMap<>();
    private final Map<List<String>, JsonElement> updated = new LinkedHashMap<>();
    private final Set<DcParser.Binding> deleted = new HashSet<>();
    private final Map<List<String>, Map<String, JsonElement>> additions = new LinkedHashMap<>();
    private final Set<List<String>> forcedSections = new HashSet<>();
    private final Map<List<String>, JsonObject> sectionAdditions = new LinkedHashMap<>();

    protected DcWriterEngine(DcDocument document) {
        this.document = document;
        this.text = document.source.text();
        this.newline = document.source.newline();
        for (List<String> path : document.sectionPaths) {
            if (!hasSectionHeader(path)) forcedSections.add(path);
        }
        for (DcParser.Binding binding : document.source.bindings()) {
            if (binding.kind() == DcParser.Kind.SECTION) continue;
            values.put(binding.path(), binding);
            try {
                JsonElement original = binding.kind() == DcParser.Kind.ENTRY ? new JsonObject()
                    : DcParser.parse("value " + TOKEN_ASSIGNMENT + " " + text.substring(binding.valueStart(), binding.valueEnd())).root().get("value");
                originals.put(binding.path(), original);
                updated.put(binding.path(), original.deepCopy());
            } catch (DcParseException exception) { throw new IllegalStateException("Cannot read recorded value", exception); }
        }
    }

    static String preserve(DcDocument document) {
        if (document.sectionPaths.isEmpty() && DcValues.same(document.original, document.root())) return document.source.text();
        DcWriterEngine writer = new DcWriterEngine(document);
        if (DcValues.same(document.original, document.root()) && writer.forcedSections.isEmpty()) return document.source.text();
        writer.compare(List.of(), document.original, document.root());
        return writer.apply();
    }

    private void compare(List<String> path, JsonElement previous, JsonElement current) {
        if (forcedSections.contains(path)) {
            remove(path);
            sectionAdditions.put(path, current.getAsJsonObject().deepCopy());
            return;
        }
        boolean requestedBelow = forcedSections.stream().anyMatch(section -> startsWith(section, path));
        if (DcValues.same(previous, current) && !requestedBelow) return;
        if (previous == null) {
            if (requestedBelow && current.isJsonObject()) {
                for (var entry : current.getAsJsonObject().entrySet()) compare(DcParser.append(path, entry.getKey()), null, entry.getValue());
            } else add(path, current);
            return;
        }
        if (current == null) { remove(path); return; }
        if (requestedBelow && !previous.isJsonObject() && current.isJsonObject()) {
            remove(path);
            for (var entry : current.getAsJsonObject().entrySet()) compare(DcParser.append(path, entry.getKey()), null, entry.getValue());
            return;
        }
        if (previous.isJsonObject() && current.isJsonObject()) {
            for (var entry : previous.getAsJsonObject().entrySet()) {
                compare(DcParser.append(path, entry.getKey()), entry.getValue(), current.getAsJsonObject().get(entry.getKey()));
            }
            for (var entry : current.getAsJsonObject().entrySet()) {
                if (!previous.getAsJsonObject().has(entry.getKey())) compare(DcParser.append(path, entry.getKey()), null, entry.getValue());
            }
            return;
        }
        if (values.containsKey(path)) {
            updated.put(path, current.deepCopy());
            removeBindings(path, false);
            return;
        }
        List<String> owner = owner(path, true);
        if (owner != null) {
            updateOwned(owner, path, current);
            removeBindings(path, true);
        } else {
            remove(path);
            add(path, current);
        }
    }

    private void remove(List<String> path) {
        removeBindings(path, true);
        List<String> owner = owner(path, true);
        if (owner != null) updateOwned(owner, path, null);
    }

    private void removeBindings(List<String> path, boolean includeSelf) {
        for (DcParser.Binding binding : document.source.bindings()) {
            if (!startsWith(binding.path(), path)) continue;
            if (!includeSelf && binding.path().equals(path) && binding.kind() != DcParser.Kind.SECTION) continue;
            if (!deleted.add(binding)) continue;
            int start = binding.start() == 0 && text.startsWith("\uFEFF") ? 1 : binding.start();
            String comments = retainedComments(text.substring(start, binding.end()), binding.indent());
            edits.add(new Edit(start, binding.end(), comments));
        }
    }

    private void add(List<String> path, JsonElement value) {
        List<String> owner = owner(path, false);
        if (owner != null) { updateOwned(owner, path, value); return; }
        List<String> parent = path.subList(0, path.size() - 1);
        additions.computeIfAbsent(parent, ignored -> new LinkedHashMap<>()).put(last(path), value);
    }

    private List<String> owner(List<String> path, boolean existing) {
        List<String> owner = null;
        for (var entry : originals.entrySet()) {
            List<String> candidate = entry.getKey();
            if (candidate.size() >= path.size() || !startsWith(path, candidate) || deleted.contains(values.get(candidate))) continue;
            List<String> relative = path.subList(candidate.size(), path.size());
            JsonElement value = entry.getValue();
            if (!value.isJsonObject()) continue;
            if (existing) {
                if (DcDocument.valueAt(value.getAsJsonObject(), relative) == null) continue;
            } else {
                List<String> parent = path.subList(0, path.size() - 1);
                boolean hasSection = document.source.sections().keySet().stream()
                    .anyMatch(section -> section.size() >= candidate.size() && startsWith(parent, section));
                if (hasSection) continue;
            }
            if (owner == null || candidate.size() > owner.size()) owner = candidate;
        }
        return owner;
    }

    private void updateOwned(List<String> owner, List<String> path, JsonElement value) {
        JsonObject object = updated.get(owner).getAsJsonObject();
        for (int i = owner.size(); i < path.size() - 1; i++) {
            String name = path.get(i);
            if (!object.has(name)) object.add(name, new JsonObject());
            object = object.getAsJsonObject(name);
        }
        if (value == null) object.remove(last(path));
        else object.add(last(path), value.deepCopy());
    }

    private void replaceValues() {
        for (var entry : updated.entrySet()) {
            DcParser.Binding binding = values.get(entry.getKey());
            if (deleted.contains(binding) || DcValues.same(originals.get(entry.getKey()), entry.getValue())) continue;
            JsonElement current = entry.getValue();
            if (binding.kind() == DcParser.Kind.VALUE) {
                String raw = text.substring(binding.valueStart(), binding.valueEnd());
                String value = formatValue(current, raw);
                String comments = retainedComments(raw, binding.indent());
                int start = binding.start() == 0 && text.startsWith("\uFEFF") ? 1 : binding.start();
                edits.add(new Edit(start, binding.valueEnd(), comments + text.substring(start, binding.valueStart()) + value));
            } else {
                String replacement = binding.indent() + formatKey(last(binding.path())) + " " + TOKEN_ASSIGNMENT + " " + formatValue(current, "");
                int end = binding.commentStart() < 0 ? contentEnd(binding.end()) : binding.commentStart();
                if (binding.commentStart() >= 0) replacement += " ";
                int start = binding.start() == 0 && text.startsWith("\uFEFF") ? 1 : binding.start();
                edits.add(new Edit(start, end, replacement));
            }
        }
    }

    private String apply() {
        replaceValues();
        Map<Integer, StringBuilder> insertions = new TreeMap<>();
        for (var entry : additions.entrySet()) {
            List<String> path = entry.getKey();
            DcParser.Section section = document.source.sections().get(path);
            boolean existing = section != null && (path.isEmpty() || !insideRemoval(section.headerEnd));
            int offset = existing ? section.insertAt : text.length();
            StringBuilder addition = insertions.computeIfAbsent(offset, ignored -> new StringBuilder());
            if (!existing) {
                appendHeaderPath(addition, path, newline);
            }
            String indent = existing ? section.indent() : sectionIndent(path);
            for (var value : entry.getValue().entrySet()) {
                addition.append(indent).append(formatKey(value.getKey())).append(" ").append(TOKEN_ASSIGNMENT).append(" ")
                    .append(formatValue(value.getValue(), "")).append(newline);
            }
        }
        for (var entry : sectionAdditions.entrySet()) {
            StringBuilder section = new StringBuilder();
            if (!policy("allowDottedCategories") && entry.getKey().size() > 1) {
                appendHeaderPath(section, entry.getKey().subList(0, entry.getKey().size() - 1), "\n");
            }
            writeObject(section, entry.getValue(), entry.getKey());
            StringBuilder addition = insertions.computeIfAbsent(text.length(), ignored -> new StringBuilder());
            if (!addition.isEmpty()) addition.append(newline);
            addition.append(section.toString().replace("\n", newline));
        }
        for (var entry : insertions.entrySet()) {
            String prefix = entry.getKey() > 0 && text.charAt(entry.getKey() - 1) != '\n' && text.charAt(entry.getKey() - 1) != '\r' && text.charAt(entry.getKey() - 1) != '\uFEFF' ? newline : "";
            edits.add(new Edit(entry.getKey(), entry.getKey(), prefix + entry.getValue()));
        }
        edits.sort(Comparator.comparingInt(Edit::start).thenComparingInt(Edit::end));
        StringBuilder result = new StringBuilder();
        int cursor = 0;
        for (Edit edit : edits) {
            if (edit.start() < cursor) throw new IllegalStateException("Overlapping document changes");
            result.append(text, cursor, edit.start()).append(edit.value());
            cursor = edit.end();
        }
        return result.append(text, cursor, text.length()).toString();
    }

    private static void appendHeaderPath(StringBuilder out, List<String> path, String newline) {
        if (policy("allowDottedCategories")) {
            out.append(TOKEN_CATEGORY_OPEN).append(formatPath(path)).append(TOKEN_CATEGORY_CLOSE).append(newline);
        } else {
            for (int index = 0; index < path.size(); index++) {
                out.append(" ".repeat(index * integer("indentWidth"))).append(TOKEN_CATEGORY_OPEN)
                    .append(formatKey(path.get(index))).append(TOKEN_CATEGORY_CLOSE).append(newline);
            }
        }
    }

    private static String sectionIndent(List<String> path) {
        return policy("allowDottedCategories") || path.isEmpty() ? "" : " ".repeat((path.size() - 1) * integer("indentWidth"));
    }

    private boolean hasSectionHeader(List<String> path) {
        for (DcParser.Binding binding : document.source.bindings()) {
            if (binding.kind() != DcParser.Kind.SECTION || !binding.path().equals(path)) continue;
            int start = binding.start();
            if (start == 0 && text.startsWith("\uFEFF")) start++;
            while (start < binding.end() && Character.isWhitespace(text.charAt(start))) start++;
            if (start < binding.end() && text.startsWith(TOKEN_CATEGORY_OPEN, start)) return true;
        }
        return false;
    }

    private boolean insideRemoval(int offset) {
        return edits.stream().anyMatch(edit -> edit.start() < offset && offset < edit.end());
    }

    private int contentEnd(int end) {
        if (end > 0 && text.charAt(end - 1) == '\n') end--;
        if (end > 0 && text.charAt(end - 1) == '\r') end--;
        return end;
    }

    private String retainedComments(String source, String indent) {
        StringBuilder comments = new StringBuilder();
        for (int i = 0; i < source.length();) {
            if (stringAt(source, i) != null) {
                int end = quotedEnd(source, i);
                i = end < 0 ? source.length() : end;
            } else if (commentAt(source, i)) {
                int end = i;
                while (end < source.length() && source.charAt(end) != '\r' && source.charAt(end) != '\n') end++;
                comments.append(indent).append(source, i, end).append(newline);
                i = end;
            } else i++;
        }
        return comments.toString();
    }

    static String normalize(JsonObject root) {
        StringBuilder out = new StringBuilder();
        writeObject(out, root, List.of());
        return out.toString();
    }

    private static void writeObject(StringBuilder out, JsonObject object, List<String> path) {
        boolean qualified = policy("allowDottedCategories");
        String indent = sectionIndent(path);
        if (!path.isEmpty()) {
            if (!out.isEmpty()) out.append('\n');
            String header = qualified ? formatPath(path) : formatKey(last(path));
            out.append(indent).append(TOKEN_CATEGORY_OPEN).append(header).append(TOKEN_CATEGORY_CLOSE).append('\n');
        }
        for (var entry : object.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                out.append(indent).append(formatKey(entry.getKey())).append(" ").append(TOKEN_ASSIGNMENT).append(" ")
                    .append(formatValue(entry.getValue(), "")).append('\n');
            }
        }
        for (var entry : object.entrySet()) {
            if (entry.getValue().isJsonObject()) writeObject(out, entry.getValue().getAsJsonObject(), DcParser.append(path, entry.getKey()));
        }
    }

    static String formatValue(JsonElement value, String original) {
        if (value == null || value.isJsonNull()) { requireType("null"); return NULL_LITERAL; }
        if (value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) { requireType("boolean"); return primitive.getAsBoolean() ? TRUE_LITERAL : FALSE_LITERAL; }
            if (primitive.isNumber()) {
                requireType("number");
                String number = primitive.toString();
                if (!number.matches(NUMBER_PATTERN)) throw new IllegalStateException("Number does not match the generated number rule");
                return number;
            }
            requireType("string");
            return DcSyntaxRuntime.quote(primitive.getAsString(), original);
        }
        if (value.isJsonArray()) {
            requireType("array");
            return value.getAsJsonArray().asList().stream().map(item -> formatValue(item, ""))
                .collect(Collectors.joining(TOKEN_ITEM_SEPARATOR + " ", TOKEN_ARRAY_OPEN, TOKEN_ARRAY_CLOSE));
        }
        requireType("object");
        return value.getAsJsonObject().entrySet().stream()
            .map(entry -> quote(entry.getKey()) + TOKEN_OBJECT_SEPARATOR + " " + formatValue(entry.getValue(), ""))
            .collect(Collectors.joining(TOKEN_ITEM_SEPARATOR + " ", TOKEN_OBJECT_OPEN, TOKEN_OBJECT_CLOSE));
    }
    private static void requireType(String type) {
        if (!VALUE_TYPES.contains(type)) throw new IllegalStateException("Value type is disabled: " + type);
    }
    static String formatHeader(List<String> path) {
        if (path.isEmpty()) throw new IllegalArgumentException("A category header needs a path");
        StringBuilder header = new StringBuilder();
        appendHeaderPath(header, path, "\n");
        return header.substring(0, header.length() - 1);
    }
    private static String formatPath(List<String> path) { return path.stream().map(DcWriterEngine::formatKey).collect(Collectors.joining(TOKEN_CATEGORY_SEPARATOR)); }
    static String formatKey(String key) { return key.matches(BARE_KEY_PATTERN) ? key : quote(key); }
    private static String quote(String value) { return DcSyntaxRuntime.quote(value, ""); }
    private static String last(List<String> path) { return path.get(path.size() - 1); }
    private static boolean startsWith(List<String> path, List<String> prefix) { return path.size() >= prefix.size() && path.subList(0, prefix.size()).equals(prefix); }
    private record Edit(int start, int end, String value) { }
}
