package com.cozooo.dlc_manager.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

public final class DcConfigIO {
    public enum Layout {
        DLC_STRUCTURED,
        GENERIC
    }

    private DcConfigIO() {
    }

    public static JsonObject read(Path path, Layout layout) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        if (!isDc(path)) {
            return null;
        }
        if (layout == Layout.DLC_STRUCTURED && !isNoticeConfigPath(path)) {
            return DcFormatUtil.read(path);
        }
        return DcFormatUtil.readGeneric(path);
    }

    public static boolean write(Path originalPath, JsonObject obj, Layout layout) {
        if (originalPath == null || obj == null) {
            return false;
        }
        Path target = DcFormatUtil.toDcPath(originalPath);
        if (target == null) {
            return false;
        }
        JsonObject source = obj;
        if (layout == Layout.DLC_STRUCTURED && isInfoPath(target)) {
            source = stripDownloadKeys(obj);
        }
        boolean written = layout == Layout.DLC_STRUCTURED && !isNoticeConfigPath(target)
            ? DcFormatUtil.write(target, source)
            : DcFormatUtil.writeGeneric(target, source);
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

    public static Path toDcPath(Path path) {
        return DcFormatUtil.toDcPath(path);
    }

    public static boolean isDc(Path path) {
        return path != null
            && path.getFileName() != null
            && DcFormatUtil.isDcName(path.getFileName().toString());
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
        String base = DcFormatUtil.stripConfigExtension(path.getFileName().toString());
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
