package com.cozooo.dlc_manager.client.required;

import com.cozooo.dlc_manager.core.DcFormatUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.cozooo.dlc_manager.core.JsonCaseUtil;
import com.cozooo.dlc_manager.core.Json5Util;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DlcRequiredLoader {
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
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(requiredDir)) {
            for (Path path : stream) {
                if (path.getFileName() == null) {
                    continue;
                }
                String fileName = path.getFileName().toString();
                if (!Json5Util.isJsonLikeName(fileName) || Json5Util.shouldSkipLegacy(path)) {
                    continue;
                }
                if (isNoticeConfig(fileName)) {
                    continue;
                }
                Path migrated = Json5Util.migrateLegacy(path, PRETTY_GSON);
                DlcRequiredEntry entry = readEntry(migrated, requiredDir);
                if (entry != null && entry.isInList()) {
                    entries.add(entry);
                }
            }
        } catch (IOException ignored) {
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
        configPath = resolveExistingConfigPath(configPath);
        if (!Files.exists(configPath)) {
            return null;
        }
        Path migrated = Json5Util.migrateLegacy(configPath, PRETTY_GSON);
        return readEntry(migrated, requiredDir);
    }

    private static DlcRequiredEntry readEntry(Path path, Path requiredDir) {
        if (path == null) {
            return null;
        }
        JsonObject obj = readConfigObject(path);
        if (obj == null) {
            return null;
        }
        NormalizedRequired normalized = normalizeRequiredObject(obj, requiredDir, path, null);
        if (normalized == null) {
            return null;
        }
        if (path.getFileName() != null && DcFormatUtil.isDcName(path.getFileName().toString())) {
            if (needsKeyMigration(obj) || needsDcLayoutMigration(path)) {
                DcFormatUtil.write(path, normalized.ordered);
            }
        } else {
            Path dcPath = Json5Util.toDcPath(path);
            if (dcPath != null && DcFormatUtil.write(dcPath, normalized.ordered)) {
                try {
                    Files.deleteIfExists(path);
                    path = dcPath;
                } catch (IOException ignored) {
                }
            }
        }
        return normalized.entry;
    }

    private static JsonObject readConfigObject(Path path) {
        if (path == null || path.getFileName() == null) {
            return null;
        }
        if (DcFormatUtil.isDcName(path.getFileName().toString())) {
            return DcFormatUtil.read(path);
        }
        JsonElement element = Json5Util.readElement(path);
        if (element != null && element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        return null;
    }

    private static Path resolveExistingConfigPath(Path configPath) {
        if (configPath == null || configPath.getFileName() == null) {
            return configPath;
        }
        if (Files.exists(configPath)) {
            return configPath;
        }
        String name = configPath.getFileName().toString();
        String base = Json5Util.stripJsonExtension(name);
        Path parent = configPath.getParent();
        if (parent == null) {
            return configPath;
        }
        Path dc = parent.resolve(base + Json5Util.DC_EXTENSION);
        if (Files.exists(dc)) {
            return dc;
        }
        Path json5 = parent.resolve(base + Json5Util.JSON5_EXTENSION);
        if (Files.exists(json5)) {
            return json5;
        }
        Path json = parent.resolve(base + Json5Util.JSON_EXTENSION);
        if (Files.exists(json)) {
            return json;
        }
        return configPath;
    }

    private static boolean isNoticeConfig(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        return "notice".equalsIgnoreCase(Json5Util.stripJsonExtension(fileName));
    }

    public static DlcRequiredEntry fromJsonObject(JsonObject obj, Path requiredDir, Path configPath, String defaultIdentifier) {
        if (obj == null) {
            return null;
        }
        DlcRequiredEntry entry = new DlcRequiredEntry(configPath, requiredDir);
        if (defaultIdentifier != null && !defaultIdentifier.isBlank()) {
            entry.setIdentifier(defaultIdentifier);
        }
        String identifier = getString(obj, "identifier");
        if (identifier != null && !identifier.isBlank()) {
            entry.setIdentifier(identifier);
        }
        String fileName = getString(obj, "file_name");
        if (fileName != null && !fileName.isBlank()) {
            entry.setRequiredFileName(fileName);
        }
        List<String> directUrls = getDirectUrls(obj);
        if (!directUrls.isEmpty()) {
            entry.setDirectUrls(directUrls);
        } else {
            String directUrl = getString(obj, "directUrl");
            if (directUrl != null && !directUrl.isBlank()) {
                entry.setDirectUrl(directUrl);
            }
        }
        String modrinthProjectId = getString(obj, "modrinth_projectid");
        if (modrinthProjectId != null && !modrinthProjectId.isBlank()) {
            entry.setModrinthProjectId(modrinthProjectId);
        }
        String modrinthVersionId = getString(obj, "modrinth_versionid");
        if (modrinthVersionId != null && !modrinthVersionId.isBlank()) {
            entry.setModrinthVersionId(modrinthVersionId);
        }
        String curseProjectId = getString(obj, "curseforge_projectid");
        if (curseProjectId != null && !curseProjectId.isBlank()) {
            entry.setCurseforgeProjectId(curseProjectId);
        }
        String curseFileId = getString(obj, "curseforge_fileid");
        if (curseFileId != null && !curseFileId.isBlank()) {
            entry.setCurseforgeFileId(curseFileId);
        }
        String priority = getString(obj, "priority");
        if (priority != null && !priority.isBlank()) {
            entry.setDownloadPriority(priority);
        }
        Boolean inList = getBoolean(obj, "isInList");
        if (inList != null) {
            entry.setInList(inList);
        }
        Boolean hidden = getBoolean(obj, "isHidden");
        if (hidden != null) {
            entry.setHidden(hidden);
        }
        Boolean userRemoved = getBoolean(obj, "UserRemoved");
        if (userRemoved != null) {
            entry.setUserRemoved(userRemoved);
        }
        readDisplayNames(obj, entry);
        readTips(obj, entry);
        String applied = null;
        applied = getString(obj, "appliedTarget");
        entry.setAppliedTargetRaw(applied == null ? "" : applied);
        JsonElement depsElement = JsonCaseUtil.getIgnoreCase(obj, "dependencies");
        if (depsElement != null) {
            parseDependencies(depsElement, entry, requiredDir);
        }
        return entry;
    }

    private static void readDisplayNames(JsonObject obj, DlcRequiredEntry entry) {
        if (obj == null || entry == null) {
            return;
        }
        JsonObject display = JsonCaseUtil.getObjectIgnoreCase(obj, "displayName");
        if (display == null) {
            display = JsonCaseUtil.getObjectIgnoreCase(obj, "DisplayName");
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
                String key = JsonCaseUtil.normalizeLangKey(e.getKey());
                if (key != null) {
                    entry.putDisplayName(key, e.getValue().getAsString());
                }
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
        JsonObject tipObj = JsonCaseUtil.getObjectIgnoreCase(obj, "tip");
        if (tipObj != null) {
            for (var e : tipObj.entrySet()) {
                if (e.getValue() != null && e.getValue().isJsonPrimitive()) {
                    String key = JsonCaseUtil.normalizeLangKey(e.getKey());
                    if (key != null) {
                        entry.putTip(key, e.getValue().getAsString());
                    }
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
        String resolved = getString(obj, "identifier");
        if (resolved != null && !resolved.isBlank()) {
            identifier = resolved;
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
        if (obj == null) {
            return List.of();
        }
        JsonElement element = JsonCaseUtil.getIgnoreCase(obj, "directUrl");
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
        DependencyResult deps = normalizeDependencies(JsonCaseUtil.getIgnoreCase(obj, "dependencies"), requiredDir);
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
                || "tip".equalsIgnoreCase(key)
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

    private static boolean needsKeyMigration(JsonObject obj) {
        if (obj == null) {
            return false;
        }
        return JsonCaseUtil.hasIgnoreCase(obj, "dlc_name")
            || JsonCaseUtil.hasIgnoreCase(obj, "dlc_identifier");
    }

    private static boolean needsDcLayoutMigration(Path path) {
        if (path == null || path.getFileName() == null || !DcFormatUtil.isDcName(path.getFileName().toString())) {
            return false;
        }
        try {
            String text = Files.readString(path);
            if (text == null || text.isBlank()) {
                return false;
            }
            return !text.contains("[basic]");
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean isIdentifierOnlyObject(JsonObject obj) {
        if (obj == null || obj.size() != 1) {
            return false;
        }
        String value = getString(obj, "identifier");
        return value != null && !value.isBlank();
    }

    private static String resolveIdentifier(JsonObject obj, String fallback) {
        String value = getString(obj, "identifier");
        if (value != null && !value.isBlank()) {
            return value;
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

    private static String getString(JsonObject obj, String key) {
        return JsonCaseUtil.getStringIgnoreCase(obj, key);
    }

    private static Boolean getBoolean(JsonObject obj, String key) {
        return JsonCaseUtil.getBooleanIgnoreCase(obj, key);
    }

    private static final class DependencyResult {
        private JsonElement element;
        private final List<String> names = new ArrayList<>();
        private final List<DlcRequiredEntry> entries = new ArrayList<>();
    }
}
