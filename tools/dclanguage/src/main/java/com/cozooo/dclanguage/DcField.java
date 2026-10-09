package com.cozooo.dclanguage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public final class DcField<T> {
    private final String name;
    private final DcType<T> type;
    private final List<Check<T>> checks;
    private final JsonElement defaultValue;

    public DcField(String name, DcType<T> type) { this(name, type, List.of(), null); }
    private DcField(String name, DcType<T> type, List<Check<T>> checks, JsonElement defaultValue) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Field name is blank");
        this.name = name;
        this.type = Objects.requireNonNull(type);
        this.checks = List.copyOf(checks);
        this.defaultValue = defaultValue == null ? null : defaultValue.deepCopy();
    }

    public static DcField<JsonElement> any(String name) { return new DcField<>(name, DcTypes.ANY); }
    public static DcField<String> string(String name) { return new DcField<>(name, DcTypes.STRING); }
    public static DcField<Boolean> bool(String name) { return new DcField<>(name, DcTypes.BOOLEAN); }
    public static DcField<BigDecimal> number(String name) { return new DcField<>(name, DcTypes.NUMBER); }
    public static DcField<List<JsonElement>> list(String name) { return new DcField<>(name, DcTypes.list(DcTypes.ANY)); }
    public static <T> DcField<List<T>> list(String name, DcType<T> element) { return new DcField<>(name, DcTypes.list(element)); }
    public static DcField<JsonObject> object(String name) { return new DcField<>(name, DcTypes.OBJECT); }
    public static DcField<String> enumeration(String name, String... values) { return new DcField<>(name, DcTypes.enumeration(values)); }
    public DcField<T> check(Predicate<T> predicate, String message) {
        List<Check<T>> updated = new ArrayList<>(checks);
        updated.add(new Check<>(Objects.requireNonNull(predicate), Objects.requireNonNull(message)));
        DcField<T> field = new DcField<>(name, type, updated, defaultValue);
        field.checkDefault();
        return field;
    }
    public DcField<T> defaultValue(T value) {
        DcField<T> field = new DcField<>(name, type, checks, type.encode(value));
        field.checkDefault();
        return field;
    }
    public String name() { return name; }
    public DcType<T> type() { return type; }
    public boolean hasDefault() { return defaultValue != null; }
    public T defaultValue() { return defaultValue == null ? null : type.decode(defaultValue.deepCopy()); }
    String validationMessage(JsonElement value) {
        T decoded = type.decode(value);
        for (Check<T> check : checks) if (!check.predicate().test(decoded)) return check.message();
        return null;
    }
    private void checkDefault() {
        if (defaultValue == null) return;
        String issue = validationMessage(defaultValue);
        if (issue != null) throw new IllegalArgumentException("Invalid default for " + name + ": " + issue);
    }
    private record Check<T>(Predicate<T> predicate, String message) { }
}
