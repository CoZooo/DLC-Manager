package com.cozooo.dlc_manager.client.required;

import com.cozooo.dlc_manager.client.download.DlcDownloadCandidate;
import com.cozooo.dlc_manager.client.download.DlcDownloadUtil;
import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.cozooo.dlc_manager.core.DlcDcFormatUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads {@link DlcRequiredEntry} instances from {@code <dlcRoot>/required/*.dc}.
 * <p>
 * Ported from {@code common/.../client/required/DlcRequiredLoader.kt}.  Only the
 * reading half of the Kotlin object is ported here: the in-game loader also
 * performs an on-disk "key migration" rewrite (normalising legacy keys and the
 * {@code [basic]} layout) as a side effect of scanning.  That rewrite is a
 * mutating optimisation tied to the running game; the standalone JavaFX manager
 * is a read/inspect/repair tool, so this loader stays non-mutating — it parses
 * with {@link DlcDcFormatUtil#read} and never writes back.
 * <p>
 * The standalone-relevant detection helpers from
 * {@code DlcRequiredRuntime.kt} (missing-download / needs-apply) are folded in
 * here as static methods so the UI has a single entry point for Required state.
 */
public final class DlcRequiredLoader {

    private DlcRequiredLoader() {
    }

    // ───────────────────────────────────────────────────────────────
    //  Loading
    // ───────────────────────────────────────────────────────────────

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
                if (!DlcDcFormatUtil.isDcName(fileName)) {
                    continue;
                }
                if (isNoticeConfig(fileName)) {
                    continue;
                }
                DlcRequiredEntry entry = readEntry(path, requiredDir);
                if (entry != null) {
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
        Path resolved = resolveExistingConfigPath(configPath);
        if (resolved == null || !Files.exists(resolved)) {
            return null;
        }
        return readEntry(resolved, requiredDir);
    }

    /**
     * Expand a list of "root" required entries with their transitive
     * dependencies, mirroring {@code DlcRequiredRuntime.collectDependencies}.
     * Inline dependency entries (declared nested in a parent's
     * {@code dependencies} block) are pulled in from the parents themselves.
     */
    public static List<DlcRequiredEntry> collectDependencies(List<DlcRequiredEntry> roots, List<DlcRequiredEntry> all) {
        List<DlcRequiredEntry> result = new ArrayList<>();
        if (roots == null || roots.isEmpty()) {
            return result;
        }
        Map<String, DlcRequiredEntry> index = new HashMap<>();
        if (all != null) {
            for (DlcRequiredEntry e : all) {
                if (e == null) {
                    continue;
                }
                String id = e.getIdentifier();
                if (id != null && !id.isBlank()) {
                    index.putIfAbsent(id, e);
                }
            }
        }
        java.util.ArrayDeque<DlcRequiredEntry> queue = new java.util.ArrayDeque<>(roots);
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        while (!queue.isEmpty()) {
            DlcRequiredEntry entry = queue.poll();
            if (entry == null) {
                continue;
            }
            String id = entry.getIdentifier();
            if (id == null || id.isBlank() || !seen.add(id)) {
                continue;
            }
            DlcRequiredEntry resolved = index.getOrDefault(id, entry);
            result.add(resolved);
            for (DlcRequiredDependency dep : resolved.getDependencies()) {
                if (dep == null) {
                    continue;
                }
                String depId = dep.getIdentifier();
                if (depId == null || depId.isBlank()) {
                    continue;
                }
                DlcRequiredEntry depEntry = index.get(depId);
                if (depEntry != null) {
                    queue.add(depEntry);
                } else if (dep.getEntry() != null) {
                    queue.add(dep.getEntry());
                }
            }
        }
        return result;
    }

    // ───────────────────────────────────────────────────────────────
    //  Reading
    // ───────────────────────────────────────────────────────────────

    private static DlcRequiredEntry readEntry(Path path, Path requiredDir) {
        JsonObject obj = readConfigObject(path);
        if (obj == null) {
            return null;
        }
        return fromJsonObject(obj, requiredDir, path, null);
    }

    private static JsonObject readConfigObject(Path path) {
        if (path == null || path.getFileName() == null) {
            return null;
        }
        if (!DlcDcFormatUtil.isDcName(path.getFileName().toString())) {
            return null;
        }
        return DlcDcFormatUtil.read(path);
    }

    private static Path resolveExistingConfigPath(Path configPath) {
        if (configPath == null || configPath.getFileName() == null) {
            return configPath;
        }
        if (Files.exists(configPath)) {
            return configPath;
        }
        String name = configPath.getFileName().toString();
        String base = DlcDcFormatUtil.stripConfigExtension(name);
        Path parent = configPath.getParent();
        if (parent == null) {
            return configPath;
        }
        Path dc = parent.resolve(base + DlcDcFormatUtil.DC_EXTENSION);
        if (Files.exists(dc)) {
            return dc;
        }
        return configPath;
    }

    private static boolean isNoticeConfig(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        return "notice".equalsIgnoreCase(DlcDcFormatUtil.stripConfigExtension(fileName));
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
        String priority = DlcDownloadCandidate.readPriority(obj);
        if (priority != null && !priority.isBlank()) {
            entry.setDownloadPriority(priority);
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
        String applied = getString(obj, "appliedTarget");
        entry.setAppliedTargetRaw(applied == null ? "" : applied);
        entry.setRedirectUrl(getString(obj, "RedirectUrl"));
        JsonElement depsElement = DlcDcConfigIO.getIgnoreCase(obj, "dependencies");
        if (depsElement != null) {
            parseDependencies(depsElement, entry, requiredDir);
        }
        return entry;
    }

    private static void readDisplayNames(JsonObject obj, DlcRequiredEntry entry) {
        if (obj == null || entry == null) {
            return;
        }
        JsonObject display = DlcDcConfigIO.getObjectIgnoreCase(obj, "displayName");
        if (display == null) {
            display = DlcDcConfigIO.getObjectIgnoreCase(obj, "DisplayName");
        }
        if (display == null) {
            String fallback = entry.getBaseName();
            if (fallback == null || fallback.isBlank()) {
                fallback = entry.getIdentifier();
            }
            entry.putDisplayName("en_us", fallback);
            return;
        }
        for (Map.Entry<String, JsonElement> e : display.entrySet()) {
            JsonElement value = e.getValue();
            if (value != null && value.isJsonPrimitive()) {
                String lang = DlcDcConfigIO.normalizeLangKey(e.getKey());
                if (lang != null) {
                    entry.putDisplayName(lang, value.getAsString());
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
        JsonObject tipObj = DlcDcConfigIO.getObjectIgnoreCase(obj, "tip");
        if (tipObj == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : tipObj.entrySet()) {
            JsonElement value = e.getValue();
            if (value != null && value.isJsonPrimitive()) {
                String lang = DlcDcConfigIO.normalizeLangKey(e.getKey());
                if (lang != null) {
                    entry.putTip(lang, value.getAsString());
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
                    String id = part.trim();
                    if (!id.isBlank()) {
                        entry.addDependency(new DlcRequiredDependency(id, null));
                    }
                }
            }
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement value : element.getAsJsonArray()) {
                parseDependencyValue(null, value, entry, requiredDir);
            }
            return;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : element.getAsJsonObject().entrySet()) {
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
                    String id = part.trim();
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
        String depId = depEntry != null ? depEntry.getIdentifier() : null;
        if (depId == null || depId.isBlank()) {
            depId = identifier;
        }
        owner.addDependency(new DlcRequiredDependency(depId, depEntry));
    }

    private static List<String> getDirectUrls(JsonObject obj) {
        if (obj == null) {
            return List.of();
        }
        JsonElement element = DlcDcConfigIO.getIgnoreCase(obj, "directUrl");
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

    private static String getString(JsonObject obj, String key) {
        return DlcDcConfigIO.getStringIgnoreCase(obj, key);
    }

    private static Boolean getBoolean(JsonObject obj, String key) {
        JsonElement element = DlcDcConfigIO.getIgnoreCase(obj, key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        try {
            return element.getAsBoolean();
        } catch (Exception ignored) {
            return null;
        }
    }

    // ───────────────────────────────────────────────────────────────
    //  Detection (ported from DlcRequiredRuntime.kt)
    // ───────────────────────────────────────────────────────────────

    /**
     * Resolve the on-disk file name for an entry, falling back through the
     * configured name → URL hint → an existing matching file in the required
     * directory.  Mirrors {@code DlcRequiredRuntime.hasMissingDownloads}.
     */
    public static String resolveEffectiveFileName(DlcRequiredEntry entry) {
        if (entry == null) {
            return null;
        }
        String fileName = normalizeFileName(entry.getRequiredFileName());
        if (fileName == null) {
            fileName = normalizeFileName(guessFileName(entry));
        }
        if (fileName == null) {
            fileName = normalizeFileName(resolveExistingRequiredFile(entry));
        }
        if (isTempFileName(fileName)) {
            fileName = null;
        }
        entry.setResolvedFileName(fileName);
        return fileName;
    }

    /** True if the entry's file is present in the required directory. */
    public static boolean isDownloaded(DlcRequiredEntry entry) {
        String fileName = resolveEffectiveFileName(entry);
        if (fileName == null) {
            return false;
        }
        Path required = entry.resolveRequiredPath(fileName);
        return required != null && Files.exists(required);
    }

    /**
     * True if a downloaded entry still needs to be installed (copied) into its
     * applied-target directories.  Mirrors {@code DlcRequiredRuntime.entryNeedsApply}.
     */
    public static boolean needsApply(DlcRequiredEntry entry) {
        if (entry == null) {
            return false;
        }
        String fileName = normalizeFileName(entry.getResolvedFileName());
        if (fileName == null || fileName.isBlank()) {
            fileName = normalizeFileName(resolveEffectiveFileName(entry));
        }
        if (isTempFileName(fileName)) {
            return true;
        }
        if (fileName == null) {
            return true;
        }
        for (Path target : entry.resolveAppliedTargets()) {
            Path targetFile = resolveTarget(target, fileName);
            if (targetFile == null || !Files.exists(targetFile)) {
                return true;
            }
        }
        return false;
    }

    public static List<DlcRequiredEntry> collectMissingDownloads(List<DlcRequiredEntry> entries) {
        List<DlcRequiredEntry> missing = new ArrayList<>();
        if (entries == null) {
            return missing;
        }
        for (DlcRequiredEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            if (!isDownloaded(entry)) {
                missing.add(entry);
            }
        }
        return missing;
    }

    public static List<DlcRequiredEntry> collectNeedsApply(List<DlcRequiredEntry> entries) {
        List<DlcRequiredEntry> needs = new ArrayList<>();
        if (entries == null) {
            return needs;
        }
        for (DlcRequiredEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            if (isDownloaded(entry) && needsApply(entry)) {
                needs.add(entry);
            }
        }
        return needs;
    }

    private static String guessFileName(DlcRequiredEntry entry) {
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

    private static String resolveExistingRequiredFile(DlcRequiredEntry entry) {
        if (entry == null) {
            return null;
        }
        String base = entry.getBaseName();
        Path requiredDir = entry.getRequiredDir();
        if (requiredDir == null || base == null || base.isBlank()) {
            return null;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(requiredDir, base + ".*")) {
            for (Path path : stream) {
                String name = path.getFileName() == null ? "" : path.getFileName().toString();
                if (!DlcDcFormatUtil.isDcName(name) && !name.endsWith(".part")) {
                    return name;
                }
            }
        } catch (IOException ignored) {
        }
        return null;
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
        String trimmedRaw = DlcDownloadUtil.stripQuery(fileName);
        if (trimmedRaw == null) {
            return null;
        }
        String trimmed = trimmedRaw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (DlcDcFormatUtil.isDcName(trimmed)) {
            return null;
        }
        return trimmed.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static boolean isTempFileName(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".part");
    }
}
