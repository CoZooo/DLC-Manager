package com.cozooo.dlc_manager.tools.externalsync;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

final class DcRemoteLoader {
    private DcRemoteLoader() {
    }

    static List<SyncModels.RemoteEntry> load(Path dlcRoot) throws IOException {
        Path remoteRoot = dlcRoot.resolve("remote");
        Path redirectRoot = remoteRoot.resolve("redirect").normalize();
        if (!Files.isDirectory(remoteRoot)) {
            return List.of();
        }
        List<SyncModels.RemoteEntry> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(remoteRoot)) {
            List<Path> configs = stream
                .filter(Files::isRegularFile)
                .filter(DcRemoteLoader::isDcFile)
                .filter(path -> !path.normalize().startsWith(redirectRoot))
                .sorted()
                .toList();
            for (Path config : configs) {
                SyncModels.RemoteEntry entry = readEntry(config);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }
        return entries;
    }

    static Map<String, Value> parseFlat(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        Map<String, Value> values = new LinkedHashMap<>();
        String section = "";
        String[] lines = text.split("\\R", -1);
        for (String rawLine : lines) {
            String line = stripComment(rawLine).trim();
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]") && line.length() > 2) {
                section = unquote(line.substring(1, line.length() - 1).trim());
                continue;
            }
            int equalIndex = line.indexOf('=');
            if (equalIndex < 0) {
                if (isRelationSection(section)) {
                    String relation = unquote(line.replaceFirst(":$", "").trim());
                    if (!relation.isBlank()) {
                        values.put(normalized(section) + "." + normalized(relation), Value.string(relation));
                    }
                }
                continue;
            }
            String key = unquote(line.substring(0, equalIndex).trim());
            String value = line.substring(equalIndex + 1).trim();
            values.put(normalizeKey(section, key), parseValue(value));
        }
        return values;
    }

    static String extractFileName(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String path = URI.create(url).getPath();
            if (path == null || path.isBlank()) {
                return null;
            }
            int slashIndex = path.lastIndexOf('/');
            String fileName = slashIndex >= 0 ? path.substring(slashIndex + 1) : path;
            return sanitizeFileName(URLDecoder.decode(fileName, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    static String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        String sanitized = fileName.replace('\\', '/');
        int slashIndex = sanitized.lastIndexOf('/');
        if (slashIndex >= 0) {
            sanitized = sanitized.substring(slashIndex + 1);
        }
        sanitized = sanitized.replaceAll("[\\p{Cntrl}<>:\\\"/\\\\|?*]+", "_").trim();
        return sanitized.isBlank() ? null : sanitized;
    }

    private static SyncModels.RemoteEntry readEntry(Path config) throws IOException {
        Map<String, Value> values = parseFlat(Files.readString(config, StandardCharsets.UTF_8));
        if (values.isEmpty()) {
            return null;
        }
        String fallback = stripDc(config.getFileName().toString());
        List<String> directUrls = stringList(values, "directurl");
        String directUrl = directUrls.isEmpty() ? null : directUrls.get(0);
        String fileName = firstNonBlank(
            stringValue(values, "file_name"),
            stringValue(values, "filename"),
            stringValue(values, "dlc_name"),
            extractFileName(directUrl),
            fallback
        );
        String identifier = firstNonBlank(
            stringValue(values, "identifier"),
            stringValue(values, "dlc_identifier"),
            stringValue(values, "dlc_name"),
            fileName,
            fallback
        );
        SyncModels.RemoteEntry entry = new SyncModels.RemoteEntry();
        entry.configPath = config.toAbsolutePath().normalize().toString();
        entry.identifier = identifier;
        entry.fileName = fileName;
        entry.dlcType = firstNonBlank(stringValue(values, "type"), stringValue(values, "dlc_type"), "MOD");
        entry.curseforgeProjectId = blankToNull(stringValue(values, "curseforge_projectid"));
        entry.curseforgeFileId = blankToNull(stringValue(values, "curseforge_fileid"));
        entry.modrinthProjectId = blankToNull(stringValue(values, "modrinth_projectid"));
        entry.modrinthVersionId = blankToNull(stringValue(values, "modrinth_versionid"));
        entry.directUrls = directUrls;
        entry.priority = priorityList(values);
        entry.dependencies = relationNames(values, "dependencies");
        entry.conflicts = relationNames(values, "conflicts");
        return entry;
    }

    private static Value parseValue(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.endsWith(",")) {
            value = value.substring(0, value.length() - 1).trim();
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            return Value.array(splitArray(value.substring(1, value.length() - 1)).stream().map(DcRemoteLoader::parseValue).toList());
        }
        return Value.string(unquote(value));
    }

    private static List<String> splitArray(String raw) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index < raw.length(); index++) {
            char character = raw.charAt(index);
            if (quoted) {
                current.append(character);
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == quote) {
                    quoted = false;
                }
            } else if (character == '"' || character == '\'') {
                quoted = true;
                quote = character;
                current.append(character);
            } else if (character == ',' || character == '\n' || character == '\r') {
                addToken(tokens, current);
            } else {
                current.append(character);
            }
        }
        addToken(tokens, current);
        return tokens;
    }

    private static String stripComment(String raw) {
        boolean quoted = false;
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index < raw.length(); index++) {
            char character = raw.charAt(index);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == quote) {
                    quoted = false;
                }
                continue;
            }
            if (character == '"' || character == '\'') {
                quoted = true;
                quote = character;
            } else if (character == '#') {
                return raw.substring(0, index);
            } else if (character == '/' && index + 1 < raw.length() && raw.charAt(index + 1) == '/' && !(index > 0 && raw.charAt(index - 1) == ':')) {
                return raw.substring(0, index);
            }
        }
        return raw;
    }

    private static List<String> priorityList(Map<String, Value> values) {
        List<String> order = new ArrayList<>();
        for (String item : stringList(values, "priority")) {
            for (String part : item.split("[,;>\\s]+")) {
                String normalized = normalizePriority(part);
                if (!normalized.isBlank() && !order.contains(normalized)) {
                    order.add(normalized);
                }
            }
        }
        if (order.isEmpty()) {
            order.add("curseforge");
            order.add("modrinth");
            order.add("directurl");
        }
        return order;
    }

    private static List<String> relationNames(Map<String, Value> values, String section) {
        String normalizedSection = normalized(section);
        Set<String> names = new LinkedHashSet<>();
        Value direct = values.get(normalizedSection);
        if (direct != null) {
            names.addAll(direct.asStrings());
        }
        for (String key : values.keySet()) {
            if (key.startsWith(normalizedSection + ".")) {
                names.add(key.substring(normalizedSection.length() + 1));
            }
        }
        return names.stream().filter(value -> value != null && !value.isBlank()).toList();
    }

    private static List<String> stringList(Map<String, Value> values, String key) {
        Value value = values.get(normalized(key));
        if (value == null) {
            return new ArrayList<>();
        }
        return value.asStrings().stream().filter(item -> item != null && !item.isBlank()).map(String::trim).toList();
    }

    private static String stringValue(Map<String, Value> values, String key) {
        List<String> valuesList = stringList(values, key);
        return valuesList.isEmpty() ? null : valuesList.get(0);
    }

    private static String normalizePriority(String value) {
        String normalized = normalized(value).replace("-", "").replace("_", "");
        return switch (normalized) {
            case "direct", "directurl", "url" -> "directurl";
            case "curse", "curseforge", "cf" -> "curseforge";
            case "modrinth", "mr" -> "modrinth";
            default -> "";
        };
    }

    private static String normalizeKey(String section, String key) {
        String normalizedSection = normalized(section);
        if (normalizedSection.isBlank() || normalizedSection.equals("download") || normalizedSection.equals("basic")) {
            return normalized(key);
        }
        return normalizedSection + "." + normalized(key);
    }

    private static boolean isRelationSection(String section) {
        String normalizedSection = normalized(section);
        return normalizedSection.equals("dependencies") || normalizedSection.equals("conflicts");
    }

    private static boolean isDcFile(Path path) {
        return path.getFileName() != null && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".dc");
    }

    private static String stripDc(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".dc") ? name.substring(0, name.length() - 3) : name;
    }

    private static void addToken(List<String> tokens, StringBuilder current) {
        String token = current.toString().trim();
        if (!token.isBlank()) {
            tokens.add(token);
        }
        current.setLength(0);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String unquote(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'")))) {
            return unescape(trimmed.substring(1, trimmed.length() - 1));
        }
        return trimmed;
    }

    private static String unescape(String value) {
        return value
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("\\\\", "\\");
    }

    static final class Value {
        private final String scalar;
        private final List<Value> array;

        private Value(String scalar, List<Value> array) {
            this.scalar = scalar;
            this.array = array;
        }

        static Value string(String value) {
            return new Value(value == null ? "" : value, null);
        }

        static Value array(List<Value> values) {
            return new Value(null, values == null ? List.of() : values);
        }

        List<String> asStrings() {
            if (array == null) {
                return scalar == null ? List.of() : List.of(scalar);
            }
            List<String> result = new ArrayList<>();
            for (Value value : array) {
                result.addAll(value.asStrings());
            }
            return result;
        }
    }
}
