package com.cozooo.dlc_manager.client.required;

import com.cozooo.dlc_manager.client.remote.ModrinthClient;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.client.screen.DlcRequiredDownloadScreen;
import com.cozooo.dlc_manager.core.DlcPathConfig;
import com.cozooo.dlc_manager.core.Json5Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DlcRequiredManager {
    private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();
    private static boolean promptedThisSession = false;

    private DlcRequiredManager() {
    }

    public static boolean maybeShowFromTitle(Screen parent) {
        if (promptedThisSession) {
            return false;
        }
        if (hasSkipMarker()) {
            return false;
        }
        if (!hasMissingDownloadsForPrompt()) {
            return false;
        }
        promptedThisSession = true;
        Minecraft.getInstance().setScreen(new DlcRequiredDownloadScreen(parent, collectMissingDownloadsForPrompt()));
        return true;
    }

    public static void markPrompted() {
        promptedThisSession = true;
    }

    public static void markSkipped() {
        if (hasForceMarker()) {
            return;
        }
        Path requiredDir = getRequiredDir();
        if (requiredDir == null) {
            return;
        }
        try {
            Files.createDirectories(requiredDir);
            Files.writeString(requiredDir.resolve("SKIP"), "");
        } catch (Exception ignored) {
            // Best-effort.
        }
    }

    public static boolean hasSkipMarker() {
        if (hasForceMarker()) {
            return false;
        }
        Path requiredDir = getRequiredDir();
        if (requiredDir == null) {
            return false;
        }
        return Files.exists(requiredDir.resolve("SKIP"));
    }

    public static boolean hasForceMarker() {
        Path requiredDir = getRequiredDir();
        if (requiredDir == null) {
            return false;
        }
        return Files.exists(requiredDir.resolve("FORCE"));
    }

    public static boolean hasForceRestartMarker() {
        Path requiredDir = getRequiredDir();
        if (requiredDir == null) {
            return false;
        }
        Path marker = requiredDir.resolve("FORCE");
        if (!Files.exists(marker)) {
            return false;
        }
        try {
            String content = Files.readString(marker);
            return content != null && content.toLowerCase(java.util.Locale.ROOT).contains("forcerestart");
        } catch (Exception ignored) {
            return false;
        }
    }

    public static List<DlcRequiredEntry> loadEntries() {
        return DlcRequiredLoader.load(getDlcRoot());
    }

    public static List<DlcRequiredEntry> loadAllEntries() {
        List<DlcRequiredEntry> entries = new ArrayList<>(loadEntries());
        Map<String, DlcRequiredEntry> index = new HashMap<>();
        for (DlcRequiredEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            String id = entry.getIdentifier();
            if (id != null && !id.isBlank()) {
                index.putIfAbsent(id, entry);
            }
        }
        ArrayDeque<DlcRequiredEntry> queue = new ArrayDeque<>(entries);
        while (!queue.isEmpty()) {
            DlcRequiredEntry entry = queue.poll();
            if (entry == null) {
                continue;
            }
            for (DlcRequiredDependency dep : entry.getDependencies()) {
                if (dep == null) {
                    continue;
                }
                String id = dep.getIdentifier();
                if (id == null || id.isBlank()) {
                    continue;
                }
                if (index.containsKey(id)) {
                    continue;
                }
                DlcRequiredEntry depEntry = dep.getEntry();
                if (depEntry != null && depEntry.isInList()) {
                    depEntry.setIdentifier(id);
                    index.put(id, depEntry);
                    entries.add(depEntry);
                    queue.add(depEntry);
                }
            }
        }
        return entries;
    }

    private static Path getDlcRoot() {
        return DlcPathConfig.getDlcRoots().isEmpty() ? null : DlcPathConfig.getDlcRoots().get(0);
    }

    private static Path getRequiredDir() {
        Path dlcRoot = getDlcRoot();
        if (dlcRoot == null) {
            return null;
        }
        return dlcRoot.resolve("required");
    }

    public static Map<String, DlcRequiredEntry> buildIndex(List<DlcRequiredEntry> entries) {
        Map<String, DlcRequiredEntry> map = new HashMap<>();
        for (DlcRequiredEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            String id = entry.getIdentifier();
            if (id == null || id.isBlank()) {
                continue;
            }
            map.putIfAbsent(id, entry);
        }
        return map;
    }

    public static Map<String, Set<String>> buildDependents(List<DlcRequiredEntry> entries) {
        Map<String, Set<String>> dependents = new HashMap<>();
        Map<String, DlcRequiredEntry> index = buildIndex(entries);
        for (DlcRequiredEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            String id = entry.getIdentifier();
            if (id == null || id.isBlank()) {
                continue;
            }
            for (DlcRequiredDependency dep : entry.getDependencies()) {
                String depId = dep == null ? null : dep.getIdentifier();
                if (depId == null || depId.isBlank()) {
                    continue;
                }
                if (!index.containsKey(depId) && dep != null && dep.getEntry() != null) {
                    index.put(depId, dep.getEntry());
                }
                dependents.computeIfAbsent(depId, k -> new HashSet<>()).add(id);
            }
        }
        return dependents;
    }

    public static List<DlcRequiredEntry> collectDependencies(List<DlcRequiredEntry> roots) {
        List<DlcRequiredEntry> result = new ArrayList<>();
        if (roots == null || roots.isEmpty()) {
            return result;
        }
        List<DlcRequiredEntry> all = loadAllEntries();
        Map<String, DlcRequiredEntry> index = buildIndex(all);
        ArrayDeque<DlcRequiredEntry> queue = new ArrayDeque<>(roots);
        Set<String> seen = new HashSet<>();
        while (!queue.isEmpty()) {
            DlcRequiredEntry entry = queue.poll();
            if (entry == null) {
                continue;
            }
            String id = entry.getIdentifier();
            if (id == null || id.isBlank()) {
                continue;
            }
            if (!seen.add(id)) {
                continue;
            }
            DlcRequiredEntry resolved = index.getOrDefault(id, entry);
            result.add(resolved);
            for (DlcRequiredDependency dep : resolved.getDependencies()) {
                String depId = dep == null ? null : dep.getIdentifier();
                if (depId == null || depId.isBlank()) {
                    continue;
                }
                DlcRequiredEntry depEntry = index.get(depId);
                if (depEntry != null) {
                    queue.add(depEntry);
                } else if (dep != null && dep.getEntry() != null) {
                    queue.add(dep.getEntry());
                }
            }
        }
        return result;
    }

    public static boolean hasMissingDownloads() {
        List<DlcRequiredEntry> entries = loadAllEntries();
        for (DlcRequiredEntry entry : entries) {
            if (entry == null || !entry.isInList()) {
                continue;
            }
            String fileName = normalizeFileName(entry.getRequiredFileName());
            if (fileName == null) {
                fileName = normalizeFileName(resolveFileNameLocal(entry));
            }
            if (fileName == null) {
                fileName = normalizeFileName(resolveExistingRequiredFile(entry));
            }
            if (isTempFileName(fileName)) {
                fileName = null;
            }
            entry.setResolvedFileName(fileName);
            if (fileName == null) {
                return true;
            }
            Path required = entry.resolveRequiredPath(fileName);
            if (required == null || !Files.exists(required)) {
                return true;
            }
            ensureFileNameKey(entry, fileName);
        }
        return false;
    }

    public static boolean hasMissingDownloadsForPrompt() {
        List<DlcRequiredEntry> entries = loadAllEntries();
        for (DlcRequiredEntry entry : entries) {
            if (entry == null || !entry.isInList() || entry.isUserRemoved()) {
                continue;
            }
            String fileName = normalizeFileName(entry.getRequiredFileName());
            if (fileName == null) {
                fileName = normalizeFileName(resolveFileNameLocal(entry));
            }
            if (fileName == null) {
                fileName = normalizeFileName(resolveExistingRequiredFile(entry));
            }
            entry.setResolvedFileName(fileName);
            if (isTempFileName(fileName)) {
                fileName = null;
            }
            if (fileName == null) {
                return true;
            }
            Path required = entry.resolveRequiredPath(fileName);
            if (required == null || !Files.exists(required) || entryNeedsApply(entry)) {
                return true;
            }
            ensureFileNameKey(entry, fileName);
        }
        return false;
    }

    public static boolean hasMissingOrApply() {
        List<DlcRequiredEntry> entries = loadAllEntries();
        for (DlcRequiredEntry entry : entries) {
            if (entry == null || !entry.isInList()) {
                continue;
            }
            String fileName = normalizeFileName(entry.getRequiredFileName());
            if (fileName == null) {
                fileName = normalizeFileName(resolveFileNameLocal(entry));
            }
            entry.setResolvedFileName(fileName);
            if (fileName == null) {
                return true;
            }
            Path required = entry.resolveRequiredPath(fileName);
            if (required == null || !Files.exists(required)) {
                return true;
            }
            ensureFileNameKey(entry, fileName);
            if (entryNeedsApply(entry)) {
                return true;
            }
        }
        return false;
    }

    public static List<DlcRequiredEntry> collectMissingDownloads() {
        List<DlcRequiredEntry> missing = new ArrayList<>();
        List<DlcRequiredEntry> entries = loadAllEntries();
        for (DlcRequiredEntry entry : entries) {
            if (entry == null || !entry.isInList()) {
                continue;
            }
            String fileName = normalizeFileName(entry.getRequiredFileName());
            if (fileName == null) {
                fileName = normalizeFileName(resolveFileNameLocal(entry));
            }
            if (fileName == null) {
                fileName = normalizeFileName(resolveExistingRequiredFile(entry));
            }
            entry.setResolvedFileName(fileName);
            if (isTempFileName(fileName)) {
                fileName = null;
            }
            if (fileName == null) {
                missing.add(entry);
                continue;
            }
            Path required = entry.resolveRequiredPath(fileName);
            if (required == null || !Files.exists(required)) {
                missing.add(entry);
            } else {
                ensureFileNameKey(entry, fileName);
            }
        }
        return missing;
    }

    public static List<DlcRequiredEntry> collectMissingDownloadsForPrompt() {
        List<DlcRequiredEntry> missing = new ArrayList<>();
        List<DlcRequiredEntry> entries = loadAllEntries();
        for (DlcRequiredEntry entry : entries) {
            if (entry == null || !entry.isInList() || entry.isUserRemoved()) {
                continue;
            }
            String fileName = normalizeFileName(entry.getRequiredFileName());
            if (fileName == null) {
                fileName = normalizeFileName(resolveFileNameLocal(entry));
            }
            if (fileName == null) {
                fileName = normalizeFileName(resolveExistingRequiredFile(entry));
            }
            entry.setResolvedFileName(fileName);
            if (isTempFileName(fileName)) {
                fileName = null;
            }
            if (fileName == null) {
                missing.add(entry);
                continue;
            }
            Path required = entry.resolveRequiredPath(fileName);
            if (required == null || !Files.exists(required) || entryNeedsApply(entry)) {
                missing.add(entry);
            } else {
                ensureFileNameKey(entry, fileName);
            }
        }
        return missing;
    }

    public static List<DlcRequiredEntry> collectMissingOrApply() {
        List<DlcRequiredEntry> list = new ArrayList<>();
        List<DlcRequiredEntry> entries = loadAllEntries();
        for (DlcRequiredEntry entry : entries) {
            if (entry == null || !entry.isInList()) {
                continue;
            }
            String fileName = normalizeFileName(entry.getRequiredFileName());
            if (fileName == null) {
                fileName = normalizeFileName(resolveFileNameLocal(entry));
            }
            entry.setResolvedFileName(fileName);
            if (isTempFileName(fileName)) {
                fileName = null;
            }
            if (fileName == null) {
                list.add(entry);
                continue;
            }
            Path required = entry.resolveRequiredPath(fileName);
            if (required == null || !Files.exists(required) || entryNeedsApply(entry)) {
                list.add(entry);
            } else {
                ensureFileNameKey(entry, fileName);
            }
        }
        return list;
    }

    public static boolean hasMissingAppliedTargets() {
        List<DlcRequiredEntry> entries = loadEntries();
        for (DlcRequiredEntry entry : entries) {
            if (entryNeedsApply(entry)) {
                return true;
            }
        }
        return false;
    }

    public static boolean entryNeedsApply(DlcRequiredEntry entry) {
        if (entry == null || !entry.isInList()) {
            return false;
        }
        String fileName = normalizeFileName(entry.getResolvedFileName());
        if (fileName == null || fileName.isBlank()) {
            fileName = normalizeFileName(guessFileName(entry));
            if (fileName == null) {
                String versionId = entry.getModrinthVersionId();
                if (versionId != null && !versionId.isBlank()) {
                    try {
                        ModrinthClient.ModrinthVersion version = ModrinthClient.fetchVersion(versionId);
                        if (version != null && version.fileName != null && !version.fileName.isBlank()) {
                            fileName = normalizeFileName(version.fileName);
                        }
                    } catch (Exception ignored) {
                        // Best-effort.
                    }
                }
            }
            entry.setResolvedFileName(fileName);
        }
        if (isTempFileName(fileName)) {
            entry.setResolvedFileName(null);
            return true;
        }
        if (fileName == null) {
            return true;
        }
        for (Path target : entry.resolveAppliedTargets()) {
            Path targetFile = resolveTarget(target, fileName);
            if (!Files.exists(targetFile)) {
                return true;
            }
        }
        return false;
    }

    public static String guessFileName(DlcRequiredEntry entry) {
        if (entry == null) {
            return null;
        }
        String directUrl = entry.getDirectUrl();
        if (directUrl != null && !directUrl.isBlank()) {
            String name = DlcDownloadUtil.extractFileNameFromUrl(directUrl);
            if (DlcDownloadUtil.isLikelyFileName(name)) {
                return name;
            }
        }
        return null;
    }

    private static String resolveFileName(DlcRequiredEntry entry) {
        String versionId = entry == null ? null : entry.getModrinthVersionId();
        if (versionId != null && !versionId.isBlank()) {
            try {
                ModrinthClient.ModrinthVersion version = ModrinthClient.fetchVersion(versionId);
                if (version != null && version.fileName != null && !version.fileName.isBlank()) {
                    return version.fileName;
                }
            } catch (Exception ignored) {
                // Best-effort.
            }
        }
        String fileName = guessFileName(entry);
        if (fileName != null && !fileName.isBlank()) {
            return fileName;
        }
        return null;
    }

    private static String resolveFileNameLocal(DlcRequiredEntry entry) {
        String fileName = guessFileName(entry);
        if (fileName != null && !fileName.isBlank()) {
            return fileName;
        }
        return null;
    }

    private static String resolveExistingRequiredFile(DlcRequiredEntry entry) {
        if (entry == null) {
            return null;
        }
        String base = entry.getBaseName();
        if (entry.getRequiredDir() == null || base == null || base.isBlank()) {
            return null;
        }
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(entry.getRequiredDir(), base + ".*")) {
            for (Path path : stream) {
                String name = path.getFileName() == null ? "" : path.getFileName().toString();
                if (!Json5Util.isJsonLikeName(name) && !name.endsWith(".part")) {
                    return name;
                }
            }
        } catch (Exception ignored) {
            // Best-effort.
        }
        return null;
    }

    public static void ensureFileNameKey(DlcRequiredEntry entry, String fileName) {
        if (entry == null) {
            return;
        }
        fileName = normalizeFileName(fileName);
        if (fileName == null) {
            return;
        }
        if (normalizeFileName(entry.getRequiredFileName()) != null) {
            return;
        }
        Path config = entry.getConfigPath();
        if (config == null || !Files.exists(config)) {
            return;
        }
        try {
            com.google.gson.JsonObject obj = Json5Util.readObject(config);
            if (obj == null) {
                return;
            }
            com.google.gson.JsonObject reordered = reorderRequiredJson(obj, fileName);
            try (java.io.Writer writer = Files.newBufferedWriter(config)) {
                GSON.toJson(reordered, writer);
            }
            entry.setRequiredFileName(fileName);
        } catch (Exception ignored) {
            // Best-effort.
        }
    }

    public static void updateFileNameKey(DlcRequiredEntry entry, String fileName) {
        if (entry == null) {
            return;
        }
        fileName = normalizeFileName(fileName);
        if (fileName == null) {
            return;
        }
        Path config = entry.getConfigPath();
        if (config == null || !Files.exists(config)) {
            return;
        }
        try {
            com.google.gson.JsonObject obj = Json5Util.readObject(config);
            if (obj == null) {
                return;
            }
            com.google.gson.JsonObject reordered = reorderRequiredJson(obj, fileName);
            try (java.io.Writer writer = Files.newBufferedWriter(config)) {
                GSON.toJson(reordered, writer);
            }
            entry.setRequiredFileName(fileName);
        } catch (Exception ignored) {
            // Best-effort.
        }
    }

    public static void updateUserRemoved(DlcRequiredEntry entry, boolean userRemoved) {
        if (entry == null) {
            return;
        }
        Path config = entry.getConfigPath();
        if (config == null || !Files.exists(config)) {
            return;
        }
        try {
            com.google.gson.JsonObject obj = Json5Util.readObject(config);
            if (obj == null) {
                return;
            }
            if (userRemoved) {
                obj.addProperty("UserRemoved", true);
            } else {
                obj.remove("UserRemoved");
                obj.remove("userRemoved");
            }
            com.google.gson.JsonObject reordered = reorderRequiredJson(obj, entry.getRequiredFileName());
            try (java.io.Writer writer = Files.newBufferedWriter(config)) {
                GSON.toJson(reordered, writer);
            }
            entry.setUserRemoved(userRemoved);
        } catch (Exception ignored) {
            // Best-effort.
        }
    }

    private static com.google.gson.JsonObject reorderRequiredJson(com.google.gson.JsonObject obj, String fileNameOverride) {
        com.google.gson.JsonObject ordered = new com.google.gson.JsonObject();
        ordered.addProperty("modrinth_projectid", getString(obj, "modrinth_projectid"));
        ordered.addProperty("modrinth_versionid", getString(obj, "modrinth_versionid"));

        com.google.gson.JsonElement directUrl = obj.get("directUrl");
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

        ordered.addProperty("identifier", getString(obj, "identifier"));

        String fileName = fileNameOverride;
        if (fileName == null || fileName.isBlank()) {
            fileName = getString(obj, "file_name");
        }
        if (fileName != null && !fileName.isBlank()) {
            ordered.addProperty("file_name", fileName);
        }

        String applied = getString(obj, "appliedTarget");
        if (applied == null || applied.isBlank()) {
            applied = getString(obj, "appliedTaget");
        }
        ordered.addProperty("appliedTarget", applied == null ? "" : applied);

        ordered.addProperty("isInList", getBoolean(obj, "isInList", true));
        ordered.addProperty("isHidden", getBoolean(obj, "isHidden", false));
        boolean userRemoved = getBoolean(obj, "UserRemoved", false) || getBoolean(obj, "userRemoved", false);
        if (userRemoved) {
            ordered.addProperty("UserRemoved", true);
        }

        com.google.gson.JsonElement deps = obj.get("dependencies");
        ordered.add("dependencies", deps == null ? new com.google.gson.JsonPrimitive("") : deps);

        com.google.gson.JsonObject display = null;
        if (obj.has("DisplayName") && obj.get("DisplayName").isJsonObject()) {
            display = obj.getAsJsonObject("DisplayName");
        } else if (obj.has("displayName") && obj.get("displayName").isJsonObject()) {
            display = obj.getAsJsonObject("displayName");
        }
        if (display == null) {
            display = new com.google.gson.JsonObject();
            display.addProperty("en_us", "");
        }
        ordered.add("DisplayName", display);

        com.google.gson.JsonObject tip = obj.has("tip") && obj.get("tip").isJsonObject()
            ? obj.getAsJsonObject("tip")
            : new com.google.gson.JsonObject();
        ordered.add("tip", tip);

        for (var entry : obj.entrySet()) {
            String key = entry.getKey();
            if (key == null || ordered.has(key)) {
                continue;
            }
            if ("appliedTaget".equals(key) || "displayName".equals(key)) {
                continue;
            }
            if ("UserRemoved".equals(key) || "userRemoved".equals(key)) {
                continue;
            }
            ordered.add(key, entry.getValue());
        }
        return ordered;
    }

    private static String getString(com.google.gson.JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
            return "";
        }
        return obj.get(key).getAsString();
    }

    private static boolean getBoolean(com.google.gson.JsonObject obj, String key, boolean fallback) {
        if (obj == null || key == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return obj.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static Path resolveTarget(Path targetDir, String fileName) {
        if (targetDir == null || fileName == null || fileName.isBlank()) {
            return null;
        }
        String name = targetDir.getFileName() == null ? "" : targetDir.getFileName().toString();
        if (name.endsWith(".jar") || name.endsWith(".zip")) {
            return targetDir;
        }
        return targetDir.resolve(fileName);
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
        if (Json5Util.isJsonLikeName(trimmed)) {
            return null;
        }
        return trimmed.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static boolean isTempFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        return fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".part");
    }
}
