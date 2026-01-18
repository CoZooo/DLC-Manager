package com.cozooo.dlc_manager.client.required;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DlcRequiredLoader {
    private static final Gson GSON = new Gson();

    private DlcRequiredLoader() {
    }

    public static List<DlcRequiredEntry> load(Path dlcRoot) {
        List<DlcRequiredEntry> entries = new ArrayList<>();
        if (dlcRoot == null) {
            return entries;
        }
        Path requiredDir = dlcRoot.resolve("required");
        if (!Files.exists(requiredDir)) {
            return entries;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(requiredDir, "*.json")) {
            for (Path path : stream) {
                if (path.getFileName() != null && "notice.json".equalsIgnoreCase(path.getFileName().toString())) {
                    continue;
                }
                DlcRequiredEntry entry = readEntry(path, requiredDir);
                if (entry != null && entry.isInList()) {
                    entries.add(entry);
                }
            }
        } catch (IOException ignored) {
            // Best effort.
        }
        return entries;
    }

    public static DlcRequiredEntry loadSingle(Path configPath, Path dlcRoot) {
        if (configPath == null || dlcRoot == null) {
            return null;
        }
        Path requiredDir = dlcRoot.resolve("required");
        if (!Files.isDirectory(requiredDir)) {
            return null;
        }
        if (!Files.exists(configPath)) {
            return null;
        }
        return readEntry(configPath, requiredDir);
    }

    private static DlcRequiredEntry readEntry(Path path, Path requiredDir) {
        if (path == null) {
            return null;
        }
        JsonObject obj;
        try (Reader reader = Files.newBufferedReader(path)) {
            obj = GSON.fromJson(reader, JsonObject.class);
        } catch (IOException ignored) {
            return null;
        }
        return fromJsonObject(obj, requiredDir, path, null);
    }

    public static DlcRequiredEntry fromJsonObject(JsonObject obj, Path requiredDir, Path configPath, String defaultIdentifier) {
        if (obj == null) {
            return null;
        }
        DlcRequiredEntry entry = new DlcRequiredEntry(configPath, requiredDir);
        if (defaultIdentifier != null && !defaultIdentifier.isBlank()) {
            entry.setIdentifier(defaultIdentifier);
        }
        if (obj.has("identifier") && obj.get("identifier").isJsonPrimitive()) {
            entry.setIdentifier(obj.get("identifier").getAsString());
        }
        if (obj.has("file_name") && obj.get("file_name").isJsonPrimitive()) {
            entry.setRequiredFileName(obj.get("file_name").getAsString());
        }
        List<String> directUrls = getDirectUrls(obj);
        if (!directUrls.isEmpty()) {
            entry.setDirectUrls(directUrls);
        } else if (obj.has("directUrl") && obj.get("directUrl").isJsonPrimitive()) {
            entry.setDirectUrl(obj.get("directUrl").getAsString());
        }
        if (obj.has("modrinth_projectid") && obj.get("modrinth_projectid").isJsonPrimitive()) {
            entry.setModrinthProjectId(obj.get("modrinth_projectid").getAsString());
        }
        if (obj.has("modrinth_versionid") && obj.get("modrinth_versionid").isJsonPrimitive()) {
            entry.setModrinthVersionId(obj.get("modrinth_versionid").getAsString());
        }
        if (obj.has("priority") && obj.get("priority").isJsonPrimitive()) {
            entry.setDownloadPriority(obj.get("priority").getAsString());
        }
        if (obj.has("isInList") && obj.get("isInList").isJsonPrimitive()) {
            entry.setInList(obj.get("isInList").getAsBoolean());
        }
        if (obj.has("isHidden") && obj.get("isHidden").isJsonPrimitive()) {
            entry.setHidden(obj.get("isHidden").getAsBoolean());
        }
        readDisplayNames(obj, entry);
        readTips(obj, entry);
        String applied = null;
        if (obj.has("appliedTarget") && obj.get("appliedTarget").isJsonPrimitive()) {
            applied = obj.get("appliedTarget").getAsString();
        } else if (obj.has("appliedTaget") && obj.get("appliedTaget").isJsonPrimitive()) {
            applied = obj.get("appliedTaget").getAsString();
        }
        entry.setAppliedTargetRaw(applied == null ? "" : applied);
        if (obj.has("dependencies")) {
            parseDependencies(obj.get("dependencies"), entry, requiredDir);
        }
        return entry;
    }

    private static void readDisplayNames(JsonObject obj, DlcRequiredEntry entry) {
        if (obj == null || entry == null) {
            return;
        }
        JsonObject display = null;
        if (obj.has("displayName") && obj.get("displayName").isJsonObject()) {
            display = obj.getAsJsonObject("displayName");
        } else if (obj.has("DisplayName") && obj.get("DisplayName").isJsonObject()) {
            display = obj.getAsJsonObject("DisplayName");
        }
        if (display == null) {
            String fallback = entry.getBaseName();
            if (fallback == null || fallback.isBlank()) {
                fallback = entry.getIdentifier();
            }
            entry.putDisplayName("en_us", fallback);
            return;
        }
        for (var e : display.entrySet()) {
            if (e.getValue() != null && e.getValue().isJsonPrimitive()) {
                entry.putDisplayName(e.getKey(), e.getValue().getAsString());
            }
        }
        if (entry.getDisplayNames().isEmpty()) {
            String fallback = entry.getBaseName();
            if (fallback == null || fallback.isBlank()) {
                fallback = entry.getIdentifier();
            }
            entry.putDisplayName("en_us", fallback);
        }
    }

    private static void readTips(JsonObject obj, DlcRequiredEntry entry) {
        if (obj == null || entry == null) {
            return;
        }
        if (obj.has("tip") && obj.get("tip").isJsonObject()) {
            JsonObject tipObj = obj.getAsJsonObject("tip");
            for (var e : tipObj.entrySet()) {
                if (e.getValue() != null && e.getValue().isJsonPrimitive()) {
                    entry.putTip(e.getKey(), e.getValue().getAsString());
                }
            }
        }
    }

    private static void parseDependencies(JsonElement element, DlcRequiredEntry entry, Path requiredDir) {
        if (element == null || entry == null) {
            return;
        }
        if (element.isJsonPrimitive()) {
            String raw = element.getAsString();
            if (raw != null) {
                for (String part : raw.split(",")) {
                    String id = part == null ? "" : part.trim();
                    if (!id.isBlank()) {
                        entry.addDependency(new DlcRequiredDependency(id, null));
                    }
                }
            }
            return;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement value : array) {
                parseDependencyValue(null, value, entry, requiredDir);
            }
            return;
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            for (var e : obj.entrySet()) {
                parseDependencyValue(e.getKey(), e.getValue(), entry, requiredDir);
            }
        }
    }

    private static void parseDependencyValue(String key, JsonElement value, DlcRequiredEntry owner, Path requiredDir) {
        if (value == null || owner == null) {
            return;
        }
        if (value.isJsonPrimitive()) {
            String raw = value.getAsString();
            if ((raw == null || raw.isBlank()) && key != null) {
                raw = key;
            }
            if (raw != null) {
                for (String part : raw.split(",")) {
                    String id = part == null ? "" : part.trim();
                    if (!id.isBlank()) {
                        owner.addDependency(new DlcRequiredDependency(id, null));
                    }
                }
            }
            return;
        }
        if (!value.isJsonObject()) {
            return;
        }
        JsonObject obj = value.getAsJsonObject();
        String identifier = key;
        if (obj.has("identifier") && obj.get("identifier").isJsonPrimitive()) {
            identifier = obj.get("identifier").getAsString();
        }
        DlcRequiredEntry depEntry = fromJsonObject(obj, requiredDir, null, identifier);
        if (depEntry != null && !depEntry.isInList()) {
            depEntry = null;
        }
        String depId = depEntry == null ? null : depEntry.getIdentifier();
        if (depId == null || depId.isBlank()) {
            depId = identifier;
        }
        owner.addDependency(new DlcRequiredDependency(depId, depEntry));
    }

    private static List<String> getDirectUrls(JsonObject obj) {
        if (obj == null || !obj.has("directUrl")) {
            return List.of();
        }
        JsonElement element = obj.get("directUrl");
        if (element == null) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        if (element.isJsonPrimitive()) {
            String url = element.getAsString();
            if (url != null && !url.isBlank()) {
                urls.add(url.trim());
            }
            return urls;
        }
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                if (item != null && item.isJsonPrimitive()) {
                    String url = item.getAsString();
                    if (url != null && !url.isBlank()) {
                        urls.add(url.trim());
                    }
                }
            }
        }
        return urls;
    }
}
