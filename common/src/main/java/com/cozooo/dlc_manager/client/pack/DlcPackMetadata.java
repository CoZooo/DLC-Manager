package com.cozooo.dlc_manager.client.pack;

import com.cozooo.dlc_manager.client.core.DlcMarkdownRenderer;
import com.cozooo.dlc_manager.client.core.DlcTextFormatter;
import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
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
    public final String platform;
    public final String gameVersion;
    public final String modLoader;
    public final List<String> authors;
    public final List<String> modpacks;
    public final boolean modpackAnyWildcard;
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
        String platform,
        String gameVersion,
        String modLoader,
        List<String> authors,
        List<String> modpacks,
        boolean modpackAnyWildcard,
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
        this.platform = normalizePlatform(platform);
        this.gameVersion = gameVersion == null ? "" : gameVersion;
        this.modLoader = modLoader == null ? "" : modLoader;
        this.authors = authors == null ? List.of() : List.copyOf(authors);
        this.modpacks = modpacks == null ? List.of() : List.copyOf(modpacks);
        this.modpackAnyWildcard = modpackAnyWildcard;
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
        return DlcMarkdownRenderer.renderMarkdownComponent(resolveDescription(lang));
    }

    public static Result read(JsonObject obj) {
        return read(obj, null);
    }

    public static Result read(Path path) {
        if (path == null) {
            return Result.invalid("missing pack.dc");
        }
        String text = null;
        try {
            text = Files.readString(path);
        } catch (Exception ignored) {
        }
        return read(DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC), text);
    }

    public static Result read(JsonObject obj, String sourceText) {
        if (obj == null) {
            return Result.invalid("missing pack.dc");
        }
        JsonObject basic = DlcDcConfigIO.getObjectIgnoreCase(obj, "basic");
        String identifier = trim(DlcDcConfigIO.getStringIgnoreCase(basic, "identifier"));
        if (!isValidIdentifier(identifier)) {
            return Result.invalid("invalid identifier");
        }
        String version = trim(DlcDcConfigIO.getStringIgnoreCase(basic, "version"));
        if (!isValidVersion(version)) {
            return Result.invalid("invalid version");
        }
        JsonObject modpack = DlcDcConfigIO.getObjectIgnoreCase(obj, "modpack");
        List<String> authors = readStringList(DlcDcConfigIO.getIgnoreCase(basic, "author"));
        if (authors.isEmpty()) {
            authors = List.of("Unknown Author");
        }
        SourceRules sourceRules = readSourceRules(sourceText);
        List<String> modpacks = sourceRules != null && sourceRules.modpack != null
            ? sourceRules.modpack
            : readRuleList(DlcDcConfigIO.getIgnoreCase(modpack, "modpack"));
        List<String> baseVersions = sourceRules != null && sourceRules.base != null
            ? sourceRules.base
            : readRuleList(DlcDcConfigIO.getIgnoreCase(modpack, "base"));
        List<String> dlcVersions = sourceRules != null && sourceRules.dlc != null
            ? sourceRules.dlc
            : readRuleList(DlcDcConfigIO.getIgnoreCase(modpack, "dlc"));
        return Result.valid(new DlcPackMetadata(
            identifier,
            version,
            normalizePlatform(DlcDcConfigIO.getStringIgnoreCase(basic, "platform")),
            sourceRules != null && sourceRules.basicGameVersion != null
                ? sourceRules.basicGameVersion
                : readRuleString(DlcDcConfigIO.getStringIgnoreCase(basic, "gameVersion")),
            sourceRules != null && sourceRules.basicModLoader != null
                ? sourceRules.basicModLoader
                : readRuleString(DlcDcConfigIO.getStringIgnoreCase(basic, "modLoader")),
            authors,
            modpacks,
            false,
            baseVersions,
            dlcVersions,
            readLocalized(DlcDcConfigIO.getObjectIgnoreCase(obj, "DisplayName")),
            readLocalized(DlcDcConfigIO.getObjectIgnoreCase(obj, "description")),
            readLinks(obj),
            readStringSection(DlcDcConfigIO.getIgnoreCase(obj, "dependencies")),
            readStringSection(DlcDcConfigIO.getIgnoreCase(obj, "conflicts"))
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

    public static String normalizePlatform(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? "universal" : trimmed.toLowerCase(Locale.ROOT);
    }

    public static String currentPlatform() {
        return CrossPlatformRuntime.isDedicatedServer() ? "server" : "client";
    }

    public static boolean platformMatches(String rule, String current) {
        String normalized = normalizePlatform(rule);
        if ("universal".equals(normalized)) {
            return true;
        }
        return current != null && normalized.equals(current.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean versionMatches(String rule, String current) {
        String normalized = trim(rule);
        if (normalized == null || normalized.isEmpty()) {
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
        return anyStringMatches(rules, current, true);
    }

    public static boolean anyStringMatches(List<String> rules, String current, boolean allowAnyWildcard) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        String currentValue = trim(current);
        if (currentValue == null || currentValue.isEmpty()) {
            return false;
        }
        for (String rule : rules) {
            String normalized = trim(rule);
            if (normalized == null || normalized.isEmpty()) {
                return true;
            }
            if (currentValue.equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return false;
    }

    public static boolean stringRuleMatches(String rule, String current) {
        String normalized = trim(rule);
        if (normalized == null || normalized.isEmpty()) {
            return true;
        }
        String currentValue = trim(current);
        return currentValue != null && !currentValue.isEmpty() && currentValue.equalsIgnoreCase(normalized);
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
        String current = DlcDcConfigIO.normalizeLangKey(lang);
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
            String lang = DlcDcConfigIO.normalizeLangKey(entry.getKey());
            String text = primitiveString(entry.getValue());
            if (lang != null && text != null) {
                out.put(lang, trimEnd(text));
            }
        }
        return out;
    }

    private static Map<String, String> readLinks(JsonObject root) {
        if (root == null) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        readLinksInto(out, DlcDcConfigIO.getObjectIgnoreCase(root, "links"));
        String lang = DlcDcConfigIO.normalizeLangKey(CrossPlatformRuntime.languageCode());
        if (lang != null && !lang.isBlank()) {
            JsonObject links = DlcDcConfigIO.getObjectIgnoreCase(root, "links");
            readLinksInto(out, DlcDcConfigIO.getObjectIgnoreCase(links, lang));
        }
        return out;
    }

    private static void readLinksInto(Map<String, String> out, JsonObject obj) {
        if (out == null || obj == null) {
            return;
        }
        for (String key : LINK_KEYS) {
            String value = trim(DlcDcConfigIO.getStringIgnoreCase(obj, key));
            if (value != null && !value.isEmpty()) {
                out.put(key, value);
            }
        }
    }

    private static String readRuleString(String value) {
        return stringOrEmpty(value);
    }

    private static List<String> readRuleList(JsonElement element) {
        return readStringList(element);
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
                String identifier = trim(DlcDcConfigIO.getStringIgnoreCase(value.getAsJsonObject(), "identifier"));
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

    private static SourceRules readSourceRules(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return null;
        }
        SourceRules rules = new SourceRules();
        String section = "";
        String[] lines = sourceText.split("\\R", -1);
        for (String line : lines) {
            String stripped = stripComment(line).trim();
            if (stripped.isEmpty()) {
                continue;
            }
            if (stripped.startsWith("[") && stripped.endsWith("]")) {
                section = stripped.substring(1, stripped.length() - 1).trim();
                if (isQuoted(section)) {
                    section = unquote(section);
                }
                continue;
            }
            int eq = stripped.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = stripped.substring(0, eq).trim();
            String value = stripped.substring(eq + 1).trim();
            if ("basic".equalsIgnoreCase(section)) {
                if ("gameVersion".equalsIgnoreCase(key)) {
                    rules.basicGameVersion = parseQuotedRule(value);
                } else if ("modLoader".equalsIgnoreCase(key)) {
                    rules.basicModLoader = parseQuotedRule(value);
                }
            } else if ("modpack".equalsIgnoreCase(section)) {
                if ("modpack".equalsIgnoreCase(key)) {
                    rules.modpack = parseQuotedRuleList(value);
                } else if ("base".equalsIgnoreCase(key)) {
                    rules.base = parseQuotedRuleList(value);
                } else if ("dlc".equalsIgnoreCase(key)) {
                    rules.dlc = parseQuotedRuleList(value);
                }
            }
        }
        return rules;
    }

    private static String parseQuotedRule(String raw) {
        String value = raw == null ? "" : raw.trim();
        return isQuoted(value) ? unquote(value).trim() : "";
    }

    private static List<String> parseQuotedRuleList(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return List.of();
        }
        if (!value.startsWith("[") || !value.endsWith("]")) {
            String item = parseQuotedRule(value);
            return item.isEmpty() ? List.of() : List.of(item);
        }
        List<String> out = new ArrayList<>();
        for (String token : splitArrayTokens(value.substring(1, value.length() - 1))) {
            String item = parseQuotedRule(token);
            if (!item.isEmpty()) {
                out.add(item);
            }
        }
        return out;
    }

    private static List<String> splitArrayTokens(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (escaped) {
                current.append(ch);
                escaped = false;
                continue;
            }
            if (quote != 0) {
                current.append(ch);
                if (quote == '"' && ch == '\\') {
                    escaped = true;
                } else if (ch == quote) {
                    quote = 0;
                }
                continue;
            }
            if (ch == '"' || ch == '\'') {
                quote = ch;
                current.append(ch);
                continue;
            }
            if (ch == ',') {
                out.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        out.add(current.toString().trim());
        return out;
    }

    private static boolean isQuoted(String value) {
        return value != null && value.length() >= 2
            && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")));
    }

    private static String unquote(String value) {
        String inner = value.substring(1, value.length() - 1);
        if (!value.startsWith("\"")) {
            return inner;
        }
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < inner.length(); i++) {
            char ch = inner.charAt(i);
            if (escaped) {
                switch (ch) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    default -> out.append(ch);
                }
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else {
                out.append(ch);
            }
        }
        if (escaped) {
            out.append('\\');
        }
        return out.toString();
    }

    private static String stripComment(String line) {
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (quote != 0) {
                if (quote == '"' && ch == '\\') {
                    escaped = true;
                } else if (ch == quote) {
                    quote = 0;
                }
                continue;
            }
            if (ch == '"' || ch == '\'') {
                quote = ch;
                continue;
            }
            if (ch == '#') {
                return line.substring(0, i);
            }
        }
        return line;
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

    private static String stringOrEmpty(String value) {
        String trimmed = trim(value);
        return trimmed == null ? "" : trimmed;
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

    private static class SourceRules {
        String basicGameVersion;
        String basicModLoader;
        List<String> modpack;
        List<String> base;
        List<String> dlc;
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
