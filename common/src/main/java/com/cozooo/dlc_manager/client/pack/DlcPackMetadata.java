package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.client.core.DlcTextFormatter;
import com.cozooo.dlc_manager.core.DcConfigIO;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DlcPackMetadata {
    private static final Set<String> LINK_KEYS = Set.of("homepage", "issue", "sponsor");
    private static final String VERSION_PATTERN = "^\\d+(?:\\.\\d+)*(?:[-+][A-Za-z0-9_.-]+)?$";
    private static final Set<Character> FORBIDDEN_IDENTIFIER_CHARS = Set.of('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000');

    public final String identifier;
    public final String version;
    public final List<String> authors;
    public final List<String> modpacks;
    public final List<String> baseVersions;
    public final List<String> dlcVersions;
    public final Map<String, String> displayNames;
    public final Map<String, String> descriptions;
    public final Map<String, String> links;
    public final List<String> dependencies;
    public final List<String> conflicts;

    public DlcPackMetadata(
        String identifier,
        String version,
        List<String> authors,
        List<String> modpacks,
        List<String> baseVersions,
        List<String> dlcVersions,
        Map<String, String> displayNames,
        Map<String, String> descriptions,
        Map<String, String> links,
        List<String> dependencies,
        List<String> conflicts
    ) {
        this.identifier = identifier;
        this.version = version;
        this.authors = authors == null ? List.of() : List.copyOf(authors);
        this.modpacks = modpacks == null ? List.of() : List.copyOf(modpacks);
        this.baseVersions = baseVersions == null ? List.of() : List.copyOf(baseVersions);
        this.dlcVersions = dlcVersions == null ? List.of() : List.copyOf(dlcVersions);
        this.displayNames = displayNames == null ? Map.of() : Map.copyOf(displayNames);
        this.descriptions = descriptions == null ? Map.of() : Map.copyOf(descriptions);
        this.links = links == null ? Map.of() : Map.copyOf(links);
        this.dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        this.conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }

    public String resolveDisplayName(String lang) {
        return resolveLocalized(displayNames, lang, identifier);
    }

    public String resolveDescription(String lang) {
        return resolveLocalized(descriptions, lang, "");
    }

    public Component displayNameComponent(String lang) {
        return DlcTextFormatter.parse(resolveDisplayName(lang));
    }

    public Component descriptionComponent(String lang) {
        return DlcTextFormatter.parse(resolveDescription(lang));
    }

    public static Result read(JsonObject obj) {
        if (obj == null) {
            return Result.invalid("missing pack.dc");
        }
        JsonObject basic = DcConfigIO.getObjectIgnoreCase(obj, "basic");
        String identifier = trim(DcConfigIO.getStringIgnoreCase(basic, "identifier"));
        if (!isValidIdentifier(identifier)) {
            return Result.invalid("invalid identifier");
        }
        String version = trim(DcConfigIO.getStringIgnoreCase(basic, "version"));
        if (!isValidVersion(version)) {
            return Result.invalid("invalid version");
        }
        JsonObject modpack = DcConfigIO.getObjectIgnoreCase(obj, "modpack");
        List<String> authors = readStringList(DcConfigIO.getIgnoreCase(basic, "author"));
        if (authors.isEmpty()) {
            authors = List.of("Unknown Author");
        }
        List<String> modpacks = readStringList(DcConfigIO.getIgnoreCase(modpack, "modpack"));
        if (modpacks.isEmpty()) {
            modpacks = List.of("any");
        }
        List<String> baseVersions = readStringList(DcConfigIO.getIgnoreCase(modpack, "base"));
        if (baseVersions.isEmpty()) {
            baseVersions = List.of("any");
        }
        List<String> dlcVersions = readStringList(DcConfigIO.getIgnoreCase(modpack, "dlc"));
        if (dlcVersions.isEmpty()) {
            dlcVersions = List.of("any");
        }
        return Result.valid(new DlcPackMetadata(
            identifier,
            version,
            authors,
            modpacks,
            baseVersions,
            dlcVersions,
            readLocalized(DcConfigIO.getObjectIgnoreCase(obj, "DisplayName")),
            readLocalized(DcConfigIO.getObjectIgnoreCase(obj, "description")),
            readLinks(DcConfigIO.getObjectIgnoreCase(obj, "links")),
            readStringSection(DcConfigIO.getIgnoreCase(obj, "dependencies")),
            readStringSection(DcConfigIO.getIgnoreCase(obj, "conflicts"))
        ));
    }

    public static boolean isValidIdentifier(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < 32 || FORBIDDEN_IDENTIFIER_CHARS.contains(ch)) {
                return false;
            }
        }
        try {
            Path.of(value);
            return true;
        } catch (InvalidPathException ex) {
            return false;
        }
    }

    public static boolean isValidVersion(String value) {
        return value != null && !value.isBlank() && value.trim().matches(VERSION_PATTERN);
    }

    public static boolean versionMatches(String rule, String current) {
        String normalized = trim(rule);
        if (normalized == null || normalized.isEmpty() || "any".equalsIgnoreCase(normalized)) {
            return true;
        }
        String currentValue = trim(current);
        if (currentValue == null || currentValue.isEmpty()) {
            return false;
        }
        if (normalized.toLowerCase(Locale.ROOT).endsWith(".x")) {
            return currentValue.startsWith(normalized.substring(0, normalized.length() - 1));
        }
        String[] split = normalized.split("~", 2);
        if (split.length == 2) {
            String lower = split[0].trim();
            String upper = split[1].trim();
            if (!lower.isEmpty() && compareVersions(currentValue, lower) < 0) {
                return false;
            }
            return upper.isEmpty() || compareVersions(currentValue, upper) <= 0;
        }
        return currentValue.equals(normalized);
    }

    public static boolean anyVersionMatches(List<String> rules, String current) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        for (String rule : rules) {
            if (versionMatches(rule, current)) {
                return true;
            }
        }
        return false;
    }

    public static boolean anyStringMatches(List<String> rules, String current) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        String currentValue = trim(current);
        if (currentValue == null || currentValue.isEmpty()) {
            for (String rule : rules) {
                String normalized = trim(rule);
                if (normalized != null && "any".equalsIgnoreCase(normalized)) {
                    return true;
                }
            }
            return false;
        }
        for (String rule : rules) {
            String normalized = trim(rule);
            if (normalized == null || normalized.isEmpty() || "any".equalsIgnoreCase(normalized)) {
                return true;
            }
            if (currentValue.equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return false;
    }

    public static int compareVersionStrings(String left, String right) {
        String leftValue = trim(left);
        String rightValue = trim(right);
        return compareVersions(leftValue == null ? "" : leftValue, rightValue == null ? "" : rightValue);
    }

    private static String resolveLocalized(Map<String, String> values, String lang, String fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        String current = DcConfigIO.normalizeLangKey(lang);
        if (current != null) {
            String exact = values.get(current);
            if (exact != null && !exact.isBlank()) {
                return exact;
            }
        }
        String english = values.get("en_us");
        if (english != null && !english.isBlank()) {
            return english;
        }
        for (String value : values.values()) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    private static Map<String, String> readLocalized(JsonObject obj) {
        if (obj == null) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String lang = DcConfigIO.normalizeLangKey(entry.getKey());
            String text = primitiveString(entry.getValue());
            if (lang != null && text != null) {
                out.put(lang, trimEnd(text));
            }
        }
        return out;
    }

    private static Map<String, String> readLinks(JsonObject obj) {
        if (obj == null) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : LINK_KEYS) {
            String value = trim(DcConfigIO.getStringIgnoreCase(obj, key));
            if (value != null && !value.isEmpty()) {
                out.put(key, value);
            }
        }
        return out;
    }

    private static List<String> readStringList(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (element.isJsonArray()) {
            List<String> out = new ArrayList<>();
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                String value = trim(primitiveString(item));
                if (value != null && !value.isEmpty()) {
                    out.add(value);
                }
            }
            return out;
        }
        String value = trim(primitiveString(element));
        return value == null || value.isEmpty() ? List.of() : List.of(value);
    }

    private static List<String> readStringSection(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonObject()) {
            return readStringList(element);
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            List<String> values = readStringList(entry.getValue());
            if (!values.isEmpty()) {
                out.addAll(values);
                continue;
            }
            JsonElement value = entry.getValue();
            if (value != null && value.isJsonObject()) {
                String identifier = trim(DcConfigIO.getStringIgnoreCase(value.getAsJsonObject(), "identifier"));
                if (identifier != null && !identifier.isEmpty()) {
                    out.add(identifier);
                } else if (entry.getKey() != null && !entry.getKey().isBlank()) {
                    out.add(entry.getKey().trim());
                }
            } else if (entry.getKey() != null && !entry.getKey().isBlank()) {
                out.add(entry.getKey().trim());
            }
        }
        return out;
    }

    private static String primitiveString(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        try {
            return element.getAsString();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimEnd(String value) {
        int end = value.length();
        while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private static int compareVersions(String left, String right) {
        String[] leftParts = left.toLowerCase(Locale.ROOT).split("[.+_-]");
        String[] rightParts = right.toLowerCase(Locale.ROOT).split("[.+_-]");
        int count = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < count; i++) {
            String l = i < leftParts.length ? leftParts[i] : "0";
            String r = i < rightParts.length ? rightParts[i] : "0";
            Long ln = parseLong(l);
            Long rn = parseLong(r);
            int cmp = ln != null && rn != null ? ln.compareTo(rn) : l.compareTo(r);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static class Result {
        public final boolean valid;
        public final DlcPackMetadata metadata;
        public final String error;

        private Result(boolean valid, DlcPackMetadata metadata, String error) {
            this.valid = valid;
            this.metadata = metadata;
            this.error = error;
        }

        public static Result valid(DlcPackMetadata metadata) {
            return new Result(true, metadata, null);
        }

        public static Result invalid(String error) {
            return new Result(false, null, error);
        }
    }
}
