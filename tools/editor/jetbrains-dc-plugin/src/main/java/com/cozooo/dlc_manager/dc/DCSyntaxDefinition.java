package com.cozooo.dlc_manager.dc;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

public final class DCSyntaxDefinition {
    public record StringRule(String delimiter, boolean multiline, boolean escaped) { }
    private final JsonObject definition;
    public final List<StringRule> strings;
    public final List<String> comments;
    public final List<String> operators;
    public final List<String> brackets;
    public final Pattern number;
    public final Pattern identifier;
    public final List<String> literals;
    public final boolean ignoreBooleanCase;
    public final boolean allowBom;

    public DCSyntaxDefinition(JsonObject definition) {
        this.definition = definition.deepCopy();
        JsonObject syntax = this.definition.getAsJsonObject("syntax");
        List<StringRule> strings = new ArrayList<>();
        for (JsonElement value : syntax.getAsJsonArray("stringRules")) {
            JsonObject rule = value.getAsJsonObject();
            strings.add(new StringRule(rule.get("delimiter").getAsString(), rule.get("multiline").getAsBoolean(), rule.get("escaped").getAsBoolean()));
        }
        strings.sort(Comparator.comparingInt((StringRule value) -> value.delimiter().length()).reversed());
        this.strings = List.copyOf(strings);
        comments = tokens("comment", "lineComment");
        operators = tokens("assignment", "block", "objectSeparator", "categorySeparator");
        brackets = tokens("categoryOpen", "categoryClose", "arrayOpen", "arrayClose", "objectOpen", "objectClose");
        number = Pattern.compile(syntax.get("numberPattern").getAsString());
        identifier = Pattern.compile(syntax.get("bareKeyPattern").getAsString());
        literals = syntax.getAsJsonObject("literals").entrySet().stream().map(value -> value.getValue().getAsString()).sorted(Comparator.comparingInt(String::length).reversed()).toList();
        JsonObject policies = this.definition.getAsJsonObject("policies");
        ignoreBooleanCase = policies.get("caseInsensitiveBooleans").getAsBoolean();
        allowBom = policies.get("allowBom").getAsBoolean();
    }

    public String token(String name) { return definition.getAsJsonObject("syntax").getAsJsonObject("tokens").get(name).getAsString(); }
    public String literal(String name) { return definition.getAsJsonObject("syntax").getAsJsonObject("literals").get(name).getAsString(); }
    public String definitionHash() { return definition.get("definitionHash").getAsString(); }
    private List<String> tokens(String... names) { return Arrays.stream(names).map(this::token).filter(value -> !value.isEmpty()).distinct().sorted(Comparator.comparingInt(String::length).reversed()).toList(); }
    public static DCSyntaxDefinition get() { return Holder.INSTANCE; }
    private static final class Holder {
        private static final DCSyntaxDefinition INSTANCE = load();
        private static DCSyntaxDefinition load() {
            try (var stream = DCSyntaxDefinition.class.getResourceAsStream("/com/cozooo/dclanguage/language-definition.json")) {
                if (stream == null) throw new IllegalStateException("Bundled DC syntax definition is missing");
                return new DCSyntaxDefinition(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException error) { throw new IllegalStateException("Cannot read DC syntax definition", error); }
        }
    }
}
