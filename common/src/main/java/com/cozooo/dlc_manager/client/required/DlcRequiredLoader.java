package com.cozooo.dlc_manager.client.required;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DlcRequiredLoader {
    private static final Gson GSON = new Gson();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

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
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(true);
            obj = GSON.fromJson(jsonReader, JsonObject.class);
        } catch (IOException ignored) {
            return null;
        }
        NormalizedRequired normalized = normalizeRequiredObject(obj, requiredDir, path, null);
        if (normalized == null) {
            return null;
        }
        try (Writer writer = Files.newBufferedWriter(path)) {
            PRETTY_GSON.toJson(normalized.ordered, writer);
        } catch (IOException ignored) {
            // Best-effort.
        }
        return normalized.entry;
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
        if (obj.has("curseforge_projectid") && obj.get("curseforge_projectid").isJsonPrimitive()) {
            entry.setCurseforgeProjectId(obj.get("curseforge_projectid").getAsString());
        }
        if (obj.has("curseforge_fileid") && obj.get("curseforge_fileid").isJsonPrimitive()) {
            entry.setCurseforgeFileId(obj.get("curseforge_fileid").getAsString());
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
        if (obj.has("UserRemoved") && obj.get("UserRemoved").isJsonPrimitive()) {
            entry.setUserRemoved(obj.get("UserRemoved").getAsBoolean());
        } else if (obj.has("userRemoved") && obj.get("userRemoved").isJsonPrimitive()) {
            entry.setUserRemoved(obj.get("userRemoved").getAsBoolean());
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

    private static NormalizedRequired normalizeRequiredObject(JsonObject obj, Path requiredDir, Path configPath, String defaultIdentifier) {
        if (obj == null) {
            return null;
        }
        DlcRequiredEntry entry = fromJsonObject(obj, requiredDir, configPath, defaultIdentifier);
        if (entry == null) {
            return null;
        }
        String curseProjectId = entry.getCurseforgeProjectId();
        String curseFileId = entry.getCurseforgeFileId();
        String modrinthProjectId = entry.getModrinthProjectId();
        String modrinthVersionId = entry.getModrinthVersionId();
        List<String> directUrls = entry.getDirectUrls();
        String directUrl = directUrls.isEmpty() ? "" : directUrls.get(0);
        String priority = entry.getDownloadPriority();
        String identifier = entry.getIdentifier();
        String fileName = entry.getRequiredFileName();
        String appliedTarget = entry.getAppliedTargetRaw();
        DependencyResult deps = normalizeDependencies(obj.get("dependencies"), requiredDir);
        JsonObject display = new JsonObject();
        entry.getDisplayNames().forEach(display::addProperty);
        JsonObject tip = new JsonObject();
        entry.getTips().forEach(tip::addProperty);

        JsonObject ordered = new JsonObject();
        ordered.addProperty("curseforge_projectid", curseProjectId == null ? "" : curseProjectId);
        ordered.addProperty("curseforge_fileid", curseFileId == null ? "" : curseFileId);
        ordered.addProperty("modrinth_projectid", modrinthProjectId == null ? "" : modrinthProjectId);
        ordered.addProperty("modrinth_versionid", modrinthVersionId == null ? "" : modrinthVersionId);
        if (directUrls.size() > 1) {
            JsonArray urls = new JsonArray();
            for (String url : directUrls) {
                urls.add(url);
            }
            ordered.add("directUrl", urls);
        } else {
            ordered.addProperty("directUrl", directUrl == null ? "" : directUrl);
        }
        ordered.addProperty("priority", priority == null || priority.isBlank() ? "curseforge" : priority);
        ordered.addProperty("identifier", identifier == null ? "" : identifier);
        if (fileName != null && !fileName.isBlank()) {
            ordered.addProperty("file_name", fileName);
        }
        ordered.addProperty("appliedTarget", appliedTarget == null ? "" : appliedTarget);
        ordered.addProperty("isInList", entry.isInList());
        ordered.addProperty("isHidden", entry.isHidden());
        if (entry.isUserRemoved()) {
            ordered.addProperty("UserRemoved", true);
        }
        ordered.add("dependencies", deps.element == null ? new JsonPrimitive("") : deps.element);
        ordered.add("DisplayName", display);
        ordered.add("tip", tip);

        for (var e : obj.entrySet()) {
            String key = e.getKey();
            if (key == null || ordered.has(key)) {
                continue;
            }
            if ("displayName".equalsIgnoreCase(key) || "DisplayName".equalsIgnoreCase(key)
                || "tip".equalsIgnoreCase(key) || "appliedTaget".equalsIgnoreCase(key)
                || "userRemoved".equalsIgnoreCase(key)) {
                continue;
            }
            ordered.add(key, e.getValue());
        }

        NormalizedRequired normalized = new NormalizedRequired();
        normalized.entry = entry;
        normalized.ordered = ordered;
        return normalized;
    }

    private static final class NormalizedRequired {
        private DlcRequiredEntry entry;
        private JsonObject ordered;
    }

    private static DependencyResult normalizeDependencies(JsonElement element, Path requiredDir) {
        DependencyResult result = new DependencyResult();
        if (element == null || element.isJsonNull()) {
            result.element = new JsonPrimitive("");
            return result;
        }
        if (element.isJsonPrimitive()) {
            String raw = element.getAsString();
            result.element = new JsonPrimitive(raw);
            result.names.addAll(splitNames(raw));
            return result;
        }
        if (element.isJsonArray()) {
            JsonArray array = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                if (child == null || child.isJsonNull()) {
                    continue;
                }
                if (child.isJsonPrimitive()) {
                    String raw = child.getAsString();
                    array.add(new JsonPrimitive(raw));
                    result.names.addAll(splitNames(raw));
                    continue;
                }
                if (child.isJsonObject()) {
                    JsonObject obj = child.getAsJsonObject();
                    if (isIdentifierOnlyObject(obj)) {
                        String identifier = resolveIdentifier(obj, null);
                        JsonObject ordered = new JsonObject();
                        ordered.addProperty("identifier", identifier == null ? "" : identifier);
                        array.add(ordered);
                        if (identifier != null && !identifier.isBlank()) {
                            result.names.add(identifier);
                        }
                        continue;
                    }
                    if (isRequiredConfigObject(obj)) {
                        NormalizedRequired normalized = normalizeRequiredObject(obj, requiredDir, null, null);
                        if (normalized != null) {
                            array.add(normalized.ordered);
                            result.entries.add(normalized.entry);
                            String identifier = normalized.entry.getIdentifier();
                            if (identifier != null && !identifier.isBlank()) {
                                result.names.add(identifier);
                            }
                        }
                        continue;
                    }
                }
                DependencyResult childResult = normalizeDependencies(child, requiredDir);
                array.add(childResult.element == null ? new JsonPrimitive("") : childResult.element);
                result.names.addAll(childResult.names);
                result.entries.addAll(childResult.entries);
            }
            result.element = array;
            return result;
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (isIdentifierOnlyObject(obj)) {
                String identifier = resolveIdentifier(obj, null);
                JsonObject ordered = new JsonObject();
                ordered.addProperty("identifier", identifier == null ? "" : identifier);
                result.element = ordered;
                if (identifier != null && !identifier.isBlank()) {
                    result.names.add(identifier);
                }
                return result;
            }
            if (isRequiredConfigObject(obj)) {
                NormalizedRequired normalized = normalizeRequiredObject(obj, requiredDir, null, null);
                if (normalized != null) {
                    result.element = normalized.ordered;
                    result.entries.add(normalized.entry);
                    String identifier = normalized.entry.getIdentifier();
                    if (identifier != null && !identifier.isBlank()) {
                        result.names.add(identifier);
                    }
                } else {
                    result.element = new JsonPrimitive("");
                }
                return result;
            }
            JsonObject ordered = new JsonObject();
            for (var entry : obj.entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                if (value == null || value.isJsonNull()) {
                    continue;
                }
                if (value.isJsonPrimitive()) {
                    if (value.getAsJsonPrimitive().isString()) {
                        String raw = value.getAsString();
                        if (raw.isBlank()) {
                            continue;
                        }
                        JsonObject childOrdered = new JsonObject();
                        childOrdered.addProperty("identifier", raw);
                        ordered.add(key, childOrdered);
                        result.names.add(raw);
                    } else {
                        ordered.add(key, value);
                    }
                    continue;
                }
                if (value.isJsonObject()) {
                    JsonObject childObj = value.getAsJsonObject();
                    if (isIdentifierOnlyObject(childObj)) {
                        String identifier = resolveIdentifier(childObj, key);
                        JsonObject childOrdered = new JsonObject();
                        childOrdered.addProperty("identifier", identifier == null ? "" : identifier);
                        ordered.add(key, childOrdered);
                        if (identifier != null && !identifier.isBlank()) {
                            result.names.add(identifier);
                        }
                        continue;
                    }
                    if (isRequiredConfigObject(childObj)) {
                        NormalizedRequired normalized = normalizeRequiredObject(childObj, requiredDir, null, key);
                        if (normalized != null) {
                            ordered.add(key, normalized.ordered);
                            result.entries.add(normalized.entry);
                            String identifier = normalized.entry.getIdentifier();
                            if (identifier != null && !identifier.isBlank()) {
                                result.names.add(identifier);
                            }
                        }
                        continue;
                    }
                }
                DependencyResult childResult = normalizeDependencies(value, requiredDir);
                ordered.add(key, childResult.element == null ? new JsonPrimitive("") : childResult.element);
                result.names.addAll(childResult.names);
                result.entries.addAll(childResult.entries);
            }
            result.element = ordered;
            return result;
        }
        result.element = new JsonPrimitive("");
        return result;
    }

    private static boolean isRequiredConfigObject(JsonObject obj) {
        if (obj == null) {
            return false;
        }
        for (String key : obj.keySet()) {
            if (key == null) {
                continue;
            }
            String lowered = key.toLowerCase();
            if ("modrinth_projectid".equals(lowered)
                || "modrinth_versionid".equals(lowered)
                || "directurl".equals(lowered)
                || "curseforge_projectid".equals(lowered)
                || "curseforge_fileid".equals(lowered)
                || "priority".equals(lowered)
                || "identifier".equals(lowered)
                || "file_name".equals(lowered)
                || "appliedtarget".equals(lowered)
                || "appliedtaget".equals(lowered)
                || "displayname".equals(lowered)
                || "tip".equals(lowered)
                || "isinlist".equals(lowered)
                || "ishidden".equals(lowered)
                || "dependencies".equals(lowered)
                || "userremoved".equals(lowered)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIdentifierOnlyObject(JsonObject obj) {
        if (obj == null || obj.size() != 1) {
            return false;
        }
        return obj.has("identifier") && obj.get("identifier").isJsonPrimitive();
    }

    private static String resolveIdentifier(JsonObject obj, String fallback) {
        if (obj != null && obj.has("identifier") && obj.get("identifier").isJsonPrimitive()) {
            String value = obj.get("identifier").getAsString();
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    private static List<String> splitNames(String raw) {
        List<String> names = new ArrayList<>();
        if (raw == null) {
            return names;
        }
        for (String part : raw.split(",")) {
            String value = part == null ? "" : part.trim();
            if (!value.isBlank()) {
                names.add(value);
            }
        }
        return names;
    }

    private static final class DependencyResult {
        private JsonElement element;
        private final List<String> names = new ArrayList<>();
        private final List<DlcRequiredEntry> entries = new ArrayList<>();
    }
}
