package com.cozooo.dlc_manager.bootstrap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class DlcBootstrapI18n {
    private static final JsonObject ENGLISH = load("en_us");
    private static final JsonObject CHINESE = load("zh_cn");
    private DlcBootstrapI18n() {}

    public static String languageCode() {
        return "zh".equals(Locale.getDefault().getLanguage()) ? "zh_cn" : "en_us";
    }

    public static String text(String key, Object... arguments) {
        String languageKey = "dlc_manager.bootstrap." + key.toLowerCase(Locale.ROOT);
        JsonObject messages = "zh_cn".equals(languageCode()) ? CHINESE : ENGLISH;
        var value = messages.has(languageKey) ? messages.get(languageKey) : ENGLISH.get(languageKey);
        return String.format(Locale.ROOT, value == null ? languageKey : value.getAsString(), arguments);
    }

    private static JsonObject load(String language) {
        try (var input = DlcBootstrapI18n.class.getResourceAsStream("/assets/dlc_manager/lang/" + language + ".json")) {
            if (input == null) throw new IOException("Missing bootstrap language: " + language);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
