package com.cozooo.dlc_manager.client.required;

import com.cozooo.dlc_manager.client.remote.ModrinthClient;
import com.cozooo.dlc_manager.client.screen.DlcRequiredDownloadScreen;
import com.cozooo.dlc_manager.core.DlcPathConfig;
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
        if (!hasMissingDownloads()) {
            return false;
        }
        promptedThisSession = true;
        Minecraft.getInstance().setScreen(new DlcRequiredDownloadScreen(parent, collectMissingDownloads()));
        return true;
    }

    public static void markPrompted() {
        promptedThisSession = true;
    }

    public static void markSkipped() {
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
        Path requiredDir = getRequiredDir();
        if (requiredDir == null) {
            return false;
        }
        return Files.exists(requiredDir.resolve("SKIP"));
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
            String fileName = entry.getRequiredFileName();
            if (fileName == null || fileName.isBlank()) {
                fileName = resolveFileName(entry);
            }
            if (fileName == null || fileName.isBlank()) {
                fileName = resolveExistingRequiredFile(entry);
            }
            entry.setResolvedFileName(fileName);
            if (fileName == null || fileName.isBlank()) {
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

    public static boolean hasMissingOrApply() {
        List<DlcRequiredEntry> entries = loadAllEntries();
        for (DlcRequiredEntry entry : entries) {
            if (entry == null || !entry.isInList()) {
                continue;
            }
            String fileName = entry.getRequiredFileName();
            if (fileName == null || fileName.isBlank()) {
                fileName = resolveFileName(entry);
            }
            entry.setResolvedFileName(fileName);
            if (fileName == null || fileName.isBlank()) {
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
            String fileName = entry.getRequiredFileName();
            if (fileName == null || fileName.isBlank()) {
                fileName = resolveFileName(entry);
            }
            if (fileName == null || fileName.isBlank()) {
                fileName = resolveExistingRequiredFile(entry);
            }
            entry.setResolvedFileName(fileName);
            if (fileName == null || fileName.isBlank()) {
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

    public static List<DlcRequiredEntry> collectMissingOrApply() {
        List<DlcRequiredEntry> list = new ArrayList<>();
        List<DlcRequiredEntry> entries = loadAllEntries();
        for (DlcRequiredEntry entry : entries) {
            if (entry == null || !entry.isInList()) {
                continue;
            }
            String fileName = entry.getRequiredFileName();
            if (fileName == null || fileName.isBlank()) {
                fileName = resolveFileName(entry);
            }
            entry.setResolvedFileName(fileName);
            if (fileName == null || fileName.isBlank()) {
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
        String fileName = entry.getResolvedFileName();
        if (fileName == null || fileName.isBlank()) {
            fileName = guessFileName(entry);
            if (fileName == null || fileName.isBlank()) {
                String versionId = entry.getModrinthVersionId();
                if (versionId != null && !versionId.isBlank()) {
                    try {
                        ModrinthClient.ModrinthVersion version = ModrinthClient.fetchVersion(versionId);
                        if (version != null && version.fileName != null && !version.fileName.isBlank()) {
                            fileName = version.fileName;
                        }
                    } catch (Exception ignored) {
                        // Best-effort.
                    }
                }
            }
            entry.setResolvedFileName(fileName);
        }
        if (fileName == null || fileName.isBlank()) {
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
            String name = DlcRequiredDownloadScreen.extractFileNameFromUrl(directUrl);
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return null;
    }

    private static String resolveFileName(DlcRequiredEntry entry) {
        String fileName = guessFileName(entry);
        if (fileName != null && !fileName.isBlank()) {
            return fileName;
        }
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
                if (!name.endsWith(".json")) {
                    return name;
                }
            }
        } catch (Exception ignored) {
            // Best-effort.
        }
        return null;
    }

    public static void ensureFileNameKey(DlcRequiredEntry entry, String fileName) {
        if (entry == null || fileName == null || fileName.isBlank()) {
            return;
        }
        if (entry.getRequiredFileName() != null && !entry.getRequiredFileName().isBlank()) {
            return;
        }
        Path config = entry.getConfigPath();
        if (config == null || !Files.exists(config)) {
            return;
        }
        try (java.io.Reader reader = Files.newBufferedReader(config)) {
            com.google.gson.JsonObject obj = GSON.fromJson(reader, com.google.gson.JsonObject.class);
            if (obj == null) {
                return;
            }
            com.google.gson.JsonObject reordered = new com.google.gson.JsonObject();
            boolean inserted = false;
            for (var entryObj : obj.entrySet()) {
                String key = entryObj.getKey();
                if (!inserted && ("appliedTarget".equals(key) || "appliedTaget".equals(key))) {
                    reordered.addProperty("file_name", fileName);
                    inserted = true;
                }
                reordered.add(key, entryObj.getValue());
            }
            if (!inserted) {
                reordered.addProperty("file_name", fileName);
            }
            try (java.io.Writer writer = Files.newBufferedWriter(config)) {
                GSON.toJson(reordered, writer);
            }
            entry.setRequiredFileName(fileName);
        } catch (Exception ignored) {
            // Best-effort.
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
}
