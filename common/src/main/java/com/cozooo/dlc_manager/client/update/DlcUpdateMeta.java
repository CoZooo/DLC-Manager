package com.cozooo.dlc_manager.client.update;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DlcUpdateMeta {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String dlcOld;
    private String dlcNew;
    private String timestamp;
    private boolean hidden;
    private final List<String> restorePaths = new ArrayList<>();
    private final List<String> createdPaths = new ArrayList<>();

    public static DlcUpdateMeta load(Path metaPath) {
        if (metaPath == null || !Files.exists(metaPath)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(metaPath)) {
            JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            if (obj == null) {
                return null;
            }
            DlcUpdateMeta meta = new DlcUpdateMeta();
            if (obj.has("dlc_old") && obj.get("dlc_old").isJsonPrimitive()) {
                meta.dlcOld = obj.get("dlc_old").getAsString();
            }
            if (obj.has("dlc_new") && obj.get("dlc_new").isJsonPrimitive()) {
                meta.dlcNew = obj.get("dlc_new").getAsString();
            }
            if (obj.has("timestamp") && obj.get("timestamp").isJsonPrimitive()) {
                meta.timestamp = obj.get("timestamp").getAsString();
            }
            if (obj.has("hidden") && obj.get("hidden").isJsonPrimitive()) {
                meta.hidden = obj.get("hidden").getAsBoolean();
            }
            if (obj.has("restore") && obj.get("restore").isJsonArray()) {
                obj.getAsJsonArray("restore").forEach(el -> {
                    if (el != null && el.isJsonPrimitive()) {
                        meta.restorePaths.add(el.getAsString());
                    }
                });
            }
            if (obj.has("created") && obj.get("created").isJsonArray()) {
                obj.getAsJsonArray("created").forEach(el -> {
                    if (el != null && el.isJsonPrimitive()) {
                        meta.createdPaths.add(el.getAsString());
                    }
                });
            }
            return meta;
        } catch (IOException ignored) {
            return null;
        }
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
            obj.addProperty("timestamp", timestamp == null ? "" : timestamp);
            obj.addProperty("hidden", hidden);
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
            try (Writer writer = Files.newBufferedWriter(metaPath)) {
                GSON.toJson(obj, writer);
            }
        } catch (IOException ignored) {
            // Best-effort save.
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

    public List<String> getRestorePaths() {
        return restorePaths;
    }

    public List<String> getCreatedPaths() {
        return createdPaths;
    }
}
