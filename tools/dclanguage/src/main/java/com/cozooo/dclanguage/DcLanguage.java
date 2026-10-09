package com.cozooo.dclanguage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DcLanguage {
    private final DcCategory root = new DcCategory(List.of(), true, false);
    private final Map<List<String>, DcCategory> registered = new LinkedHashMap<>();
    private boolean caseSensitive = false;

    public DcLanguage() { registered.put(List.of(), root); }
    public boolean caseSensitive() { return caseSensitive; }
    public DcCategory rootCategory() { return root; }
    public Map<String, DcCategory> categories() { return root.categories(); }
    public DcLanguage caseSensitive(boolean enabled) {
        if (registered.size() > 1 || !root.fields().isEmpty()) throw new IllegalStateException("Set case policy before registering fields or categories");
        caseSensitive = enabled;
        root.caseSensitive(enabled);
        return this;
    }

    public DcCategory registerMainCategory(String name, boolean required) { return registerCategory(root, name, required); }
    public DcCategory registerCategory(DcCategory parent, String name, boolean required) {
        owned(parent);
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Category name is blank");
        if (lookup(parent.categories(), name) != null || lookup(parent.fields(), name) != null) throw new IllegalArgumentException("Category already registered: " + name);
        List<String> path = new ArrayList<>(parent.path());
        path.add(name);
        DcCategory category = new DcCategory(path, required, caseSensitive);
        parent.addCategory(category);
        registered.put(category.path(), category);
        return category;
    }

    public <T> DcField<T> addField(DcCategory category, DcField<T> field, boolean required) {
        owned(category);
        Objects.requireNonNull(field);
        if (lookup(category.fields(), field.name()) != null || lookup(category.categories(), field.name()) != null) throw new IllegalArgumentException("Field already registered: " + field.name());
        category.addField(field, required);
        return field;
    }

    private void owned(DcCategory category) {
        if (category == null || registered.get(category.path()) != category) throw new IllegalArgumentException("Category does not belong to this schema");
    }

    public DcValidationResult validate(DcDocument document) {
        Objects.requireNonNull(document);
        List<DcIssue> issues = new ArrayList<>();
        validateCategory(root, document.root(), issues);
        return new DcValidationResult(issues);
    }

    private void validateCategory(DcCategory category, JsonObject values, List<DcIssue> issues) {
        String prefix = String.join(".", category.path());
        if (!caseSensitive) {
            List<String> declared = new ArrayList<>(category.fields().keySet());
            declared.addAll(category.categories().keySet());
            for (String name : declared) {
                long matches = values.keySet().stream().filter(key -> key.equalsIgnoreCase(name)).count();
                if (matches > 1) issues.add(new DcIssue(join(prefix, name), "Ambiguous keys differing only in case"));
            }
        }
        for (DcCategory.DcFieldRule rule : category.fields().values()) {
            DcField<?> field = rule.field();
            JsonElement value = lookup(values.asMap(), field.name());
            String path = join(prefix, field.name());
            if (value == null) {
                if (rule.required()) issues.add(new DcIssue(path, "Required field is missing"));
            } else if (!field.type().accepts(value)) {
                issues.add(new DcIssue(path, "Expected " + field.type().name()));
            } else {
                String message = field.validationMessage(value);
                if (message != null) issues.add(new DcIssue(path, message));
            }
        }
        for (DcCategory child : category.categories().values()) {
            JsonElement value = lookup(values.asMap(), child.name());
            String path = String.join(".", child.path());
            if (value == null) {
                if (child.required()) issues.add(new DcIssue(path, "Required category is missing"));
            } else if (!value.isJsonObject()) issues.add(new DcIssue(path, "Category must be an object"));
            else validateCategory(child, value.getAsJsonObject(), issues);
        }
    }

    private <T> T lookup(Map<String, T> values, String name) {
        T direct = values.get(name);
        if (direct != null || caseSensitive) return direct;
        for (var entry : values.entrySet()) if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        return null;
    }
    private static String join(String category, String field) { return category.isEmpty() ? field : category + "." + field; }
}
