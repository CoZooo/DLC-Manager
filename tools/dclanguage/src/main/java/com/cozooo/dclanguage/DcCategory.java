package com.cozooo.dclanguage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DcCategory {
    private final List<String> path;
    private final boolean required;
    private boolean caseSensitive;
    private final Map<String, DcFieldRule> fields = new LinkedHashMap<>();
    private final Map<String, DcCategory> children = new LinkedHashMap<>();

    DcCategory(List<String> path, boolean required, boolean caseSensitive) {
        this.path = List.copyOf(path);
        this.required = required;
        this.caseSensitive = caseSensitive;
    }

    public String name() { return path.isEmpty() ? "" : path.get(path.size() - 1); }
    public List<String> path() { return path; }
    public boolean required() { return required; }
    public boolean caseSensitive() { return caseSensitive; }
    public Map<String, DcFieldRule> fields() { return Collections.unmodifiableMap(fields); }
    public Map<String, DcCategory> categories() { return Collections.unmodifiableMap(children); }
    void caseSensitive(boolean value) { caseSensitive = value; }
    void addField(DcField<?> field, boolean required) { fields.put(field.name(), new DcFieldRule(field, required)); }
    void addCategory(DcCategory category) { children.put(category.name(), category); }
    public record DcFieldRule(DcField<?> field, boolean required) { }
}
