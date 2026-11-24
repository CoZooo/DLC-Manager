package com.cozooo.dlc_manager.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DlcDcConfigIO {
    public enum Layout {
        DLC_STRUCTURED,
        GENERIC
    }

    private DlcDcConfigIO() {
    }

    public static JsonObject read(Path path, Layout layout) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        if (!isDc(path)) {
            return null;
        }
        JsonObject result = layout == Layout.DLC_STRUCTURED && !isNoticeConfigPath(path)
            ? DlcDcFormatUtil.read(path)
            : DlcDcFormatUtil.readGeneric(path);
        canonicalizeAliases(result);
        return result;
    }

    public static boolean write(Path originalPath, JsonObject obj, Layout layout) {
        if (originalPath == null || obj == null) {
            return false;
        }
        Path target = DlcDcFormatUtil.toDcPath(originalPath);
        if (target == null) {
            return false;
        }
        canonicalizeAliases(obj);
        JsonObject source = obj;
        if (layout == Layout.DLC_STRUCTURED && isInfoPath(target)) {
            source = stripDownloadKeys(obj);
        }
        if (layout == Layout.DLC_STRUCTURED) {
            migrateAliasSpelling(target);
        }
        boolean written = layout == Layout.DLC_STRUCTURED && !isNoticeConfigPath(target)
            ? DlcDcFormatUtil.write(target, source)
            : DlcDcFormatUtil.writeGeneric(target, source);
        if (!written) {
            return false;
        }
        if (!target.equals(originalPath)) {
            try {
                Files.deleteIfExists(originalPath);
            } catch (IOException ignored) {
            }
        }
        return true;
    }

    private static void migrateAliasSpelling(Path path) {
        if (!Files.exists(path)) return;
        try {
            String original = Files.readString(path);
            Matcher matcher = Pattern.compile("(?m)^(\\s*)(file_name|filename|appliedTarget|isHidden)(\\s*=)").matcher(original);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                String raw = matcher.group(2);
                String canonical = switch (raw.toLowerCase(Locale.ROOT)) {
                    case "file_name", "filename" -> "source";
                    case "appliedtarget" -> "target";
                    case "ishidden" -> "hidden";
                    default -> raw;
                };
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1) + canonical + matcher.group(3)));
            }
            matcher.appendTail(buffer);
            String migrated = buffer.toString();
            if (!migrated.equals(original)) Files.writeString(path, migrated);
        } catch (IOException ignored) {
        }
    }

    public static Path toDcPath(Path path) {
        return DlcDcFormatUtil.toDcPath(path);
    }

    public static boolean isDc(Path path) {
        return path != null
            && path.getFileName() != null
            && DlcDcFormatUtil.isDcName(path.getFileName().toString());
    }

    public static JsonElement getIgnoreCase(JsonObject obj, String key) {
        if (obj == null || key == null) {
            return null;
        }
        JsonElement direct = obj.get(key);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String candidate = entry.getKey();
            if (candidate != null && candidate.equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        java.util.List<String> aliases = switch (key.toLowerCase(Locale.ROOT)) {
            case "source", "file_name", "filename" -> java.util.List.of("source", "file_name", "filename");
            case "target", "appliedtarget" -> java.util.List.of("target", "appliedTarget");
            case "hidden", "ishidden" -> java.util.List.of("hidden", "isHidden");
            default -> java.util.List.of();
        };
        for (String alias : aliases) {
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                String candidate = entry.getKey();
                if (candidate != null && candidate.equalsIgnoreCase(alias)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    public static boolean hasIgnoreCase(JsonObject obj, String key) {
        return getIgnoreCase(obj, key) != null;
    }

    public static JsonObject getObjectIgnoreCase(JsonObject obj, String key) {
        JsonElement element = getIgnoreCase(obj, key);
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        return null;
    }

    public static JsonArray getArrayIgnoreCase(JsonObject obj, String key) {
        JsonElement element = getIgnoreCase(obj, key);
        if (element != null && element.isJsonArray()) {
            return element.getAsJsonArray();
        }
        return null;
    }

    public static String getStringIgnoreCase(JsonObject obj, String key) {
        JsonElement element = getIgnoreCase(obj, key);
        if (element != null && element.isJsonPrimitive()) {
            return element.getAsString();
        }
        return null;
    }

    public static Boolean getBooleanIgnoreCase(JsonObject obj, String key) {
        JsonElement element = getIgnoreCase(obj, key);
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        }
        return null;
    }

    public static boolean getBooleanIgnoreCase(JsonObject obj, String key, boolean fallback) {
        Boolean value = getBooleanIgnoreCase(obj, key);
        return value == null ? fallback : value;
    }

    public static String normalizeLangKey(String key) {
        if (key == null) {
            return null;
        }
        return key.toLowerCase(Locale.ROOT);
    }

    public static void removeIgnoreCase(JsonObject obj, String key) {
        if (obj == null || key == null) {
            return;
        }
        if (obj.has(key)) {
            obj.remove(key);
            return;
        }
        String matched = null;
        for (String candidate : obj.keySet()) {
            if (candidate != null && candidate.equalsIgnoreCase(key)) {
                matched = candidate;
                break;
            }
        }
        if (matched != null) {
            obj.remove(matched);
        }
    }

    public static boolean canonicalizeAliases(JsonObject obj) {
        if (obj == null) {
            return false;
        }
        boolean changed = false;
        changed = promoteAlias(obj, "source", java.util.List.of("file_name", "filename")) || changed;
        changed = promoteAlias(obj, "target", java.util.List.of("appliedTarget")) || changed;
        changed = promoteAlias(obj, "hidden", java.util.List.of("isHidden")) || changed;
        for (JsonElement value : obj.asMap().values()) {
            if (value.isJsonObject()) {
                changed = canonicalizeAliases(value.getAsJsonObject()) || changed;
            } else if (value.isJsonArray()) {
                for (JsonElement child : value.getAsJsonArray()) {
                    if (child.isJsonObject()) {
                        changed = canonicalizeAliases(child.getAsJsonObject()) || changed;
                    }
                }
            }
        }
        return changed;
    }

    private static boolean promoteAlias(JsonObject obj, String canonical, java.util.List<String> aliases) {
        boolean changed = false;
        JsonElement replacement = obj.get(canonical);
        boolean replaced = false;
        JsonObject rebuilt = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (key.equalsIgnoreCase(canonical)) {
                if (!key.equals(canonical)) changed = true;
                rebuilt.add(canonical, value);
                replacement = value;
                replaced = true;
            } else if (aliases.stream().anyMatch(alias -> key.equalsIgnoreCase(alias))) {
                if (!replaced && replacement == null) {
                    rebuilt.add(canonical, value.deepCopy());
                    replacement = value;
                    replaced = true;
                }
                changed = true;
            } else {
                rebuilt.add(key, value);
            }
        }
        if (!replaced && replacement != null) rebuilt.add(canonical, replacement);
        if (changed) {
            obj.entrySet().clear();
            rebuilt.entrySet().forEach(entry -> obj.add(entry.getKey(), entry.getValue()));
        }
        return changed;
    }

    private static JsonElement findIgnoreCase(JsonObject obj, String key) {
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean isInfoPath(Path path) {
        if (path == null) {
            return false;
        }
        String normalized = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.contains("/info/");
    }

    private static boolean isNoticeConfigPath(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String base = DlcDcFormatUtil.stripConfigExtension(path.getFileName().toString());
        return "notice".equalsIgnoreCase(base);
    }

    private static JsonObject stripDownloadKeys(JsonObject input) {
        if (input == null) {
            return null;
        }
        JsonObject out = new JsonObject();
        for (var entry : input.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            if ("modrinth_projectid".equalsIgnoreCase(key)
                || "modrinth_versionid".equalsIgnoreCase(key)
                || "curseforge_projectid".equalsIgnoreCase(key)
                || "curseforge_fileid".equalsIgnoreCase(key)
                || "directUrl".equalsIgnoreCase(key)
                || "priority".equalsIgnoreCase(key)) {
                continue;
            }
            out.add(key, entry.getValue());
        }
        return out;
    }
}
