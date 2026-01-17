package com.cozooo.dlc_manager.client.remote;

import com.cozooo.dlc_manager.core.DlcTooltipLoader;
import com.cozooo.dlc_manager.core.DlcType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class DlcRemoteLoader {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    private DlcRemoteLoader() {
    }

    public static List<DlcRemoteEntry> load(Path dlcRoot) {
        if (dlcRoot == null) {
            return List.of();
        }
        Path remoteRoot = dlcRoot.resolve("info").resolve("remote");
        if (!Files.isDirectory(remoteRoot)) {
            return List.of();
        }
        List<DlcRemoteEntry> entries = new ArrayList<>();
        try (var stream = Files.walk(remoteRoot)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                .forEach(path -> {
                    DlcRemoteEntry entry = readEntry(path, dlcRoot, remoteRoot);
                    if (entry != null) {
                        entries.add(entry);
                    }
                });
        } catch (IOException ignored) {
            // Best-effort.
        }
        linkIdentifierDependencies(entries);
        return entries;
    }

    private static void linkIdentifierDependencies(List<DlcRemoteEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        Map<String, DlcRemoteEntry> index = new HashMap<>();
        for (DlcRemoteEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            String identifier = entry.getDlcIdentifier();
            if (identifier == null || identifier.isBlank()) {
                continue;
            }
            index.putIfAbsent(identifier, entry);
        }
        for (DlcRemoteEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            List<DlcRemoteEntry> linked = new ArrayList<>(entry.getRemoteDependencies());
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (DlcRemoteEntry dep : linked) {
                if (dep != null && dep.getDlcIdentifier() != null) {
                    seen.add(dep.getDlcIdentifier());
                }
            }
            for (String depId : entry.getDependencyNames()) {
                if (depId == null || depId.isBlank()) {
                    continue;
                }
                if (seen.contains(depId)) {
                    continue;
                }
                DlcRemoteEntry depEntry = index.get(depId);
                if (depEntry != null && depEntry != entry) {
                    linked.add(depEntry);
                    seen.add(depId);
                }
            }
            entry.setRemoteDependencies(linked);
        }
    }

    private static DlcRemoteEntry readEntry(Path path, Path dlcRoot, Path remoteRoot) {
        JsonObject obj;
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(true);
            obj = GSON.fromJson(jsonReader, JsonObject.class);
        } catch (IOException | JsonSyntaxException ignored) {
            return null;
        }
        if (obj == null) {
            return null;
        }
        String fileName = stripJson(path.getFileName().toString());
        Path relative = remoteRoot.relativize(path);
        Path baseDir = dlcRoot.resolve(relative.getParent() == null ? "" : relative.getParent().toString());

        NormalizedRemote normalized = normalizeRemoteObject(obj, fileName, dlcRoot, baseDir);
        if (normalized == null) {
            return null;
        }
        DlcRemoteEntry entry = normalized.entry;
        entry.setConfigPath(path);
        if (entry.getFileNameHint() == null || entry.getFileNameHint().isBlank()) {
            entry.setFileNameHint(fileName);
        }

        try (Writer writer = Files.newBufferedWriter(path)) {
            PRETTY_GSON.toJson(normalized.ordered, writer);
        } catch (IOException ignored) {
            // Best-effort.
        }
        return entry;
    }

    private static NormalizedRemote normalizeRemoteObject(JsonObject obj, String fallbackName, Path dlcRoot, Path baseDir) {
        if (obj == null) {
            return null;
        }
        String directUrl = getString(obj, "directUrl");
        String projectId = getString(obj, "modrinth_projectid");
        String versionId = getString(obj, "modrinth_versionid");
        String identifier = getString(obj, "dlc_identifier");
        String fileName = getString(obj, "file_name");
        String nameFallback = fallbackName;
        if (nameFallback == null || nameFallback.isBlank()) {
            if (directUrl != null && !directUrl.isBlank()) {
                nameFallback = extractFileName(directUrl);
            } else if (projectId != null && !projectId.isBlank()) {
                nameFallback = projectId;
            }
        }
        if (fileName == null || fileName.isBlank()) {
            if (directUrl != null && !directUrl.isBlank()) {
                fileName = extractFileName(directUrl);
            }
            if (fileName == null || fileName.isBlank()) {
                fileName = nameFallback;
            }
        }
        JsonObject displayObj = sanitizeLocalized(obj.getAsJsonObject("DisplayName"));
        if (displayObj.entrySet().isEmpty()) {
            displayObj = new JsonObject();
            displayObj.addProperty("en_us", nameFallback == null ? "" : nameFallback);
        }
        if (identifier == null || identifier.isBlank()) {
            identifier = nameFallback == null ? "" : nameFallback;
        }
        JsonObject tipObj = obj.has("tip") && obj.get("tip").isJsonObject() ? obj.getAsJsonObject("tip") : new JsonObject();
        String typeRaw = getString(obj, "type");
        DlcType type = DlcTooltipLoader.parseTypeString(typeRaw);
        if (type == null) {
            type = DlcType.MOD;
        }
        boolean legacyShown = getBoolean(obj, "isShownInList", true);
        boolean inList = obj.has("isInList") ? getBoolean(obj, "isInList", true) : legacyShown;
        boolean hidden = getBoolean(obj, "isHidden", false);
        boolean allowEnableByAll = getBoolean(obj, "AllowEnableByAll", true);

        DependencyResult deps = normalizeDependencies(obj.get("dependencies"), dlcRoot, baseDir);
        DependencyResult conflicts = normalizeConflicts(obj.get("conflicts"));

        JsonObject ordered = new JsonObject();
        ordered.addProperty("directUrl", directUrl == null ? "" : directUrl);
        ordered.addProperty("modrinth_projectid", projectId == null ? "" : projectId);
        ordered.addProperty("modrinth_versionid", versionId == null ? "" : versionId);
        ordered.addProperty("dlc_identifier", identifier);
        ordered.addProperty("file_name", fileName == null ? "" : fileName);
        ordered.addProperty("type", DlcTooltipLoader.toTypeString(type));
        ordered.addProperty("isInList", inList);
        ordered.addProperty("isHidden", hidden);
        ordered.addProperty("AllowEnableByAll", allowEnableByAll);
        ordered.add("dependencies", deps.element);
        ordered.add("conflicts", conflicts.element);
        ordered.add("DisplayName", displayObj);
        ordered.add("tip", tipObj);

        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey();
            if (key == null || ordered.has(key)) {
                continue;
            }
            if ("platform".equalsIgnoreCase(key) || "projectid".equalsIgnoreCase(key) || "version".equalsIgnoreCase(key)
                || "modid".equalsIgnoreCase(key) || "dlc_name".equalsIgnoreCase(key) || "dlc_identifier".equalsIgnoreCase(key)
                || "isShownInList".equalsIgnoreCase(key)) {
                continue;
            }
            ordered.add(key, entry.getValue());
        }

        DlcRemoteEntry entry = new DlcRemoteEntry(null, dlcRoot, baseDir, nameFallback);
        entry.setDirectUrl(ordered.get("directUrl").getAsString());
        entry.setModrinthProjectId(ordered.get("modrinth_projectid").getAsString());
        entry.setModrinthVersionId(ordered.get("modrinth_versionid").getAsString());
        entry.setDlcIdentifier(ordered.get("dlc_identifier").getAsString());
        String resolvedFileName = fileName;
        if (resolvedFileName == null || resolvedFileName.isBlank()) {
            resolvedFileName = nameFallback;
        }
        entry.setFileNameHint(resolvedFileName);
        entry.setType(type);
        entry.setInList(inList);
        entry.setHidden(hidden);
        entry.setAllowEnableByAll(allowEnableByAll);
        entry.setDependencyNames(deps.names);
        entry.setConflictNames(conflicts.names);
        entry.setRemoteDependencies(deps.entries);
        entry.setDisplayNames(readLocalized(displayObj));
        entry.setTips(readLocalized(tipObj));

        return new NormalizedRemote(entry, ordered);
    }

    private static DependencyResult normalizeDependencies(JsonElement element, Path dlcRoot, Path baseDir) {
        DependencyResult result = new DependencyResult();
        if (element == null || element.isJsonNull()) {
            result.element = new com.google.gson.JsonPrimitive("");
            return result;
        }
        if (element.isJsonPrimitive()) {
            String raw = element.getAsString();
            result.element = new com.google.gson.JsonPrimitive(raw);
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
                    array.add(new com.google.gson.JsonPrimitive(raw));
                    result.names.addAll(splitNames(raw));
                    continue;
                }
                DependencyResult childResult = normalizeDependencies(child, dlcRoot, baseDir);
                array.add(childResult.element);
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
                ordered.addProperty("dlc_identifier", identifier == null ? "" : identifier);
                result.element = ordered;
                if (identifier != null && !identifier.isBlank()) {
                    result.names.add(identifier);
                }
                return result;
            }
            if (isRemoteConfigObject(obj)) {
                NormalizedRemote normalized = normalizeRemoteObject(obj, null, dlcRoot, baseDir);
                if (normalized != null) {
                    result.element = normalized.ordered;
                    result.entries.add(normalized.entry);
                    String identifier = normalized.entry.getDlcIdentifier();
                    if (identifier != null && !identifier.isBlank()) {
                        result.names.add(identifier);
                    }
                } else {
                    result.element = new com.google.gson.JsonPrimitive("");
                }
                return result;
            }
            JsonObject ordered = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
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
                        childOrdered.addProperty("dlc_identifier", raw);
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
                        childOrdered.addProperty("dlc_identifier", identifier == null ? "" : identifier);
                        ordered.add(key, childOrdered);
                        if (identifier != null && !identifier.isBlank()) {
                            result.names.add(identifier);
                        }
                        continue;
                    }
                    NormalizedRemote normalized = normalizeRemoteObject(childObj, key, dlcRoot, baseDir);
                    if (normalized != null) {
                        ordered.add(key, normalized.ordered);
                        result.entries.add(normalized.entry);
                        String identifier = normalized.entry.getDlcIdentifier();
                        if (identifier != null && !identifier.isBlank()) {
                            result.names.add(identifier);
                        }
                    } else {
                        ordered.add(key, value);
                    }
                } else if (value.isJsonArray()) {
                    DependencyResult childResult = normalizeDependencies(value, dlcRoot, baseDir);
                    ordered.add(key, childResult.element);
                    result.names.addAll(childResult.names);
                    result.entries.addAll(childResult.entries);
                } else {
                    ordered.add(key, value);
                }
            }
            result.element = ordered;
            return result;
        }
        result.element = new com.google.gson.JsonPrimitive("");
        return result;
    }

    private static DependencyResult normalizeConflicts(JsonElement element) {
        DependencyResult result = new DependencyResult();
        if (element == null || element.isJsonNull()) {
            result.element = new com.google.gson.JsonPrimitive("");
            return result;
        }
        if (element.isJsonPrimitive()) {
            String raw = element.getAsString();
            result.element = new com.google.gson.JsonPrimitive(raw);
            result.names.addAll(splitNames(raw));
            return result;
        }
        if (element.isJsonArray()) {
            JsonArray array = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                DependencyResult childResult = normalizeConflicts(child);
                array.add(childResult.element);
                result.names.addAll(childResult.names);
            }
            result.element = array;
            return result;
        }
        if (element.isJsonObject()) {
            String name = getString(element.getAsJsonObject(), "dlc_identifier");
            if (name == null || name.isBlank()) {
                name = getString(element.getAsJsonObject(), "dlc_name");
            }
            if (name == null || name.isBlank()) {
                name = getString(element.getAsJsonObject(), "name");
            }
            result.element = new com.google.gson.JsonPrimitive(name == null ? "" : name);
            result.names.addAll(splitNames(name));
            return result;
        }
        result.element = new com.google.gson.JsonPrimitive("");
        return result;
    }

    private static List<String> splitNames(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String part : raw.split("[\\n\\r,]+")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                names.add(trimmed);
            }
        }
        return names;
    }

    private static String stripJson(String name) {
        if (name == null) {
            return "";
        }
        if (name.endsWith(".json")) {
            return name.substring(0, name.length() - 5);
        }
        return name;
    }

    private static String extractFileName(String url) {
        if (url == null) {
            return null;
        }
        int idx = url.lastIndexOf('/');
        if (idx >= 0 && idx + 1 < url.length()) {
            return url.substring(idx + 1);
        }
        return url;
    }

    private static boolean isIdentifierOnlyObject(JsonObject obj) {
        if (obj == null) {
            return false;
        }
        boolean hasIdentifier = obj.has("dlc_identifier") || obj.has("dlc_name") || obj.has("name");
        if (!hasIdentifier) {
            return false;
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey();
            if ("dlc_identifier".equalsIgnoreCase(key) || "dlc_name".equalsIgnoreCase(key) || "name".equalsIgnoreCase(key)) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static String resolveIdentifier(JsonObject obj, String fallback) {
        if (obj == null) {
            return fallback;
        }
        String identifier = getString(obj, "dlc_identifier");
        if (identifier == null || identifier.isBlank()) {
            identifier = getString(obj, "dlc_name");
        }
        if (identifier == null || identifier.isBlank()) {
            identifier = getString(obj, "name");
        }
        if (identifier == null || identifier.isBlank()) {
            identifier = fallback;
        }
        return identifier;
    }

    private static boolean isRemoteConfigObject(JsonObject obj) {
        if (obj == null) {
            return false;
        }
        return obj.has("directUrl")
            || obj.has("modrinth_projectid")
            || obj.has("modrinth_versionid")
            || obj.has("dlc_identifier")
            || obj.has("file_name")
            || obj.has("type")
            || obj.has("DisplayName")
            || obj.has("tip")
            || obj.has("isInList")
            || obj.has("isHidden")
            || obj.has("AllowEnableByAll")
            || obj.has("dependencies")
            || obj.has("conflicts");
    }

    private static String getString(JsonObject obj, String key) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    private static boolean getBoolean(JsonObject obj, String key, boolean fallback) {
        if (obj != null && obj.has(key) && obj.get(key).isJsonPrimitive()
            && obj.get(key).getAsJsonPrimitive().isBoolean()) {
            return obj.get(key).getAsBoolean();
        }
        return fallback;
    }

    private static JsonObject sanitizeLocalized(JsonObject obj) {
        JsonObject result = new JsonObject();
        if (obj == null) {
            return result;
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                result.addProperty(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return result;
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

    private static final class DependencyResult {
        JsonElement element = new com.google.gson.JsonPrimitive("");
        List<String> names = new ArrayList<>();
        List<DlcRemoteEntry> entries = new ArrayList<>();
    }

    private static final class NormalizedRemote {
        final DlcRemoteEntry entry;
        final JsonObject ordered;

        private NormalizedRemote(DlcRemoteEntry entry, JsonObject ordered) {
            this.entry = entry;
            this.ordered = ordered;
        }
    }
}
