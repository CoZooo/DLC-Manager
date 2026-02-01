package com.cozooo.dlc_manager.client.remote;

import com.cozooo.dlc_manager.core.JsonCaseUtil;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class DlcRedirectEntry {
    private Path configPath;
    private String available;
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

    public void setAvailable(String available) {
        this.available = available;
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
        return available != null && "hidden".equalsIgnoreCase(available.trim());
    }

    public boolean isAvailableFor(String lang) {
        if (available == null || available.isBlank()) {
            return true;
        }
        if (isHidden()) {
            return false;
        }
        String wanted = JsonCaseUtil.normalizeLangKey(available);
        if (wanted == null) {
            return true;
        }
        String current = JsonCaseUtil.normalizeLangKey(lang);
        return wanted.equals(current);
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
        String key = JsonCaseUtil.normalizeLangKey(lang);
        if (key != null && values.containsKey(key)) {
            return values.get(key);
        }
        if (values.containsKey("en_us")) {
            return values.get("en_us");
        }
        return values.values().iterator().next();
    }
}
