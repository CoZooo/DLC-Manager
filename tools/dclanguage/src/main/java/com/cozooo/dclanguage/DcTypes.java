package com.cozooo.dclanguage;

import com.google.gson.*;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashSet;

public final class DcTypes {
    private DcTypes() { }
    public static final DcType<String> STRING = new DcType<>("string", v -> v.isJsonPrimitive() && v.getAsJsonPrimitive().isString(), JsonElement::getAsString, JsonPrimitive::new);
    public static final DcType<Boolean> BOOLEAN = new DcType<>("boolean", v -> v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean(), JsonElement::getAsBoolean, JsonPrimitive::new);
    public static final DcType<BigDecimal> NUMBER = new DcType<>("number", DcTypes::isNumber, JsonElement::getAsBigDecimal, JsonPrimitive::new);
    public static final DcType<JsonObject> OBJECT = new DcType<>("object", JsonElement::isJsonObject, v -> v.getAsJsonObject().deepCopy(), JsonObject::deepCopy);
    public static final DcType<JsonElement> ANY = new DcType<>("value", v -> true, JsonElement::deepCopy, v -> v == null ? JsonNull.INSTANCE : v.deepCopy());

    public static final DcType<Void> NULL = new DcType<>("null", JsonElement::isJsonNull, v -> null, v -> JsonNull.INSTANCE);

    private static boolean isNumber(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return false;
        try { value.getAsBigDecimal(); return true; }
        catch (NumberFormatException exception) { return false; }
    }

    public static DcType<String> enumeration(String... values) {
        Set<String> allowed = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(values)));
        if (allowed.isEmpty() || allowed.contains(null)) throw new IllegalArgumentException("Enum values are empty or null");
        return new DcType<>("one of " + allowed, v -> STRING.accepts(v) && allowed.contains(v.getAsString()), JsonElement::getAsString, JsonPrimitive::new);
    }

    public static <T> DcType<List<T>> list(DcType<T> element) {
        Objects.requireNonNull(element);
        return new DcType<>("list of " + element.name(), v -> v.isJsonArray() && v.getAsJsonArray().asList().stream().allMatch(element::accepts), v -> {
            List<T> values = new ArrayList<>();
            v.getAsJsonArray().forEach(item -> values.add(element.decode(item)));
            return Collections.unmodifiableList(values);
        }, values -> {
            JsonArray array = new JsonArray();
            values.forEach(value -> array.add(element.encode(value)));
            return array;
        });
    }

    public static <T> DcType<T> nullable(DcType<T> type) {
        Objects.requireNonNull(type);
        return new DcType<>(type.name() + " or null", v -> v.isJsonNull() || type.accepts(v), v -> v.isJsonNull() ? null : type.decode(v), v -> v == null ? JsonNull.INSTANCE : type.encode(v));
    }
}
