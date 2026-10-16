package com.cozooo.dclanguage;

import com.google.gson.JsonElement;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

public final class DcType<T> {
    private final String name;
    private final Predicate<JsonElement> accepts;
    private final Function<JsonElement, T> decode;
    private final Function<T, JsonElement> encode;

    public DcType(String name, Predicate<JsonElement> accepts, Function<JsonElement, T> decode, Function<T, JsonElement> encode) {
        this.name = Objects.requireNonNull(name);
        this.accepts = Objects.requireNonNull(accepts);
        this.decode = Objects.requireNonNull(decode);
        this.encode = Objects.requireNonNull(encode);
    }

    public String name() { return name; }
    public boolean accepts(JsonElement value) { return value != null && accepts.test(value); }
    public T decode(JsonElement value) {
        if (!accepts(value)) throw new IllegalArgumentException("Expected " + name);
        return decode.apply(value);
    }
    public JsonElement encode(T value) {
        JsonElement result = Objects.requireNonNull(encode.apply(value));
        if (!accepts(result)) throw new IllegalArgumentException("Expected " + name);
        return result;
    }
}
