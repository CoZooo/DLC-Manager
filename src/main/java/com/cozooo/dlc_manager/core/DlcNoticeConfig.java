package com.cozooo.dlc_manager.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

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
            if (lang != null && values.containsKey(lang)) {
                return values.get(lang);
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

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Path path;
    private boolean exists;
    private boolean noticeEnabled = true;
    private boolean neverShow = false;
    private ShowFrequency showFrequency = ShowFrequency.PER_LAUNCH_MANAGER;
    private final LocalizedText title = new LocalizedText();
    private final Subhead subhead = new Subhead();
    private final LocalizedText contents = new LocalizedText();

    public static DlcNoticeConfig load() {
        DlcNoticeConfig config = new DlcNoticeConfig();
        config.path = resolveNoticePath();
        if (config.path == null || !Files.exists(config.path)) {
            ensureNoticeFiles();
            config.path = resolveNoticePath();
        }
        if (config.path == null || !Files.exists(config.path)) {
            config.noticeEnabled = false;
            config.exists = false;
            return config;
        }
        config.exists = true;
        try (Reader reader = Files.newBufferedReader(config.path)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(true);
            JsonObject obj = GSON.fromJson(jsonReader, JsonObject.class);
            if (obj == null) {
                config.noticeEnabled = false;
                return config;
            }
            config.noticeEnabled = getBoolean(obj, "isNoticeEnabled", true);
            config.neverShow = getBoolean(obj, "neverShow", false);
            config.showFrequency = ShowFrequency.fromString(getString(obj, "ShowFrequency"));
            config.title.values.putAll(readLocalized(obj.getAsJsonObject("Title")));
            JsonObject subheadObj = obj.has("Subhead") && obj.get("Subhead").isJsonObject() ? obj.getAsJsonObject("Subhead") : null;
            if (subheadObj != null) {
                config.subhead.enabled = getBoolean(subheadObj, "enabled", true);
                config.subhead.text.values.putAll(readLocalized(subheadObj));
            }
            config.contents.values.putAll(readLocalized(obj.getAsJsonObject("Contents")));
            config.ensureDefaults();
            return config;
        } catch (IOException | JsonSyntaxException ignored) {
            config.noticeEnabled = false;
            return config;
        }
    }

    public void save() {
        if (path == null || !exists) {
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
        obj.add("Contents", writeLocalized(contents));
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(obj, writer);
        } catch (IOException ignored) {
            // Best-effort persistence.
        }
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

    private static Path resolveNoticePath() {
        for (Path root : DlcPathConfig.getDlcRoots()) {
            if (root == null) {
                continue;
            }
            Path noticePath = root.resolve("info").resolve("notice.json");
            if (Files.exists(noticePath)) {
                return noticePath;
            }
        }
        // Fallback to first root if exists, but mark as non-existent.
        if (!DlcPathConfig.getDlcRoots().isEmpty()) {
            return DlcPathConfig.getDlcRoots().get(0).resolve("info").resolve("notice.json");
        }
        return null;
    }

    private static void ensureNoticeFiles() {
        for (Path root : DlcPathConfig.getDlcRoots()) {
            if (root == null || !Files.exists(root)) {
                continue;
            }
            Path noticePath = root.resolve("info").resolve("notice.json");
            if (Files.exists(noticePath)) {
                continue;
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("isNoticeEnabled", false);
            obj.addProperty("neverShow", false);
            obj.addProperty("ShowFrequency", ShowFrequency.PER_LAUNCH_MANAGER.getKey());
            JsonObject title = new JsonObject();
            title.addProperty("en_us", "Title");
            obj.add("Title", title);
            JsonObject subhead = new JsonObject();
            subhead.addProperty("enabled", false);
            subhead.addProperty("en_us", "Subhead");
            obj.add("Subhead", subhead);
            JsonObject contents = new JsonObject();
            contents.addProperty("en_us", "Contents");
            obj.add("Contents", contents);
            try {
                Files.createDirectories(noticePath.getParent());
                try (Writer writer = Files.newBufferedWriter(noticePath)) {
                    GSON.toJson(obj, writer);
                }
            } catch (IOException ignored) {
                // Best-effort.
            }
        }
    }

    private static Map<String, String> readLocalized(JsonObject obj) {
        Map<String, String> values = new HashMap<>();
        if (obj == null) {
            return values;
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                values.put(entry.getKey(), entry.getValue().getAsString());
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
        if (obj == null || !obj.has(key)) {
            return fallback;
        }
        JsonElement element = obj.get(key);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        }
        return fallback;
    }

    private static String getString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) {
            return null;
        }
        JsonElement element = obj.get(key);
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        return null;
    }

    private void ensureDefaults() {
        if (title.values.isEmpty()) {
            title.put("en_us", "Title");
        }
        if (subhead.text.values.isEmpty()) {
            subhead.text.put("en_us", "Subhead");
        }
        if (contents.values.isEmpty()) {
            contents.put("en_us", "Contents");
        }
    }
}
