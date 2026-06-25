package com.cozooo.dlc_manager.core;

import com.cozooo.dlc_manager.client.remote.DlcRemoteEntry;
import com.cozooo.dlc_manager.client.remote.DlcRemoteLoader;
import com.cozooo.dlc_manager.client.remote.DlcRedirectEntry;
import com.cozooo.dlc_manager.client.remote.DlcRedirectLoader;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.platform.CrossPlatformRuntime;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class DlcScanner {
    public Map<DlcType, List<DlcItem>> scan() {
        return scan(false);
    }

    public Map<DlcType, List<DlcItem>> scan(boolean detectInstalledState) {
        Map<DlcType, List<DlcItem>> grouped = new EnumMap<>(DlcType.class);
        for (DlcType type : DlcType.values()) {
            grouped.put(type, new ArrayList<>());
        }

        List<Path> roots = DlcConfig.getDlcRoots();
        List<Path> taczRoots = DlcConfig.getTaczDlcRoots();
        DlcTooltipLoader tooltipLoader = new DlcTooltipLoader();
        Path mainDlcRoot = DlcConfig.getDlcRoots().isEmpty() ? null : DlcConfig.getDlcRoots().get(0);
        Path mainInfoDir = mainDlcRoot == null ? null : mainDlcRoot.resolve("info");
        Map<String, DlcTooltipLoader.TooltipData> tooltips = mainDlcRoot == null
            ? java.util.Map.of()
            : tooltipLoader.loadTooltips(mainDlcRoot);
        Set<Path> seen = new HashSet<>();
        for (Path root : roots) {
            if (!Files.exists(root) || seen.contains(root)) {
                continue;
            }
            seen.add(root);
            scanRoot(grouped, root, taczRoots, tooltips, tooltipLoader, mainInfoDir, detectInstalledState);
            // 加载重定向项（爱发电、QQ频道等）
            scanRedirects(grouped, root);
            // 解压并扫描 update 包中的内容
            scanUpdatePackages(grouped, root, taczRoots, tooltips, tooltipLoader, mainInfoDir, detectInstalledState);
        }

        if (CrossPlatformRuntime.isModLoaded("tacz")) {
            scanTaczRoots(grouped, tooltipLoader, detectInstalledState);
        }

        return grouped;
    }

    private void scanRedirects(Map<DlcType, List<DlcItem>> grouped, Path root) {
        if (root == null) {
            return;
        }
        List<DlcRedirectEntry> entries = DlcRedirectLoader.load(root);
        for (DlcRedirectEntry entry : entries) {
            if (entry == null || entry.isHidden()) {
                continue;
            }
            String displayName = entry.resolveDisplayName("zh_cn");
            if (displayName == null || displayName.isBlank()) {
                displayName = entry.getFallbackName();
            }
            String id = "redirect:" + root.getFileName() + "|" + entry.getFallbackName();
            java.util.Map<String, String> displayNames = new java.util.HashMap<>();
            displayNames.put("zh_cn", displayName);
            java.util.Map<String, String> tips = new java.util.HashMap<>();
            tips.put("zh_cn", entry.resolveTip("zh_cn"));
            DlcItem item = new DlcItem(
                id,
                entry.getFallbackName(),
                entry.getFallbackName(),
                DlcType.UNKNOWN,
                entry.getConfigPath(),
                entry.getRedirectUrl(),
                true,
                tips,
                displayNames,
                java.util.List.of(),
                java.util.List.of(),
                true,
                false,
                false,
                false
            );
            item.setRedirectUrl(entry.getRedirectUrl());
            grouped.get(DlcType.UNKNOWN).add(item);
        }
    }

    private void scanUpdatePackages(Map<DlcType, List<DlcItem>> grouped, Path root, List<Path> taczRoots,
        Map<String, DlcTooltipLoader.TooltipData> tooltips, DlcTooltipLoader tooltipLoader, Path infoDir, boolean detectInstalledState) {
        if (root == null) {
            return;
        }
        Path updateDir = root.resolve("update");
        if (!Files.isDirectory(updateDir)) {
            return;
        }
        try (var stream = Files.list(updateDir)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName() != null && path.getFileName().toString().toLowerCase().endsWith(".zip"))
                .forEach(zipPath -> {
                    Path staging = extractUpdateZip(zipPath);
                    if (staging != null) {
                        try {
                            scanRoot(grouped, staging, taczRoots, tooltips, tooltipLoader, infoDir, detectInstalledState);
                        } finally {
                            deleteRecursive(staging);
                        }
                    }
                });
        } catch (IOException ignored) {
        }
    }

    private Path extractUpdateZip(Path zipPath) {
        Path tmpDir = null;
        try {
            tmpDir = Files.createTempDirectory("dlc-update-scan-");
            try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
                var entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) {
                        continue;
                    }
                    Path target = tmpDir.resolve(entry.getName()).normalize();
                    if (!target.startsWith(tmpDir)) {
                        continue;
                    }
                    Files.createDirectories(target.getParent());
                    try (var in = zipFile.getInputStream(entry)) {
                        Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            return tmpDir;
        } catch (IOException e) {
            if (tmpDir != null) {
                deleteRecursive(tmpDir);
            }
            return null;
        }
    }

    private void deleteRecursive(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }
    }

    private void scanRoot(Map<DlcType, List<DlcItem>> grouped, Path root, List<Path> taczRoots,
        Map<String, DlcTooltipLoader.TooltipData> tooltips, DlcTooltipLoader tooltipLoader, Path infoDir, boolean detectInstalledState) {
        Path localInfoDir = root.resolve("info");
        Path localRequiredDir = root.resolve("required");
        Path localRemoteDir = root.resolve("remote");
        Path localPresetDir = root.resolve("preset");
        Path localUpdateDir = root.resolve("update");
        Path localTmpDir = DlcTmpPaths.root(root);
        Path localLegacyTmpDir = DlcTmpPaths.legacyRoot(root);
        Map<String, String> groupedOwners = buildGroupedOwners(tooltips);
        RemoteMetadataLookup remoteMetadata = buildRemoteMetadata(root);
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (root.equals(dir)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (isUnderInfoDir(dir, infoDir) || isUnderInfoDir(dir, localInfoDir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (isUnderRequiredDir(dir, localRequiredDir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (isUnderRemoteDir(dir, localRemoteDir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (isUnderPresetDir(dir, localPresetDir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (isUnderUpdateDir(dir, localUpdateDir) || isUnderUpdateDir(dir, localTmpDir) || isUnderUpdateDir(dir, localLegacyTmpDir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (isUnderTaczRoot(dir, taczRoots)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    DlcType type = classifyDirectory(dir);
                    if (type != null) {
                        addItem(grouped, root, dir, type, tooltips, remoteMetadata, tooltipLoader, infoDir, detectInstalledState);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (isUnderInfoDir(file, infoDir) || isUnderInfoDir(file, localInfoDir)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (isUnderRequiredDir(file, localRequiredDir)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (isUnderRemoteDir(file, localRemoteDir)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (isUnderPresetDir(file, localPresetDir)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (isUnderUpdateDir(file, localUpdateDir) || isUnderUpdateDir(file, localTmpDir) || isUnderUpdateDir(file, localLegacyTmpDir)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (isUnderTaczRoot(file, taczRoots)) {
                        return FileVisitResult.CONTINUE;
                    }
                    String name = file.getFileName().toString();
                    String relative = root.relativize(file).toString().replace('\\', '/');
                    String owner = groupedOwners.get(relative);
                    if (owner != null && !owner.equals(name)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (name.endsWith(".jar")) {
                        addItem(grouped, root, file, DlcType.MOD, tooltips, remoteMetadata, tooltipLoader, infoDir, detectInstalledState);
                        return FileVisitResult.CONTINUE;
                    }
                    if (name.endsWith(".zip")) {
                        DlcType type = classifyZip(file);
                        addItem(grouped, root, file, type != null ? type : DlcType.UNKNOWN, tooltips, remoteMetadata, tooltipLoader, infoDir, detectInstalledState);
                        return FileVisitResult.CONTINUE;
                    }
                    if ((tooltips != null && tooltips.containsKey(name)) || remoteMetadata.contains(name)) {
                        addItem(grouped, root, file, DlcType.UNKNOWN, tooltips, remoteMetadata, tooltipLoader, infoDir, detectInstalledState);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }
    }

    private boolean isUnderTaczRoot(Path path, List<Path> taczRoots) {
        Path normalized = path.normalize();
        for (Path taczRoot : taczRoots) {
            Path root = taczRoot.normalize();
            if (normalized.startsWith(root)) {
                return true;
            }
        }
        return false;
    }

    private boolean isUnderInfoDir(Path path, Path infoDir) {
        if (infoDir == null) {
            return false;
        }
        Path normalized = path.normalize();
        Path normalizedInfo = infoDir.normalize();
        return normalized.startsWith(normalizedInfo);
    }

    private boolean isUnderRequiredDir(Path path, Path requiredDir) {
        if (requiredDir == null) {
            return false;
        }
        Path normalized = path.normalize();
        Path normalizedRequired = requiredDir.normalize();
        return normalized.startsWith(normalizedRequired);
    }

    private boolean isUnderUpdateDir(Path path, Path updateDir) {
        if (updateDir == null) {
            return false;
        }
        Path normalized = path.normalize();
        Path normalizedUpdate = updateDir.normalize();
        return normalized.startsWith(normalizedUpdate);
    }

    private boolean isUnderRemoteDir(Path path, Path remoteDir) {
        if (remoteDir == null) {
            return false;
        }
        Path normalized = path.normalize();
        Path normalizedRemote = remoteDir.normalize();
        return normalized.startsWith(normalizedRemote);
    }

    private boolean isUnderPresetDir(Path path, Path presetDir) {
        if (presetDir == null) {
            return false;
        }
        Path normalized = path.normalize();
        Path normalizedPreset = presetDir.normalize();
        return normalized.startsWith(normalizedPreset);
    }

    private void addItem(Map<DlcType, List<DlcItem>> grouped, Path root, Path source, DlcType detectedType,
        Map<String, DlcTooltipLoader.TooltipData> tooltips, RemoteMetadataLookup remoteMetadata,
        DlcTooltipLoader tooltipLoader, Path infoDir, boolean detectInstalledState) {
        String relative = root.relativize(source).toString().replace('\\', '/');
        String rootId = DlcPathUtil.toGameRelativePath(root).replace('\\', '/');
        String name = source.getFileName().toString();
        DlcTooltipLoader.TooltipData tooltipData = remoteMetadata.get(name);
        if (tooltipData == null && tooltips != null) {
            tooltipData = tooltips.get(name);
        }
        java.util.List<String> fileNames = tooltipData != null ? tooltipData.getFileNames() : java.util.List.of();
        if (!fileNames.isEmpty() && fileNames.stream().noneMatch(name::equals)) {
            java.util.List<String> merged = new java.util.ArrayList<>(fileNames);
            merged.add(0, name);
            fileNames = merged;
        }
        DlcType effectiveType = detectedType;
        boolean hadTypeField = false;
        boolean typeValid = false;
        if (tooltipData != null) {
            hadTypeField = tooltipData.hasTypeField();
            typeValid = tooltipData.isTypeValid();
            if (tooltipData.isTypeValid() && tooltipData.getType() != null) {
                effectiveType = tooltipData.getType();
            }
        }
        if (detectedType == DlcType.TACZ_PACK && effectiveType != DlcType.TACZ_PACK) {
            effectiveType = detectedType;
            typeValid = false;
        }
        String id = effectiveType.name().toLowerCase() + ":" + rootId + "|" + relative;
        String identifier = tooltipData != null ? tooltipData.getIdentifier() : null;
        if (identifier == null || identifier.isBlank()) {
            identifier = name;
        }
        String appliedTargetRaw = tooltipData != null ? tooltipData.getAppliedTargetRaw() : null;
        boolean configType = tooltipData != null && tooltipData.isConfigType();
        if (effectiveType == DlcType.UNKNOWN && (appliedTargetRaw == null || appliedTargetRaw.isBlank())) {
            return;
        }
        boolean enabled = detectInstalledState
            ? isEnabledInTargets(id, name, fileNames, effectiveType, appliedTargetRaw, source, configType)
            : isEnabledInState(id);
        Map<String, String> tips = tooltipData != null ? tooltipData.getTips() : null;
        boolean inList = tooltipData == null || tooltipData.isInList();
        boolean hidden = tooltipData != null && tooltipData.isHidden();
        Map<String, String> displayNames = tooltipData != null ? tooltipData.getDisplayName() : null;
        if (displayNames == null || displayNames.isEmpty()) {
            displayNames = java.util.Map.of("en_us", name);
        }
        if (!inList && !hidden) {
            return;
        }
        java.util.List<String> dependencies = tooltipData != null ? tooltipData.getDependencies() : java.util.List.of();
        java.util.List<String> conflicts = tooltipData != null ? tooltipData.getConflicts() : java.util.List.of();
        boolean alarm = tooltipData != null && tooltipData.isAlarm();
        boolean allowEnableByAll = tooltipData == null || tooltipData.AllowEnableByAll();
        List<Path> sources = new java.util.ArrayList<>();
        if (!fileNames.isEmpty() && effectiveType == DlcType.UNKNOWN) {
            for (String entry : fileNames) {
                if (entry == null || entry.isBlank()) {
                    continue;
                }
                Path resolved = root.resolve(entry.replace('\\', '/'));
                sources.add(resolved);
            }
        }
        DlcItem item = sources.isEmpty()
            ? new DlcItem(id, identifier, name, effectiveType, source, appliedTargetRaw, enabled, tips, displayNames, dependencies, conflicts, allowEnableByAll, hidden, configType, alarm)
            : new DlcItem(id, identifier, name, effectiveType, source, sources, appliedTargetRaw, enabled, tips, displayNames, dependencies, conflicts, allowEnableByAll, hidden, configType, alarm);
        if (tooltipData != null) {
            item.setRedirectUrl(tooltipData.getRedirectUrl());
        }
        if (detectInstalledState && enabled) {
            item.getAppliedTargets().addAll(detectAppliedTargets(id, name, fileNames, effectiveType, appliedTargetRaw, source, configType));
        }
        grouped.get(effectiveType).add(item);
        // if (tooltipLoader != null && infoDir != null) {
        //     boolean updateType = !typeValid || !hadTypeField;
        //     tooltipLoader.ensureTooltipFile(infoDir, name, effectiveType, updateType);
        // }
    }

    private boolean isEnabledInTargets(String itemId, String fileName, java.util.List<String> fileNames, DlcType type, String appliedTargetRaw, Path source, boolean configType) {
        if (type == DlcType.UNKNOWN) {
            Path target = DlcPaths.resolveAppliedTarget(appliedTargetRaw);
            if (target == null) {
                return false;
            }
            boolean dirTarget = DlcPaths.isAppliedTargetDirectory(appliedTargetRaw, target, source);
            if (fileNames != null && !fileNames.isEmpty()) {
                if (!dirTarget && fileNames.size() > 1) {
                    return false;
                }
                for (String entry : fileNames) {
                    if (entry == null || entry.isBlank()) {
                        continue;
                    }
                    String normalized = entry.replace('\\', '/');
                    Path check = dirTarget ? target.resolve(normalized) : target;
                    if (configType) {
                        if (!DlcConfigTypeSupport.hasBackupForItemTarget(itemId, check)
                            && !DlcConfigTypeSupport.hasBackupForTarget(check)) {
                            return false;
                        }
                    } else if (!Files.exists(check)) {
                        return false;
                    }
                    if (!dirTarget) {
                        break;
                    }
                }
                return true;
            }
            if (dirTarget) {
                Path check = target.resolve(fileName);
                return configType
                    ? DlcConfigTypeSupport.hasBackupForItemTarget(itemId, check) || DlcConfigTypeSupport.hasBackupForTarget(check)
                    : Files.exists(check);
            }
            return configType
                ? DlcConfigTypeSupport.hasBackupForItemTarget(itemId, target) || DlcConfigTypeSupport.hasBackupForTarget(target)
                : Files.exists(target);
        }
        List<Path> targetDirs = switch (type) {
            case MOD -> List.of(DlcPaths.modsDir());
            case RESOURCE_PACK -> DlcConfig.getResourcepackRoots();
            case DATA_PACK -> DlcConfig.getDatapackRoots();
            case TACZ_PACK -> List.of(DlcPaths.taczDir());
            default -> List.of();
        };
        if (type == DlcType.RESOURCE_PACK) {
            return isResourcePackSelected(fileName);
        }
        if (targetDirs.isEmpty()) {
            return false;
        }
        for (Path dir : targetDirs) {
            if (!Files.exists(dir.resolve(fileName))) {
                return false;
            }
        }
        return true;
    }

    private boolean isEnabledInState(String id) {
        DlcStateConfig.DlcState state = DlcStateConfig.getState(id);
        return state != null && state.enabled;
    }

    private List<Path> detectAppliedTargets(String itemId, String fileName, java.util.List<String> fileNames, DlcType type, String appliedTargetRaw, Path source, boolean configType) {
        if (type == DlcType.UNKNOWN) {
            Path target = DlcPaths.resolveAppliedTarget(appliedTargetRaw);
            if (target == null) {
                return List.of();
            }
            boolean dirTarget = DlcPaths.isAppliedTargetDirectory(appliedTargetRaw, target, source);
            if (fileNames != null && !fileNames.isEmpty()) {
                List<Path> result = new ArrayList<>();
                if (!dirTarget && fileNames.size() > 1) {
                    return List.of();
                }
                for (String entry : fileNames) {
                    if (entry == null || entry.isBlank()) {
                        continue;
                    }
                    String normalized = entry.replace('\\', '/');
                    Path check = dirTarget ? target.resolve(normalized) : target;
                    if (configType) {
                        Path stored = detectConfigStatePath(itemId, check);
                        if (stored == null) {
                            return List.of();
                        }
                        result.add(stored);
                    } else {
                        if (!Files.exists(check)) {
                            return List.of();
                        }
                        result.add(check);
                    }
                    if (!dirTarget) {
                        break;
                    }
                }
                return result;
            }
            Path check = dirTarget ? target.resolve(fileName) : target;
            if (configType) {
                Path stored = detectConfigStatePath(itemId, check);
                return stored == null ? List.of() : List.of(stored);
            }
            return Files.exists(check) ? List.of(check) : List.of();
        }
        List<Path> targetDirs = switch (type) {
            case MOD -> List.of(DlcPaths.modsDir());
            case RESOURCE_PACK -> DlcConfig.getResourcepackRoots();
            case DATA_PACK -> DlcConfig.getDatapackRoots();
            case TACZ_PACK -> List.of(DlcPaths.taczDir());
            default -> List.of();
        };
        if (targetDirs.isEmpty()) {
            return List.of();
        }
        List<Path> result = new ArrayList<>();
        for (Path dir : targetDirs) {
            Path check = dir.resolve(fileName);
            if (!Files.exists(check)) {
                return List.of();
            }
            result.add(check);
        }
        return result;
    }

    private Path detectConfigStatePath(String itemId, Path target) {
        Path backup = DlcConfigTypeSupport.backupPathForItemTarget(itemId, target);
        Path marker = DlcConfigTypeSupport.missingMarkerPath(backup);
        if (Files.exists(marker)) {
            return marker;
        }
        if (Files.exists(backup)) {
            return backup;
        }
        Path legacyBackup = DlcConfigTypeSupport.backupPathForTarget(target);
        Path legacyMarker = DlcConfigTypeSupport.missingMarkerPath(legacyBackup);
        if (Files.exists(legacyMarker)) {
            return legacyMarker;
        }
        if (Files.exists(legacyBackup)) {
            return legacyBackup;
        }
        return null;
    }

    private boolean isResourcePackSelected(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        // In-game, this checks Minecraft's resource-pack repository for whether
        // the player has actually toggled the pack on.  Without a running game we
        // approximate the same decision by reading options.txt's "resourcePacks"
        // array (the persistence of the in-game selection).
        Set<String> selected = selectedResourcePackIds();
        if (selected != null) {
            String stripped = stripArchiveExtension(fileName);
            // Pack ids in options.txt are "<source>/<file>" (e.g. "file/Foo.zip").
            return selected.contains("file/" + fileName)
                    || selected.contains("file/" + stripped)
                    || selected.contains("vanilla/" + fileName)
                    || selected.contains("vanilla/" + stripped);
        }
        // options.txt absent/unparseable (e.g. dedicated-server game dir, fresh
        // install): fall back to file-existence as a best-effort heuristic.
        for (Path dir : DlcConfig.getResourcepackRoots()) {
            if (Files.exists(dir.resolve(fileName))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cached set of selected resource-pack ids parsed from
     * {@code <gameDir>/options.txt}.  Returns {@code null} when options.txt is
     * absent or unparseable (caller falls back to file-existence).  Cached per
     * game directory so repeated scans don't re-read the file.
     */
    private static volatile Path cachedOptionsGameDir;
    private static volatile Set<String> cachedSelectedPackIds;

    private static Set<String> selectedResourcePackIds() {
        Path gameDir = CrossPlatformRuntime.gameDir();
        if (gameDir == null) {
            return null;
        }
        Path current = cachedOptionsGameDir;
        if (current != null && current.equals(gameDir)) {
            return cachedSelectedPackIds;
        }
        Set<String> ids = loadSelectedResourcePackIds(gameDir);
        cachedOptionsGameDir = gameDir;
        cachedSelectedPackIds = ids;
        return ids;
    }

    private static Set<String> loadSelectedResourcePackIds(Path gameDir) {
        Path options = gameDir.resolve("options.txt");
        if (!Files.exists(options)) {
            return null;
        }
        try {
            for (String line : Files.readAllLines(options)) {
                if (!line.startsWith("resourcePacks:")) {
                    continue;
                }
                String value = line.substring("resourcePacks:".length()).trim();
                if (value.isEmpty() || value.equals("[]")) {
                    return Set.of();
                }
                JsonElement parsed = JsonParser.parseString(value);
                if (!parsed.isJsonArray()) {
                    return null;
                }
                JsonArray arr = parsed.getAsJsonArray();
                Set<String> ids = new HashSet<>();
                for (JsonElement e : arr) {
                    if (e.isJsonPrimitive()) {
                        ids.add(e.getAsString());
                    }
                }
                return ids;
            }
            return null; // no resourcePacks line → no reliable info
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String stripArchiveExtension(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".zip") || lower.endsWith(".jar")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }

    private Map<String, String> buildGroupedOwners(Map<String, DlcTooltipLoader.TooltipData> tooltips) {
        Map<String, String> owners = new java.util.HashMap<>();
        if (tooltips == null || tooltips.isEmpty()) {
            return owners;
        }
        for (Map.Entry<String, DlcTooltipLoader.TooltipData> entry : tooltips.entrySet()) {
            String primary = entry.getKey();
            if (primary == null || primary.isBlank()) {
                continue;
            }
            DlcTooltipLoader.TooltipData data = entry.getValue();
            if (data == null || data.getType() != DlcType.UNKNOWN) {
                continue;
            }
            java.util.List<String> fileNames = data.getFileNames();
            if (fileNames == null || fileNames.isEmpty()) {
                continue;
            }
            owners.put(primary, primary);
            for (String name : fileNames) {
                if (name == null || name.isBlank()) {
                    continue;
                }
                owners.put(name.replace('\\', '/'), primary);
            }
        }
        return owners;
    }

    private DlcType classifyDirectory(Path dir) {
        Path packMeta = dir.resolve("pack.mcmeta");
        boolean hasPack = Files.exists(packMeta);
        boolean hasAssets = Files.exists(dir.resolve("assets"));
        boolean hasData = Files.exists(dir.resolve("data"));

        if (hasPack && (hasAssets || hasData)) {
            if (hasAssets && !hasData) {
                return DlcType.RESOURCE_PACK;
            }
            if (hasData && !hasAssets) {
                return DlcType.DATA_PACK;
            }
            return DlcType.RESOURCE_PACK;
        }

        return null;
    }

    private DlcType classifyZip(Path zipPath) {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            boolean hasPack = zipFile.getEntry("pack.mcmeta") != null;
            boolean hasAssets = hasZipPrefix(zipFile, "assets/");
            boolean hasData = hasZipPrefix(zipFile, "data/");

            if (hasPack && (hasAssets || hasData)) {
                if (hasAssets && !hasData) {
                    return DlcType.RESOURCE_PACK;
                }
                if (hasData && !hasAssets) {
                    return DlcType.DATA_PACK;
                }
                return DlcType.RESOURCE_PACK;
            }
        } catch (IOException ignored) {
            return null;
        }
        return null;
    }

    private boolean hasZipPrefix(ZipFile zipFile, String prefix) {
        return zipFile.stream().map(ZipEntry::getName).anyMatch(name -> name.startsWith(prefix));
    }

    private void scanTaczRoots(Map<DlcType, List<DlcItem>> grouped, DlcTooltipLoader tooltipLoader, boolean detectInstalledState) {
        Path mainDlcRoot = DlcConfig.getDlcRoots().isEmpty() ? null : DlcConfig.getDlcRoots().get(0);
        Path mainInfoDir = mainDlcRoot == null ? null : mainDlcRoot.resolve("info");
        Map<String, DlcTooltipLoader.TooltipData> tooltips = mainDlcRoot == null
            ? java.util.Map.of()
            : tooltipLoader.loadTooltips(mainDlcRoot);

        for (Path root : DlcConfig.getTaczDlcRoots()) {
            if (!Files.exists(root) || !Files.isDirectory(root)) {
                continue;
            }
            try (var stream = Files.list(root)) {
                stream.forEach(path -> {
                    String name = path.getFileName().toString();
                    if (Files.isDirectory(path)) {
                        addItem(grouped, root, path, DlcType.TACZ_PACK, java.util.Map.of(), RemoteMetadataLookup.empty(), tooltipLoader, mainInfoDir, detectInstalledState);
                    } else if (name.endsWith(".zip")) {
                        addItem(grouped, root, path, DlcType.TACZ_PACK, java.util.Map.of(), RemoteMetadataLookup.empty(), tooltipLoader, mainInfoDir, detectInstalledState);
                    }
                });
            } catch (IOException ignored) {
            }
        }
    }

    private RemoteMetadataLookup buildRemoteMetadata(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return RemoteMetadataLookup.empty();
        }
        List<DlcRemoteEntry> entries = DlcRemoteLoader.load(root);
        if (entries.isEmpty()) {
            return RemoteMetadataLookup.empty();
        }
        Map<String, DlcTooltipLoader.TooltipData> byFileName = new java.util.HashMap<>();
        java.util.Set<String> ambiguousFileNames = new java.util.HashSet<>();
        for (DlcRemoteEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            String fileName = entry.getFileNameHint();
            if (fileName == null || fileName.isBlank()) {
                continue;
            }
            if (entry.getType() == DlcType.DLC_PACK) {
                continue;
            }
            String identifier = entry.getDlcIdentifier();
            if (identifier == null || identifier.isBlank()) {
                identifier = fileName;
            }
            DlcType type = entry.getType() == null ? DlcType.MOD : entry.getType();
            DlcTooltipLoader.TooltipData data = new DlcTooltipLoader.TooltipData(
                identifier,
                entry.getTips(),
                entry.getDisplayNames(),
                type,
                true,
                true,
                entry.isConfigType(),
                entry.isInList(),
                entry.isHidden(),
                entry.isAllowEnableByAll(),
                entry.getResolvedDependencyNames(),
                entry.getConflictNames(),
                entry.getAppliedTargetRaw(),
                entry.getRedirectUrl(),
                java.util.List.of(),
                entry.isAlarm()
            );
            if (ambiguousFileNames.contains(fileName)) {
                continue;
            }
            DlcTooltipLoader.TooltipData existing = byFileName.get(fileName);
            if (existing == null) {
                byFileName.put(fileName, data);
            } else {
                byFileName.remove(fileName);
                ambiguousFileNames.add(fileName);
            }
        }
        return new RemoteMetadataLookup(byFileName);
    }

    private static final class RemoteMetadataLookup {
        private final Map<String, DlcTooltipLoader.TooltipData> byFileName;

        private RemoteMetadataLookup(Map<String, DlcTooltipLoader.TooltipData> byFileName) {
            this.byFileName = byFileName == null ? java.util.Map.of() : byFileName;
        }

        private static RemoteMetadataLookup empty() {
            return new RemoteMetadataLookup(java.util.Map.of());
        }

        private DlcTooltipLoader.TooltipData get(String fileName) {
            if (fileName == null || fileName.isBlank()) {
                return null;
            }
            return byFileName.get(fileName);
        }

        private boolean contains(String fileName) {
            return get(fileName) != null;
        }
    }

}
