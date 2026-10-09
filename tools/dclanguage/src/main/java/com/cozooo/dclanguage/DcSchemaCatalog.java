package com.cozooo.dclanguage;

import com.google.gson.*;
import com.cozooo.dclanguage.generated.DcGeneratedSyntax;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

public final class DcSchemaCatalog {
    public record Field(String name, String type, boolean required, String description, List<String> enumValues, JsonObject constraints, JsonElement defaultValue) {
        public Field { enumValues = List.copyOf(enumValues); constraints = constraints.deepCopy(); defaultValue = defaultValue == null ? null : defaultValue.deepCopy(); }
    }
    public record Category(List<String> path, boolean required, String description, List<Field> fields) {
        public Category { path = List.copyOf(path); fields = List.copyOf(fields); }
    }
    public record Profile(String id, String description, List<String> filePatterns, List<Category> categories) {
        public Profile { filePatterns = List.copyOf(filePatterns); categories = List.copyOf(categories); }
    }
    private final List<Profile> profiles;
    private final String definitionHash;
    private final Profile suggestions;
    private final String bareKeyPattern;

    private DcSchemaCatalog(JsonObject definition) {
        definitionHash = string(definition, "definitionHash", "");
        JsonObject syntax = definition.has("syntax") ? definition.getAsJsonObject("syntax") : new JsonObject();
        bareKeyPattern = string(syntax, "bareKeyPattern", DcGeneratedSyntax.BARE_KEY_PATTERN);
        List<Profile> loaded = new ArrayList<>();
        for (JsonElement value : array(definition, "profiles")) {
            JsonObject profile = value.getAsJsonObject();
            List<Category> categories = new ArrayList<>();
            categories(array(profile, "categories"), List.of(), categories);
            loaded.add(new Profile(string(profile, "id", ""), string(profile, "description", ""), strings(array(profile, "filePatterns")), categories));
        }
        profiles = List.copyOf(loaded);
        Map<List<String>, LinkedHashMap<String, Field>> fieldsByPath = new LinkedHashMap<>();
        Map<List<String>, String> descriptions = new LinkedHashMap<>();
        for (Profile profile : profiles) for (Category category : profile.categories()) {
            descriptions.putIfAbsent(category.path(), category.description());
            var fields = fieldsByPath.computeIfAbsent(category.path(), ignored -> new LinkedHashMap<>());
            for (Field field : category.fields()) fields.putIfAbsent(field.name().toLowerCase(Locale.ROOT), field);
        }
        List<Category> categories = new ArrayList<>();
        for (var entry : fieldsByPath.entrySet()) categories.add(new Category(entry.getKey(), false, descriptions.get(entry.getKey()), List.copyOf(entry.getValue().values())));
        suggestions = new Profile("", "Available DC fields", List.of(), categories);
    }
    public static DcSchemaCatalog fromJson(JsonObject definition) { return new DcSchemaCatalog(Objects.requireNonNull(definition)); }
    public static DcSchemaCatalog loadDefault() {
        try (var input = DcSchemaCatalog.class.getResourceAsStream("/com/cozooo/dclanguage/language-definition.json")) {
            if (input == null) throw new IllegalStateException("Generated DC language definition is missing");
            DcSchemaCatalog catalog = fromJson(JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject());
            if (!catalog.definitionHash().equals(DcGeneratedSyntax.DEFINITION_SHA256)) throw new IllegalStateException("Generated DC schema does not match the parser definition");
            return catalog;
        } catch (IOException exception) { throw new IllegalStateException("Cannot read generated DC language definition", exception); }
    }
    public List<Profile> profiles() { return profiles; }
    public Profile suggestions() { return suggestions; }
    public String definitionHash() { return definitionHash; }
    public String bareKeyPattern() { return bareKeyPattern; }
    public Profile profile(String id) { return profiles.stream().filter(value -> value.id().equals(id)).findFirst().orElse(null); }
    public Profile select(String uri, JsonObject values, String requested) {
        if (requested != null && !requested.isBlank()) {
            Profile selected = profile(requested);
            if (selected == null) throw new IllegalArgumentException("Unknown DC profile: " + requested);
            return selected;
        }
        String path = uri == null ? "" : uri;
        try { String parsed = URI.create(path).getPath(); if (parsed != null) path = parsed; } catch (IllegalArgumentException ignored) { }
        path = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        Profile selected = null;
        int best = -1;
        for (Profile profile : profiles) {
            for (String pattern : profile.filePatterns()) {
                if (!glob(pattern.toLowerCase(Locale.ROOT)).matcher(path).matches()) continue;
                int weight = pattern.replace("*", "").replace("?", "").length();
                if (weight > best) { best = weight; selected = profile; }
            }
        }
        if (selected != null) return selected;
        int bestScore = 0;
        boolean tied = false;
        if (values != null) for (Profile profile : profiles) {
            int score = 0;
            for (Category category : profile.categories()) {
                JsonObject object = DcDocument.objectAt(values, category.path());
                if (object == null) continue;
                for (Field field : category.fields()) if (DcDocument.find(object, field.name()) != null) score++;
            }
            if (score > bestScore) { bestScore = score; selected = profile; tied = false; }
            else if (score > 0 && score == bestScore) tied = true;
        }
        return selected != null && !tied ? selected : profile("generic");
    }
    public DcLanguage language(Profile profile) { return language(profile, null); }
    public DcLanguage language(Profile profile, JsonObject values) {
        DcLanguage language = DcLanguages.create();
        if (profile == null) return language;
        Map<List<String>, DcCategory> categories = new LinkedHashMap<>();
        categories.put(List.of(), language.rootCategory());
        List<Category> ordered = new ArrayList<>();
        for (Category category : profile.categories()) {
            for (List<String> path : expand(category.path(), values, 0, List.of())) {
                JsonObject object = values == null ? null : DcDocument.objectAt(values, path);
                List<Field> fields = new ArrayList<>();
                for (Field field : category.fields()) {
                    if (!field.name().equals("*")) fields.add(field);
                    else if (object != null) for (String name : object.keySet()) {
                        if (category.fields().stream().anyMatch(explicit -> explicit.name().equalsIgnoreCase(name))) continue;
                        fields.add(new Field(name, field.type(), false, field.description(), field.enumValues(), field.constraints(), field.defaultValue()));
                    }
                }
                ordered.add(new Category(path, category.required(), category.description(), fields));
            }
        }
        ordered.sort(Comparator.comparingInt(value -> value.path().size()));
        for (Category category : ordered) {
            DcCategory current = language.rootCategory();
            List<String> path = new ArrayList<>();
            for (String segment : category.path()) {
                path.add(segment);
                List<String> key = List.copyOf(path);
                DcCategory next = categories.get(key);
                if (next == null) {
                    next = language.registerCategory(current, segment, path.size() == category.path().size() && category.required());
                    categories.put(key, next);
                }
                current = next;
            }
            for (Field field : category.fields()) {
                DcType<JsonElement> type = new DcType<>(field.type(), value -> accepts(field, value), JsonElement::deepCopy, JsonElement::deepCopy);
                DcField<JsonElement> rule = new DcField<>(field.name(), type).check(value -> constraintIssue(field, value) == null, "Value does not satisfy the declared constraints");
                language.addField(current, rule, field.required());
            }
        }
        return language;
    }
    public Field field(Profile profile, List<String> path, String name) {
        Category category = category(profile, path);
        if (category == null) return null;
        return category.fields().stream().filter(field -> field.name().equalsIgnoreCase(name)).findFirst()
            .orElseGet(() -> category.fields().stream().filter(field -> field.name().equals("*")).findFirst().orElse(null));
    }
    public Category category(Profile profile, List<String> path) {
        if (profile == null) return null;
        return profile.categories().stream().filter(category -> samePath(category.path(), path)).findFirst().orElse(null);
    }
    private static boolean samePath(List<String> first, List<String> second) {
        if (first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++) if (!first.get(index).equals("*") && !first.get(index).equalsIgnoreCase(second.get(index))) return false;
        return true;
    }
    private static List<List<String>> expand(List<String> pattern, JsonObject object, int index, List<String> prefix) {
        if (index == pattern.size()) return List.of(prefix);
        String part = pattern.get(index);
        List<List<String>> paths = new ArrayList<>();
        Collection<String> names = part.equals("*") ? object == null ? List.of() : object.keySet() : List.of(part);
        for (String name : names) {
            JsonElement child = object == null ? null : DcDocument.find(object, name);
            List<String> path = new ArrayList<>(prefix); path.add(name);
            paths.addAll(expand(pattern, child != null && child.isJsonObject() ? child.getAsJsonObject() : null, index + 1, List.copyOf(path)));
        }
        return paths;
    }
    private static boolean accepts(Field field, JsonElement value) {
        boolean accepted = Arrays.stream(field.type().split("\\|")).anyMatch(type -> acceptsType(type.trim().toLowerCase(Locale.ROOT), value));
        if (!accepted || field.enumValues().isEmpty()) return accepted;
        return value.isJsonPrimitive() && field.enumValues().contains(value.getAsString());
    }
    private static boolean acceptsType(String type, JsonElement value) {
        if (type.endsWith("[]")) return value.isJsonArray() && value.getAsJsonArray().asList().stream().allMatch(item -> acceptsType(type.substring(0, type.length() - 2), item));
        return switch (type) {
            case "any", "value" -> true;
            case "null" -> value.isJsonNull();
            case "string", "enum" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
            case "boolean", "bool" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
            case "number" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
            case "integer", "int" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() && value.getAsBigDecimal().stripTrailingZeros().scale() <= 0;
            case "array", "list" -> value.isJsonArray();
            case "object", "localized" -> value.isJsonObject();
            default -> false;
        };
    }
    private static String constraintIssue(Field field, JsonElement value) {
        JsonObject constraints = field.constraints();
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            if (constraints.has("min") && value.getAsBigDecimal().compareTo(constraints.get("min").getAsBigDecimal()) < 0) return "min";
            if (constraints.has("max") && value.getAsBigDecimal().compareTo(constraints.get("max").getAsBigDecimal()) > 0) return "max";
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String text = value.getAsString();
            if (constraints.has("pattern") && !Pattern.compile(constraints.get("pattern").getAsString()).matcher(text).matches()) return "pattern";
            if (constraints.has("minLength") && text.codePointCount(0, text.length()) < constraints.get("minLength").getAsInt()) return "minLength";
            if (constraints.has("maxLength") && text.codePointCount(0, text.length()) > constraints.get("maxLength").getAsInt()) return "maxLength";
        }
        return null;
    }
    private static void categories(JsonArray values, List<String> parent, List<Category> output) {
        for (JsonElement value : values) {
            JsonObject category = value.getAsJsonObject();
            List<String> path = category.has("path") ? strings(category.getAsJsonArray("path")) : new ArrayList<>(parent);
            if (!category.has("path")) path.add(string(category, "name", ""));
            List<Field> fields = new ArrayList<>();
            for (JsonElement item : array(category, "fields")) {
                JsonObject field = item.getAsJsonObject();
                fields.add(new Field(string(field, "name", ""), string(field, "type", "any"), bool(field, "required"), string(field, "description", ""),
                    strings(array(field, "enumValues")), field.has("constraints") ? field.getAsJsonObject("constraints") : new JsonObject(), field.get("defaultValue")));
            }
            output.add(new Category(path, bool(category, "required"), string(category, "description", ""), fields));
            categories(array(category, "categories"), path, output);
        }
    }
    private static Pattern glob(String pattern) {
        StringBuilder expression = new StringBuilder("^(?:.*/)?");
        for (int index = 0; index < pattern.length(); index++) {
            char next = pattern.charAt(index);
            if (next == '*' && index + 1 < pattern.length() && pattern.charAt(index + 1) == '*') {
                index++;
                if (index + 1 < pattern.length() && pattern.charAt(index + 1) == '/') { index++; expression.append("(?:.*/)?"); }
                else expression.append(".*");
            } else if (next == '*') expression.append("[^/]*");
            else if (next == '?') expression.append("[^/]");
            else expression.append(Pattern.quote(String.valueOf(next)));
        }
        return Pattern.compile(expression.append('$').toString());
    }
    private static JsonArray array(JsonObject object, String name) { return object.has(name) && object.get(name).isJsonArray() ? object.getAsJsonArray(name) : new JsonArray(); }
    private static List<String> strings(JsonArray values) { List<String> result = new ArrayList<>(); for (JsonElement value : values) result.add(value.getAsString()); return result; }
    private static String string(JsonObject object, String name, String fallback) { return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : fallback; }
    private static boolean bool(JsonObject object, String name) { return object.has(name) && object.get(name).getAsBoolean(); }
}
