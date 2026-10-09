package com.cozooo.dclanguage;

import com.google.gson.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Objects;

public final class DcDocument {
    private final JsonObject root;
    final JsonObject original;
    final DcParser.Parsed source;
    final Set<List<String>> sectionPaths = new LinkedHashSet<>();
    private boolean caseSensitive;

    public DcDocument(JsonObject root) { this(Objects.requireNonNull(root).deepCopy(), null); }
    DcDocument(JsonObject root, DcParser.Parsed source) {
        this.root = root;
        this.original = root.deepCopy();
        this.source = source;
    }
    public DcDocument asSection(String... path) {
        Objects.requireNonNull(path, "Category path");
        if (path.length == 0) throw new IllegalArgumentException("Category path is empty");
        List<String> actual = new ArrayList<>();
        JsonObject object = root;
        for (String part : path) {
            if (part == null || part.isBlank()) throw new IllegalArgumentException("Category name is blank");
            String key = actualKey(object, part, caseSensitive);
            JsonElement value = object.get(key);
            if (value == null || !value.isJsonObject()) throw new IllegalArgumentException("Category is missing or is not an object: " + String.join(".", path));
            actual.add(key);
            object = value.getAsJsonObject();
        }
        sectionPaths.add(List.copyOf(actual));
        return this;
    }
    public JsonObject root() { return root; }
    public JsonObject toJson() { return root.deepCopy(); }
    public DcDocument caseSensitive(boolean enabled) { caseSensitive = enabled; return this; }
    public boolean caseSensitive() { return caseSensitive; }
    public JsonObject category(String... path) { return objectAt(root, List.of(path), caseSensitive); }
    public JsonObject category(DcCategory category) { return objectAt(root, category.path(), category.caseSensitive()); }
    public JsonElement get(String category, String field) { return get(List.of(category), field); }
    public JsonElement get(List<String> category, String field) {
        JsonObject object = objectAt(root, category, caseSensitive);
        return object == null ? null : find(object, field, caseSensitive);
    }
    public <T> T get(DcCategory category, DcField<T> field) {
        JsonObject object = category(category);
        JsonElement value = object == null ? null : find(object, field.name(), category.caseSensitive());
        return value == null ? field.defaultValue() : field.type().decode(value);
    }
    public String getString(String category, String field) {
        JsonElement value = get(category, field);
        return value == null ? null : DcTypes.STRING.decode(value);
    }
    public DcDocument set(String category, String field, JsonElement value) { return setAt(List.of(category), field, value, caseSensitive); }
    public DcDocument set(List<String> category, String field, JsonElement value) { return setAt(category, field, value, caseSensitive); }
    public DcDocument set(String category, String field, String value) { return set(category, field, value == null ? JsonNull.INSTANCE : new JsonPrimitive(value)); }
    public DcDocument set(String category, String field, boolean value) { return set(category, field, new JsonPrimitive(value)); }
    public DcDocument set(String category, String field, Number value) { return set(category, field, value == null ? JsonNull.INSTANCE : new JsonPrimitive(value)); }
    public <T> DcDocument set(DcCategory category, DcField<T> field, T value) { return setAt(category.path(), field.name(), field.type().encode(value), category.caseSensitive()); }
    public DcDocument remove(String category, String field) { return remove(List.of(category), field); }
    public DcDocument remove(List<String> category, String field) {
        JsonObject object = objectAt(root, category, caseSensitive);
        if (object != null) object.remove(actualKey(object, field, caseSensitive));
        return this;
    }
    public DcDocument remove(DcCategory category, DcField<?> field) {
        JsonObject object = category(category);
        if (object != null) object.remove(actualKey(object, field.name(), category.caseSensitive()));
        return this;
    }
    private DcDocument setAt(List<String> path, String field, JsonElement value, boolean exact) {
        if (field == null || field.isBlank()) throw new IllegalArgumentException("Field name is blank");
        JsonObject object = root;
        for (String part : path) {
            if (part == null || part.isBlank()) throw new IllegalArgumentException("Category name is blank");
            String actual = actualKey(object, part, exact);
            JsonElement existing = object.get(actual);
            if (existing != null && !existing.isJsonObject()) throw new IllegalArgumentException("Category is a value: " + part);
            if (existing == null) object.add(actual, new JsonObject());
            object = object.getAsJsonObject(actual);
        }
        object.add(actualKey(object, field, exact), value == null ? JsonNull.INSTANCE : value.deepCopy());
        return this;
    }
    public String toText() { return DcDocuments.writeText(this); }
    public String toText(DcWriteOptions options) { return DcDocuments.writeText(this, options); }
    static JsonElement find(JsonObject object, String key) { return find(object, key, false); }
    private static JsonElement find(JsonObject object, String key, boolean exact) { return object.get(actualKey(object, key, exact)); }
    static String actualKey(JsonObject object, String key) { return actualKey(object, key, false); }
    private static String actualKey(JsonObject object, String key, boolean exact) {
        Objects.requireNonNull(key);
        if (exact) return key;
        String match = null;
        for (String candidate : object.keySet()) {
            if (!candidate.equalsIgnoreCase(key)) continue;
            if (match != null) throw new IllegalArgumentException("Ambiguous keys differing only in case: " + key);
            match = candidate;
        }
        return match == null ? key : match;
    }
    static JsonObject objectAt(JsonObject object, List<String> path) { return objectAt(object, path, false); }
    private static JsonObject objectAt(JsonObject object, List<String> path, boolean exact) {
        for (String part : path) {
            JsonElement value = find(object, part, exact);
            if (value == null || !value.isJsonObject()) return null;
            object = value.getAsJsonObject();
        }
        return object;
    }
    static JsonElement valueAt(JsonObject root, List<String> path) {
        JsonElement value = root;
        for (String part : path) {
            if (!value.isJsonObject()) return null;
            value = value.getAsJsonObject().get(part);
            if (value == null) return null;
        }
        return value;
    }
}
