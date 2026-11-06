package com.cozooo.dlc_manager.client.notice;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DlcNoticeConfig {
    public enum ShowFrequency {
        PER_LAUNCH_GAME("perlaunchgame"),
        PER_GAME_SESSION("pergamesession"),
        PER_LAUNCH_MANAGER("perlaunchmanager"),
        ONCE("once");

        private final String key;

        ShowFrequency(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public static ShowFrequency fromString(String raw) {
            if (raw == null) {
                return PER_LAUNCH_MANAGER;
            }
            for (ShowFrequency value : values()) {
                if (value.key.equalsIgnoreCase(raw)) {
                    return value;
                }
            }
            return PER_LAUNCH_MANAGER;
        }
    }

    public static class LocalizedText {
        private final Map<String, String> values = new HashMap<>();

        public void put(String key, String value) {
            if (key == null || value == null) {
                return;
            }
            values.put(key, value);
        }

        public Map<String, String> getValues() {
            return values;
        }

        public String resolve(String lang) {
            if (values.isEmpty()) {
                return "";
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
    }

    public static class Subhead {
        public boolean enabled = true;
        public final LocalizedText text = new LocalizedText();
    }

    private Path path;
    private boolean exists;
    private boolean noticeEnabled = true;
    private boolean neverShow = false;
    private ShowFrequency showFrequency = ShowFrequency.PER_LAUNCH_MANAGER;
    private final LocalizedText title = new LocalizedText();
    private final Subhead subhead = new Subhead();
    private final LocalizedText contents = new LocalizedText();
    private final LocalizedText contentUrls = new LocalizedText();
    private transient final Set<String> contentUrlLoaded = new HashSet<>();
    private transient final Map<String, String> contentUrlText = new HashMap<>();

    public static DlcNoticeConfig load() {
        return load(resolveNoticePath());
    }

    public static DlcNoticeConfig load(Path path) {
        DlcNoticeConfig config = new DlcNoticeConfig();
        Path resolvedPath = migrateLegacyPath(path);
        config.path = resolvedPath;
        if (resolvedPath == null || !Files.exists(resolvedPath)) {
            config.noticeEnabled = false;
            config.exists = false;
            return config;
        }
        config.exists = true;
        JsonObject obj = DlcDcConfigIO.read(resolvedPath, DlcDcConfigIO.Layout.GENERIC);
        if (obj == null) {
            config.noticeEnabled = false;
            return config;
        }
        config.noticeEnabled = getBoolean(obj, "isNoticeEnabled", true);
        config.neverShow = getBoolean(obj, "neverShow", false);
        config.showFrequency = ShowFrequency.fromString(getString(obj, "ShowFrequency"));
        config.title.values.putAll(readLocalized(DlcDcConfigIO.getObjectIgnoreCase(obj, "Title")));
        JsonObject subheadObj = DlcDcConfigIO.getObjectIgnoreCase(obj, "Subhead");
        if (subheadObj != null) {
            config.subhead.enabled = getBoolean(subheadObj, "enabled", true);
            config.subhead.text.values.putAll(readLocalized(subheadObj));
        }
        config.contentUrls.values.putAll(readLocalizedUrlValues(DlcDcConfigIO.getIgnoreCase(obj, "ContentUrl")));
        config.contents.values.putAll(readLocalized(DlcDcConfigIO.getObjectIgnoreCase(obj, "Contents")));
        return config;
    }

    public void save() {
        if (path == null || !exists || !Files.exists(path)) {
            return;
        }
        JsonObject obj = new JsonObject();
        obj.addProperty("isNoticeEnabled", noticeEnabled);
        obj.addProperty("neverShow", neverShow);
        obj.addProperty("ShowFrequency", showFrequency.getKey());
        obj.add("Title", writeLocalized(title));
        JsonObject subheadObj = new JsonObject();
        subheadObj.addProperty("enabled", subhead.enabled);
        JsonObject subheadText = writeLocalized(subhead.text);
        for (Map.Entry<String, JsonElement> entry : subheadText.entrySet()) {
            subheadObj.add(entry.getKey(), entry.getValue());
        }
        obj.add("Subhead", subheadObj);
        if (!contentUrls.values.isEmpty()) {
            obj.add("ContentUrl", writeLocalized(contentUrls));
        }
        obj.add("Contents", writeLocalized(contents));
        DlcDcConfigIO.write(path, obj, DlcDcConfigIO.Layout.GENERIC);
    }

    public boolean isNoticeEnabled() {
        return noticeEnabled;
    }

    public boolean isNeverShow() {
        return neverShow;
    }

    public void setNeverShow(boolean neverShow) {
        this.neverShow = neverShow;
    }


    public ShowFrequency getShowFrequency() {
        return showFrequency;
    }

    public LocalizedText getTitle() {
        return title;
    }

    public Subhead getSubhead() {
        return subhead;
    }

    public LocalizedText getContents() {
        return contents;
    }

    public String getContentUrl() {
        return getContentUrl(null);
    }

    public String getContentUrl(String lang) {
        return normalizeUrl(contentUrls.resolve(lang));
    }

    public boolean hasContent(String lang) {
        return !contents.resolve(lang).isBlank() || !getContentUrl(lang).isBlank();
    }

    public String resolveContents(String lang) {
        String contentUrl = getContentUrl(lang);
        if (contentUrl != null && !contentUrl.isBlank()) {
            String remote = fetchContentUrlText(contentUrl);
            if (remote != null && !remote.isBlank()) {
                return remote;
            }
        }
        return contents.resolve(lang);
    }

    private static Path resolveNoticePath() {
        Path root = DlcConfig.getDlcRoots().isEmpty() ? null : DlcConfig.getDlcRoots().get(0);
        if (root == null) {
            return null;
        }
        return DlcPaths.resolveNoticeConfigPath(root);
    }

    private static Path migrateLegacyPath(Path path) {
        if (path == null || path.getFileName() == null || !"notice.dc".equalsIgnoreCase(path.getFileName().toString())) {
            return path;
        }
        Path info = path.getParent();
        if (info != null && info.getFileName() != null && "info".equalsIgnoreCase(info.getFileName().toString())) {
            return DlcPaths.resolveNoticeConfigPath(info.getParent());
        }
        return path;
    }

    private static Map<String, String> readLocalized(JsonObject obj) {
        Map<String, String> values = new HashMap<>();
        if (obj == null) {
            return values;
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getValue().isJsonPrimitive() && !"enabled".equalsIgnoreCase(entry.getKey())) {
                values.put(DlcDcConfigIO.normalizeLangKey(entry.getKey()), entry.getValue().getAsString());
            }
        }
        return values;
    }

    private static Map<String, String> readLocalizedUrlValues(JsonElement element) {
        Map<String, String> values = new HashMap<>();
        if (element == null) {
            return values;
        }
        if (element.isJsonPrimitive()) {
            String url = normalizeUrl(element.getAsString());
            if (!url.isBlank()) {
                values.put("en_us", url);
            }
            return values;
        }
        if (!element.isJsonObject()) {
            return values;
        }
        JsonObject obj = element.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                String url = normalizeUrl(entry.getValue().getAsString());
                if (!url.isBlank()) {
                    values.put(DlcDcConfigIO.normalizeLangKey(entry.getKey()), url);
                }
            }
        }
        return values;
    }

    private static JsonObject writeLocalized(LocalizedText text) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, String> entry : text.getValues().entrySet()) {
            obj.addProperty(entry.getKey(), entry.getValue());
        }
        return obj;
    }

    private static boolean getBoolean(JsonObject obj, String key, boolean fallback) {
        return DlcDcConfigIO.getBooleanIgnoreCase(obj, key, fallback);
    }

    private static String getString(JsonObject obj, String key) {
        return DlcDcConfigIO.getStringIgnoreCase(obj, key);
    }

    private synchronized String fetchContentUrlText(String contentUrl) {
        String normalizedUrl = normalizeUrl(contentUrl);
        if (normalizedUrl.isBlank()) {
            return null;
        }
        if (contentUrlLoaded.contains(normalizedUrl)) {
            return contentUrlText.get(normalizedUrl);
        }
        contentUrlLoaded.add(normalizedUrl);
        java.net.HttpURLConnection connection = null;
        try {
            connection = (java.net.HttpURLConnection) java.net.URI.create(normalizedUrl).toURL().openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "DLC-Manager-Notice/1.0");
            int code = connection.getResponseCode();
            if (code >= 200 && code < 300) {
                try (java.io.InputStream in = connection.getInputStream()) {
                    byte[] bytes = in.readAllBytes();
                    String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    if (text.startsWith("\uFEFF")) {
                        text = text.substring(1);
                    }
                    contentUrlText.put(normalizedUrl, text);
                    return text;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        contentUrlText.put(normalizedUrl, null);
        return null;
    }

    private static String normalizeUrl(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim();
    }
}
