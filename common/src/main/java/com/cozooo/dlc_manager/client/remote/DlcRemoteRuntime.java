package com.cozooo.dlc_manager.client.remote;

import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate;
import com.cozooo.dlc_manager.core.DcConfigIO;
import com.cozooo.dlc_manager.core.DcFormatUtil;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.DlcTooltipLoader;
import com.cozooo.dlc_manager.core.DlcType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DlcRemoteRuntime {
    private static boolean promptedThisSession = false;

    private DlcRemoteRuntime() {
    }

    public static boolean hasMissing() {
        return !collectMissing().isEmpty();
    }

    public static List<DlcRemoteEntry> pollMissingForManagerPrompt() {
        if (promptedThisSession) {
            return List.of();
        }
        Path dlcRoot = DlcPathConfig.getDlcRoots().isEmpty() ? null : DlcPathConfig.getDlcRoots().get(0);
        if (dlcRoot != null && DlcRemoteMarker.hasPartMarker(dlcRoot)) {
            return List.of();
        }
        List<DlcRemoteEntry> missing = collectMissing();
        if (!missing.isEmpty()) {
            promptedThisSession = true;
        }
        return missing;
    }

    public static void markPrompted() {
        promptedThisSession = true;
    }

    public static List<DlcRemoteEntry> collectMissing() {
        List<DlcRemoteEntry> missing = new ArrayList<>();
        Path dlcRoot = DlcPathConfig.getDlcRoots().isEmpty() ? null : DlcPathConfig.getDlcRoots().get(0);
        if (dlcRoot == null) {
            return missing;
        }
        List<DlcRemoteEntry> entries = DlcRemoteLoader.load(dlcRoot);
        for (DlcRemoteEntry entry : entries) {
            if (!entry.isInList() && !entry.isHidden()) {
                continue;
            }
            Path target = entry.getTargetPath();
            if (target == null || !Files.exists(target)) {
                missing.add(entry);
            }
        }
        return missing;
    }

    public static void updateFileNameKey(DlcRemoteEntry entry, String fileName) {
        if (entry == null) {
            return;
        }
        String normalized = normalizeFileName(fileName);
        if (normalized == null) {
            return;
        }
        Path config = entry.getConfigPath();
        if (config == null || !Files.exists(config)) {
            return;
        }
        JsonObject obj = DcConfigIO.read(config, DcConfigIO.Layout.DLC_STRUCTURED);
        if (obj == null) {
            return;
        }
        JsonObject reordered = reorderRemoteJson(obj, normalized);
        Path target = DcFormatUtil.toDcPath(config);
        if (target != null && DcFormatUtil.write(target, reordered)) {
            if (!target.equals(config)) {
                try {
                    Files.deleteIfExists(config);
                } catch (IOException ignored) {
                }
            }
            entry.setConfigPath(target);
        }
        entry.setFileNameHint(normalized);
    }

    private static JsonObject reorderRemoteJson(JsonObject obj, String fileNameOverride) {
        JsonObject ordered = new JsonObject();
        ordered.addProperty("modrinth_projectid", getString(obj, "modrinth_projectid"));
        ordered.addProperty("modrinth_versionid", getString(obj, "modrinth_versionid"));

        JsonElement directUrl = obj.get("directUrl");
        if (directUrl == null || directUrl.isJsonNull()) {
            ordered.addProperty("directUrl", "");
        } else {
            ordered.add("directUrl", directUrl);
        }

        String priority = DlcDownloadCandidate.readPriority(obj);
        if (priority == null || priority.isBlank()) {
            priority = "curseforge";
        }
        ordered.addProperty("priority", priority);

        ordered.addProperty("identifier", getIdentifier(obj));

        String type = getString(obj, "type");
        if (type == null || type.isBlank()) {
            type = "mod";
        }
        ordered.addProperty("type", type);
        String appliedTarget = getString(obj, "appliedTarget");
        if (appliedTarget != null && !appliedTarget.isBlank()) {
            ordered.addProperty("appliedTarget", appliedTarget);
        }

        String fileName = fileNameOverride;
        if (fileName == null || fileName.isBlank()) {
            fileName = getFileName(obj);
        }
        if (fileName != null && !fileName.isBlank()) {
            ordered.addProperty("file_name", fileName);
        }

        ordered.addProperty("isInList", getBoolean(obj, "isInList", true));
        ordered.addProperty("isHidden", getBoolean(obj, "isHidden", false));
        ordered.addProperty("AllowEnableByAll", getBoolean(obj, "AllowEnableByAll", true));

        JsonElement deps = obj.get("dependencies");
        ordered.add("dependencies", normalizeRelationElement(deps));
        JsonElement conflicts = obj.get("conflicts");
        ordered.add("conflicts", normalizeRelationElement(conflicts));

        JsonObject display = obj.has("DisplayName") && obj.get("DisplayName").isJsonObject()
            ? obj.getAsJsonObject("DisplayName")
            : new JsonObject();
        if (display.entrySet().isEmpty()) {
            display.addProperty("en_us", "");
        }
        ordered.add("DisplayName", display);
        ordered.addProperty("alarm", getBoolean(obj, "alarm", false));

        JsonObject tip = obj.has("tip") && obj.get("tip").isJsonObject()
            ? obj.getAsJsonObject("tip")
            : new JsonObject();
        ordered.add("tip", tip);

        for (var entry : obj.entrySet()) {
            String key = entry.getKey();
            if (key == null || ordered.has(key)) {
                continue;
            }
            if ("dlc_name".equalsIgnoreCase(key) || "dlc_identifier".equalsIgnoreCase(key)) {
                continue;
            }
            ordered.add(key, entry.getValue());
        }
        return ordered;
    }

    private static String getString(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
            return "";
        }
        return obj.get(key).getAsString();
    }

    private static String getIdentifier(JsonObject obj) {
        String identifier = getString(obj, "identifier");
        if (identifier == null || identifier.isBlank()) {
            identifier = getString(obj, "dlc_identifier");
        }
        return identifier;
    }

    private static String getFileName(JsonObject obj) {
        String fileName = getString(obj, "file_name");
        if (fileName == null || fileName.isBlank()) {
            fileName = getString(obj, "dlc_name");
        }
        return fileName;
    }

    private static JsonElement normalizeRelationElement(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return new com.google.gson.JsonPrimitive("");
        }
        if (element.isJsonPrimitive()) {
            return element.deepCopy();
        }
        if (element.isJsonArray()) {
            JsonArray array = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                array.add(normalizeRelationElement(child));
            }
            return array;
        }
        JsonObject obj = element.getAsJsonObject();
        JsonObject normalized = new JsonObject();
        for (var entry : obj.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            String targetKey = key;
            if ("dlc_identifier".equalsIgnoreCase(key)) {
                targetKey = "identifier";
            } else if ("dlc_name".equalsIgnoreCase(key)) {
                targetKey = "file_name";
            }
            if (normalized.has(targetKey)) {
                continue;
            }
            normalized.add(targetKey, normalizeRelationElement(entry.getValue()));
        }
        return normalized;
    }

    private static Map<String, String> resolveDisplayNames(DlcRemoteEntry entry) {
        Map<String, String> values = new HashMap<>();
        if (entry != null && entry.getDisplayNames() != null) {
            values.putAll(entry.getDisplayNames());
        }
        if (!values.isEmpty()) {
            return values;
        }
        JsonObject obj = entry == null ? null : DcConfigIO.read(entry.getConfigPath(), DcConfigIO.Layout.DLC_STRUCTURED);
        values.putAll(readLocalizedAny(obj, "DisplayName"));
        return values;
    }

    private static Map<String, String> resolveTips(DlcRemoteEntry entry) {
        Map<String, String> values = new HashMap<>();
        if (entry != null && entry.getTips() != null) {
            values.putAll(entry.getTips());
        }
        if (!values.isEmpty()) {
            return values;
        }
        JsonObject obj = entry == null ? null : DcConfigIO.read(entry.getConfigPath(), DcConfigIO.Layout.DLC_STRUCTURED);
        values.putAll(readLocalizedAny(obj, "tip"));
        return values;
    }

    private static Map<String, String> readLocalizedAny(JsonObject obj, String key) {
        Map<String, String> values = new HashMap<>();
        if (obj == null || key == null || key.isBlank()) {
            return values;
        }
        JsonObject object = DcConfigIO.getObjectIgnoreCase(obj, key);
        if (object != null) {
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (entry.getValue() == null || !entry.getValue().isJsonPrimitive()) {
                    continue;
                }
                String lang = DcConfigIO.normalizeLangKey(entry.getKey());
                if (lang == null) {
                    continue;
                }
                values.put(lang, entry.getValue().getAsString());
            }
        }
        if (!values.isEmpty()) {
            return values;
        }
        String single = DcConfigIO.getStringIgnoreCase(obj, key);
        if (single != null && !single.isBlank()) {
            values.put("en_us", single);
        }
        return values;
    }

    private static boolean getBoolean(JsonObject obj, String key, boolean fallback) {
        if (obj == null || key == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return obj.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String normalizeFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        String trimmed = DlcDownloadUtil.stripQuery(fileName);
        if (trimmed == null) {
            return null;
        }
        trimmed = trimmed.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (DcFormatUtil.isDcName(trimmed)) {
            return null;
        }
        return trimmed.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
