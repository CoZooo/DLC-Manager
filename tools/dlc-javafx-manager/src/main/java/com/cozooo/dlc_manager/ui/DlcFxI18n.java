package com.cozooo.dlc_manager.ui;

import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class DlcFxI18n {
    private static final Gson GSON = new Gson();
    private static final Map<String, Map<String, String>> CACHE = new HashMap<>();
    private static final String FALLBACK_LANG = "en_us";

    private DlcFxI18n() {
    }

    public static String tr(String key, Object... args) {
        String pattern = lookup(key);
        if (args == null || args.length == 0) {
            return pattern;
        }
        try {
            return String.format(Locale.ROOT, pattern, args);
        } catch (RuntimeException ignored) {
            return pattern;
        }
    }

    private static String lookup(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String lang = currentLang();
        String value = load(lang).get(key);
        if (value != null) {
            return value;
        }
        value = load(FALLBACK_LANG).get(key);
        return value == null ? key : value;
    }

    private static String currentLang() {
        String lang = DlcDcConfigIO.normalizeLangKey(CrossPlatformRuntime.languageCode());
        return lang == null || lang.isBlank() ? FALLBACK_LANG : lang;
    }

    private static Map<String, String> load(String lang) {
        return CACHE.computeIfAbsent(lang, DlcFxI18n::loadLangFile);
    }

    private static Map<String, String> loadLangFile(String lang) {
        Map<String, String> out = new HashMap<>();
        String resource = "/assets/dlc_manager/lang/" + lang + ".json";
        try (InputStream in = DlcFxI18n.class.getResourceAsStream(resource)) {
            if (in == null) {
                return out;
            }
            JsonObject object = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            if (object == null) {
                return out;
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                JsonElement value = entry.getValue();
                if (value != null && value.isJsonPrimitive()) {
                    out.put(entry.getKey(), value.getAsString());
                }
            }
        } catch (Throwable ignored) {
            out.clear();
        }
        return out;
    }
}
