package com.cozooo.dlc_bootstrap.launch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class DlcEarlyI18n {
    private static final JsonObject ENGLISH = load("en_us");
    private static final JsonObject CHINESE = load("zh_cn");
    private final JsonObject messages;

    DlcEarlyI18n() {
        this(Locale.getDefault());
    }

    DlcEarlyI18n(Locale locale) {
        messages = "zh".equals(locale.getLanguage()) ? CHINESE : ENGLISH;
    }

    String text(String key, Object... arguments) {
        String languageKey = "dlc_manager.bootstrap." + key.toLowerCase(Locale.ROOT);
        var value = messages.has(languageKey) ? messages.get(languageKey) : ENGLISH.get(languageKey);
        return String.format(Locale.ROOT, value == null ? languageKey : value.getAsString(), arguments);
    }

    static String nativeText(String key) {
        return new DlcEarlyI18n(Locale.ROOT).text(key);
    }

    private static JsonObject load(String language) {
        try (var input = DlcEarlyI18n.class.getResourceAsStream("/assets/dlc_manager/lang/" + language + ".json")) {
            if (input == null) throw new IOException("Missing bootstrap language: " + language);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
