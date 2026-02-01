package com.cozooo.dlc_manager.client.remote;

import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.DlcTooltipLoader;
import com.cozooo.dlc_manager.core.DlcType;
import com.cozooo.dlc_manager.core.Json5Util;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DlcRemoteManager {
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean promptedThisSession = false;

    private DlcRemoteManager() {
    }

    public static boolean maybeShowFromManager(net.minecraft.client.gui.screens.Screen parent) {
        if (promptedThisSession) {
            return false;
        }
        Path dlcRoot = DlcPathConfig.getDlcRoots().isEmpty() ? null : DlcPathConfig.getDlcRoots().get(0);
        if (dlcRoot != null && DlcRemoteMarker.hasPartMarker(dlcRoot)) {
            return false;
        }
        List<DlcRemoteEntry> missing = collectMissing();
        if (missing.isEmpty()) {
            return false;
        }
        promptedThisSession = true;
        net.minecraft.client.Minecraft.getInstance().setScreen(new com.cozooo.dlc_manager.client.screen.DlcRemoteDownloadScreen(parent, missing));
        return true;
    }

    public static boolean hasMissing() {
        return !collectMissing().isEmpty();
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
            if (target == null) {
                missing.add(entry);
                continue;
            }
            if (!Files.exists(target)) {
                missing.add(entry);
            }
        }
        return missing;
    }

    public static void writeInfoFile(Path dlcRoot, DlcRemoteEntry entry, String fileName) {
        if (entry == null || fileName == null || fileName.isBlank()) {
            return;
        }
        Path root = DlcPathConfig.getDlcRoots().isEmpty() ? null : DlcPathConfig.getDlcRoots().get(0);
        if (root == null) {
            return;
        }
        Path infoDir = root.resolve("info");
        try {
            Files.createDirectories(infoDir);
        } catch (IOException ignored) {
            return;
        }
        JsonObject obj = new JsonObject();
        obj.addProperty("dlc_name", fileName);
        String identifier = entry.getDlcIdentifier();
        if (identifier == null || identifier.isBlank()) {
            identifier = fileName;
        }
        obj.addProperty("dlc_identifier", identifier);
        JsonObject displayObj = new JsonObject();
        displayObj.addProperty("en_us", fileName);
        for (var e : entry.getDisplayNames().entrySet()) {
            displayObj.addProperty(e.getKey(), e.getValue());
        }
        obj.add("DisplayName", displayObj);
        DlcType type = entry.getType() == null ? DlcType.UNKNOWN : entry.getType();
        obj.addProperty("type", DlcTooltipLoader.toTypeString(type));
        if (type == DlcType.UNKNOWN) {
            String appliedTarget = entry.getAppliedTargetRaw();
            if (appliedTarget != null && !appliedTarget.isBlank()) {
                obj.addProperty("appliedTarget", appliedTarget);
            }
        }
        obj.addProperty("isInList", entry.isInList());
        obj.addProperty("isHidden", entry.isHidden());
        obj.addProperty("AllowEnableByAll", entry.isAllowEnableByAll());
        obj.addProperty("dependencies", String.join(",", entry.getResolvedDependencyNames()));
        obj.addProperty("conflicts", String.join(",", entry.getConflictNames()));
        JsonObject tipObj = new JsonObject();
        for (var e : entry.getTips().entrySet()) {
            tipObj.addProperty(e.getKey(), e.getValue());
        }
        obj.add("tip", tipObj);
        Path legacy = infoDir.resolve(fileName + Json5Util.JSON_EXTENSION);
        Json5Util.migrateLegacy(legacy, PRETTY_GSON);
        Path out = infoDir.resolve(fileName + Json5Util.JSON5_EXTENSION);
        try (Writer writer = Files.newBufferedWriter(out)) {
            PRETTY_GSON.toJson(obj, writer);
        } catch (IOException ignored) {
            // Best-effort.
        }
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
        try {
            JsonObject obj = Json5Util.readObject(config);
            if (obj == null) {
                return;
            }
            JsonObject reordered = reorderRemoteJson(obj, normalized);
            try (Writer writer = Files.newBufferedWriter(config)) {
                PRETTY_GSON.toJson(reordered, writer);
            }
            entry.setFileNameHint(normalized);
        } catch (IOException ignored) {
            // Best-effort.
        }
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

        String priority = getString(obj, "priority");
        if (priority == null || priority.isBlank()) {
            priority = "modrinth";
        }
        ordered.addProperty("priority", priority);

        ordered.addProperty("dlc_identifier", getString(obj, "dlc_identifier"));

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
            fileName = getString(obj, "file_name");
        }
        if (fileName != null && !fileName.isBlank()) {
            ordered.addProperty("file_name", fileName);
        }

        ordered.addProperty("isInList", getBoolean(obj, "isInList", true));
        ordered.addProperty("isHidden", getBoolean(obj, "isHidden", false));
        ordered.addProperty("AllowEnableByAll", getBoolean(obj, "AllowEnableByAll", true));

        JsonElement deps = obj.get("dependencies");
        ordered.add("dependencies", deps == null ? new com.google.gson.JsonPrimitive("") : deps);
        JsonElement conflicts = obj.get("conflicts");
        ordered.add("conflicts", conflicts == null ? new com.google.gson.JsonPrimitive("") : conflicts);

        JsonObject display = obj.has("DisplayName") && obj.get("DisplayName").isJsonObject()
            ? obj.getAsJsonObject("DisplayName")
            : new JsonObject();
        if (display.entrySet().isEmpty()) {
            display.addProperty("en_us", "");
        }
        ordered.add("DisplayName", display);

        JsonObject tip = obj.has("tip") && obj.get("tip").isJsonObject()
            ? obj.getAsJsonObject("tip")
            : new JsonObject();
        ordered.add("tip", tip);

        for (var entry : obj.entrySet()) {
            String key = entry.getKey();
            if (key == null || ordered.has(key)) {
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
        String trimmed = com.cozooo.dlc_manager.client.download.DlcDownloadUtil.stripQuery(fileName);
        if (trimmed == null) {
            return null;
        }
        trimmed = trimmed.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (Json5Util.isJsonLikeName(trimmed)) {
            return null;
        }
        return trimmed.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
