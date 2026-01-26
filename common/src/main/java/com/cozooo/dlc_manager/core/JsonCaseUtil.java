package com.cozooo.dlc_manager.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.Map;

public final class JsonCaseUtil {
    private JsonCaseUtil() {
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
}
