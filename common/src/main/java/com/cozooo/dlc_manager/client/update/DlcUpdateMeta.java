package com.cozooo.dlc_manager.client.update;

import com.cozooo.dlc_manager.core.DcConfigIO;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public class DlcUpdateMeta {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String dlcOld;
    private String dlcNew;
    private String baseOld;
    private String baseNew;
    private String timestamp;
    private boolean hidden;
    private final Map<String, String> changelog = new LinkedHashMap<>();
    private final List<String> restorePaths = new ArrayList<>();
    private final List<String> createdPaths = new ArrayList<>();

    public static DlcUpdateMeta load(Path metaPath) {
        if (metaPath == null || !Files.exists(metaPath)) {
            return null;
        }
        JsonObject obj = DcConfigIO.read(metaPath, DcConfigIO.Layout.GENERIC);
        if (obj == null) {
            return null;
        }
        DlcUpdateMeta meta = new DlcUpdateMeta();
        meta.dlcOld = DcConfigIO.getStringIgnoreCase(obj, "dlc_old");
        meta.dlcNew = DcConfigIO.getStringIgnoreCase(obj, "dlc_new");
        meta.baseOld = DcConfigIO.getStringIgnoreCase(obj, "base_old");
        meta.baseNew = DcConfigIO.getStringIgnoreCase(obj, "base_new");
        meta.timestamp = DcConfigIO.getStringIgnoreCase(obj, "timestamp");
        meta.hidden = DcConfigIO.getBooleanIgnoreCase(obj, "hidden", false);
        JsonObject logObj = DcConfigIO.getObjectIgnoreCase(obj, "changelog");
        if (logObj != null) {
            for (var entry : logObj.entrySet()) {
                if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) {
                    String raw = entry.getValue().getAsString();
                    if (raw != null && !raw.isBlank()) {
                        meta.changelog.put(DcConfigIO.normalizeLangKey(entry.getKey()), raw);
                    }
                }
            }
        }
        var restoreArray = DcConfigIO.getArrayIgnoreCase(obj, "restore");
        if (restoreArray != null) {
            restoreArray.forEach(el -> {
                if (el != null && el.isJsonPrimitive()) {
                    meta.restorePaths.add(el.getAsString());
                }
            });
        }
        var createdArray = DcConfigIO.getArrayIgnoreCase(obj, "created");
        if (createdArray != null) {
            createdArray.forEach(el -> {
                if (el != null && el.isJsonPrimitive()) {
                    meta.createdPaths.add(el.getAsString());
                }
            });
        }
        return meta;
    }

    public void save(Path metaPath) {
        if (metaPath == null) {
            return;
        }
        try {
            Files.createDirectories(metaPath.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("dlc_old", dlcOld == null ? "" : dlcOld);
            obj.addProperty("dlc_new", dlcNew == null ? "" : dlcNew);
            if (baseOld != null && !baseOld.isBlank()) {
                obj.addProperty("base_old", baseOld);
            }
            if (baseNew != null && !baseNew.isBlank()) {
                obj.addProperty("base_new", baseNew);
            }
            obj.addProperty("timestamp", timestamp == null ? "" : timestamp);
            obj.addProperty("hidden", hidden);
            if (!changelog.isEmpty()) {
                JsonObject logObj = new JsonObject();
                for (Map.Entry<String, String> entry : changelog.entrySet()) {
                    logObj.addProperty(entry.getKey(), entry.getValue());
                }
                obj.add("changelog", logObj);
            }
            com.google.gson.JsonArray restore = new com.google.gson.JsonArray();
            for (String path : restorePaths) {
                restore.add(path);
            }
            com.google.gson.JsonArray created = new com.google.gson.JsonArray();
            for (String path : createdPaths) {
                created.add(path);
            }
            obj.add("restore", restore);
            obj.add("created", created);
            DcConfigIO.write(metaPath, obj, DcConfigIO.Layout.GENERIC);
        } catch (IOException ignored) {
        }
    }

    public String getDlcOld() {
        return dlcOld;
    }

    public void setDlcOld(String dlcOld) {
        this.dlcOld = dlcOld;
    }

    public String getDlcNew() {
        return dlcNew;
    }

    public void setDlcNew(String dlcNew) {
        this.dlcNew = dlcNew;
    }

    public String getBaseOld() {
        return baseOld;
    }

    public void setBaseOld(String baseOld) {
        this.baseOld = baseOld;
    }

    public String getBaseNew() {
        return baseNew;
    }

    public void setBaseNew(String baseNew) {
        this.baseNew = baseNew;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public Map<String, String> getChangelog() {
        return changelog;
    }

    public void setChangelog(Map<String, String> values) {
        changelog.clear();
        if (values == null) {
            return;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isBlank()) {
                changelog.put(DcConfigIO.normalizeLangKey(entry.getKey()), entry.getValue());
            }
        }
    }

    public String resolveChangelog(String lang) {
        if (changelog.isEmpty()) {
            return "";
        }
        String key = DcConfigIO.normalizeLangKey(lang);
        if (key != null && changelog.containsKey(key)) {
            return changelog.get(key);
        }
        if (changelog.containsKey("en_us")) {
            return changelog.get("en_us");
        }
        return changelog.values().stream().findFirst().orElse("");
    }

    public List<String> getRestorePaths() {
        return restorePaths;
    }

    public List<String> getCreatedPaths() {
        return createdPaths;
    }
}
