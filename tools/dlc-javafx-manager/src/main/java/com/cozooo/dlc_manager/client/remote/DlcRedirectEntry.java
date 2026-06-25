package com.cozooo.dlc_manager.client.remote;

import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DlcRedirectEntry {
    private Path configPath;
    private String available;
    private final Set<String> availableLangs = new LinkedHashSet<>();
    private boolean hidden;
    private String redirectUrl;
    private String fallbackName;
    private final Map<String, String> displayNames = new HashMap<>();
    private final Map<String, String> notices = new HashMap<>();
    private final Map<String, String> tips = new HashMap<>();

    public Path getConfigPath() {
        return configPath;
    }

    public void setConfigPath(Path configPath) {
        this.configPath = configPath;
    }

    public String getAvailable() {
        return available;
    }

    public void setAvailable(JsonElement available) {
        this.availableLangs.clear();
        this.hidden = false;
        this.available = stringifyAvailable(available);
        if (available == null || available.isJsonNull()) {
            return;
        }
        if (available.isJsonArray()) {
            readAvailableArray(available.getAsJsonArray());
            return;
        }
        if (!available.isJsonPrimitive()) {
            return;
        }
        readAvailableString(available.getAsString());
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    public void setFallbackName(String fallbackName) {
        this.fallbackName = fallbackName;
    }

    public String getFallbackName() {
        return fallbackName;
    }

    public Map<String, String> getDisplayNames() {
        return displayNames;
    }

    public Map<String, String> getNotices() {
        return notices;
    }

    public Map<String, String> getTips() {
        return tips;
    }

    public boolean isHidden() {
        return hidden;
    }

    public boolean isAvailableFor(String lang) {
        if ((available == null || available.isBlank()) && availableLangs.isEmpty()) {
            return true;
        }
        if (isHidden()) {
            return false;
        }
        if (availableLangs.isEmpty()) {
            return true;
        }
        String current = DlcDcConfigIO.normalizeLangKey(lang);
        return current != null && availableLangs.contains(current);
    }

    public String resolveDisplayName(String lang) {
        return resolveLocalized(displayNames, lang, fallbackName);
    }

    public String resolveNotice(String lang) {
        return resolveLocalized(notices, lang, "");
    }

    public String resolveTip(String lang) {
        return resolveLocalized(tips, lang, "");
    }

    private String resolveLocalized(Map<String, String> values, String lang, String fallback) {
        if (values == null || values.isEmpty()) {
            return fallback == null ? "" : fallback;
        }
        String key = DlcDcConfigIO.normalizeLangKey(lang);
        if (key != null && values.containsKey(key)) {
            return values.get(key);
        }
        if (values.containsKey("en_us")) {
            return values.get("en_us");
        }
        return values.values().iterator().next();
    }

    private void readAvailableArray(JsonArray array) {
        for (JsonElement element : array) {
            if (element == null || !element.isJsonPrimitive()) {
                continue;
            }
            readAvailableString(element.getAsString());
        }
    }

    private void readAvailableString(String value) {
        if (value == null) {
            return;
        }
        for (String token : value.split(",")) {
            String trimmed = token == null ? "" : token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if ("hidden".equalsIgnoreCase(trimmed)) {
                hidden = true;
                availableLangs.clear();
                return;
            }
            String wanted = DlcDcConfigIO.normalizeLangKey(trimmed);
            if (wanted != null) {
                availableLangs.add(wanted);
            }
        }
    }

    private static String stringifyAvailable(JsonElement available) {
        if (available == null || available.isJsonNull()) {
            return null;
        }
        if (available.isJsonPrimitive()) {
            return available.getAsString();
        }
        if (!available.isJsonArray()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (JsonElement element : available.getAsJsonArray()) {
            if (element != null && element.isJsonPrimitive()) {
                values.add(element.getAsString());
            }
        }
        return String.join(",", values);
    }
}
