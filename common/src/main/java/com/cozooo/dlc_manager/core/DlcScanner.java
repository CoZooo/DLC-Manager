package com.cozooo.dlc_manager.core;

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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class DlcScanner {
    public Map<DlcType, List<DlcItem>> scan() {
        Map<DlcType, List<DlcItem>> grouped = new EnumMap<>(DlcType.class);
        for (DlcType type : DlcType.values()) {
            grouped.put(type, new ArrayList<>());
        }

        List<Path> roots = DlcPathConfig.getDlcRoots();
        List<Path> taczRoots = DlcPathConfig.getTaczDlcRoots();
        DlcTooltipLoader tooltipLoader = new DlcTooltipLoader();
        Path mainDlcRoot = DlcPathConfig.getDlcRoots().isEmpty() ? null : DlcPathConfig.getDlcRoots().get(0);
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
            scanRoot(grouped, root, taczRoots, tooltips, tooltipLoader, mainInfoDir);
        }

        if (PlatformServices.get().isModLoaded("tacz")) {
            scanTaczRoots(grouped, tooltipLoader);
        }

        return grouped;
    }

    private void scanRoot(Map<DlcType, List<DlcItem>> grouped, Path root, List<Path> taczRoots,
        Map<String, DlcTooltipLoader.TooltipData> tooltips, DlcTooltipLoader tooltipLoader, Path infoDir) {
        Path localInfoDir = root.resolve("info");
        Path localRequiredDir = root.resolve("required");
        Path localRemoteDir = root.resolve("remote");
        Path localPresetDir = root.resolve("preset");
        Path localUpdateDir = root.resolve("update");
        Path localUpdateTmpDir = root.resolve("update_tmp");
        Map<String, String> groupedOwners = buildGroupedOwners(tooltips);
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
                    if (isUnderUpdateDir(dir, localUpdateDir) || isUnderUpdateDir(dir, localUpdateTmpDir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (isUnderTaczRoot(dir, taczRoots)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    DlcType type = classifyDirectory(dir);
                    if (type != null) {
                        addItem(grouped, root, dir, type, tooltips, tooltipLoader, infoDir);
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
                    if (isUnderUpdateDir(file, localUpdateDir) || isUnderUpdateDir(file, localUpdateTmpDir)) {
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
                        addItem(grouped, root, file, DlcType.MOD, tooltips, tooltipLoader, infoDir);
                        return FileVisitResult.CONTINUE;
                    }
                    if (name.endsWith(".zip")) {
                        DlcType type = classifyZip(file);
                        addItem(grouped, root, file, type != null ? type : DlcType.UNKNOWN, tooltips, tooltipLoader, infoDir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // Best-effort scan.
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
        Map<String, DlcTooltipLoader.TooltipData> tooltips, DlcTooltipLoader tooltipLoader, Path infoDir) {
        String relative = root.relativize(source).toString().replace('\\', '/');
        String rootId = toGameRelativePath(root).replace('\\', '/');
        String name = source.getFileName().toString();
        DlcTooltipLoader.TooltipData tooltipData = tooltips != null ? tooltips.get(name) : null;
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
        if (effectiveType == DlcType.UNKNOWN && (appliedTargetRaw == null || appliedTargetRaw.isBlank())) {
            return;
        }
        boolean enabled = isEnabledInTargets(name, fileNames, effectiveType, appliedTargetRaw, source);
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
            ? new DlcItem(id, identifier, name, effectiveType, source, appliedTargetRaw, enabled, tips, displayNames, dependencies, conflicts, allowEnableByAll, hidden)
            : new DlcItem(id, identifier, name, effectiveType, source, sources, appliedTargetRaw, enabled, tips, displayNames, dependencies, conflicts, allowEnableByAll, hidden);
        grouped.get(effectiveType).add(item);
        if (tooltipLoader != null && infoDir != null) {
            boolean updateType = !typeValid || !hadTypeField;
            tooltipLoader.ensureTooltipFile(infoDir, name, effectiveType, updateType);
        }
    }

    private boolean isEnabledInTargets(String fileName, java.util.List<String> fileNames, DlcType type, String appliedTargetRaw, Path source) {
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
                    if (!Files.exists(check)) {
                        return false;
                    }
                    if (!dirTarget) {
                        break;
                    }
                }
                return true;
            }
            if (dirTarget) {
                return Files.exists(target.resolve(fileName));
            }
            return Files.exists(target);
        }
        List<Path> targetDirs = switch (type) {
            case MOD -> List.of(DlcPaths.modsDir());
            case RESOURCE_PACK -> DlcPathConfig.getResourcepackRoots();
            case DATA_PACK -> DlcPathConfig.getDatapackRoots();
            case TACZ_PACK -> List.of(DlcPaths.taczDir());
            default -> List.of();
        };
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

    private void scanTaczRoots(Map<DlcType, List<DlcItem>> grouped, DlcTooltipLoader tooltipLoader) {
        Path mainDlcRoot = DlcPathConfig.getDlcRoots().isEmpty() ? null : DlcPathConfig.getDlcRoots().get(0);
        Path mainInfoDir = mainDlcRoot == null ? null : mainDlcRoot.resolve("info");
        Map<String, DlcTooltipLoader.TooltipData> tooltips = mainDlcRoot == null
            ? java.util.Map.of()
            : tooltipLoader.loadTooltips(mainDlcRoot);

        for (Path root : DlcPathConfig.getTaczDlcRoots()) {
            if (!Files.exists(root) || !Files.isDirectory(root)) {
                continue;
            }
            try (var stream = Files.list(root)) {
                stream.forEach(path -> {
                    String name = path.getFileName().toString();
                    if (Files.isDirectory(path)) {
                        addItem(grouped, root, path, DlcType.TACZ_PACK, tooltips, tooltipLoader, mainInfoDir);
                    } else if (name.endsWith(".zip")) {
                        addItem(grouped, root, path, DlcType.TACZ_PACK, tooltips, tooltipLoader, mainInfoDir);
                    }
                });
            } catch (IOException ignored) {
                // Best-effort TACZ scan.
            }
        }
    }

    private String toGameRelativePath(Path path) {
        if (path == null) {
            return "";
        }
        Path gameDir = DlcPaths.gameDir().toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        try {
            Path gameReal = gameDir.toRealPath();
            Path pathReal = normalized.toRealPath();
            if (pathReal.startsWith(gameReal)) {
                return gameReal.relativize(pathReal).toString();
            }
        } catch (Exception ignored) {
            // Best-effort.
        }
        try {
            if (normalized.startsWith(gameDir)) {
                return gameDir.relativize(normalized).toString();
            }
        } catch (Exception ignored) {
            // Best-effort.
        }
        return normalized.toString();
    }
}
