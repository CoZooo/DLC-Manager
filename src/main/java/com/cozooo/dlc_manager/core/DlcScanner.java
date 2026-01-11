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
import net.minecraftforge.fml.ModList;
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
        Set<Path> seen = new HashSet<>();
        for (Path root : roots) {
            if (!Files.exists(root) || seen.contains(root)) {
                continue;
            }
            seen.add(root);
            Map<String, DlcTooltipLoader.TooltipData> tooltips = tooltipLoader.loadTooltips(root);
            scanRoot(grouped, root, taczRoots, tooltips, tooltipLoader);
        }

        if (ModList.get().isLoaded("tacz")) {
            scanTaczRoots(grouped, tooltipLoader);
        }

        return grouped;
    }

    private void scanRoot(Map<DlcType, List<DlcItem>> grouped, Path root, List<Path> taczRoots,
        Map<String, DlcTooltipLoader.TooltipData> tooltips, DlcTooltipLoader tooltipLoader) {
        Path infoDir = root.resolve("info");
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (root.equals(dir)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (isUnderInfoDir(dir, infoDir)) {
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
                    if (isUnderInfoDir(file, infoDir)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (isUnderTaczRoot(file, taczRoots)) {
                        return FileVisitResult.CONTINUE;
                    }
                    String name = file.getFileName().toString();
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

    private void addItem(Map<DlcType, List<DlcItem>> grouped, Path root, Path source, DlcType detectedType,
        Map<String, DlcTooltipLoader.TooltipData> tooltips, DlcTooltipLoader tooltipLoader, Path infoDir) {
        String relative = root.relativize(source).toString().replace('\\', '/');
        String rootId = root.toString().replace('\\', '/');
        String name = source.getFileName().toString();
        DlcTooltipLoader.TooltipData tooltipData = tooltips != null ? tooltips.get(name) : null;
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
        String id = effectiveType.name().toLowerCase() + ":" + rootId + "|" + relative;
        boolean enabled = isEnabledInTargets(name, effectiveType);
        Map<String, String> tips = tooltipData != null ? tooltipData.getTips() : null;
        DlcItem item = new DlcItem(id, name, effectiveType, source, enabled, tips);
        grouped.get(effectiveType).add(item);
        if (tooltipLoader != null && infoDir != null) {
            boolean updateType = !typeValid || !hadTypeField;
            tooltipLoader.ensureTooltipFile(infoDir, name, effectiveType, updateType);
        }
    }

    private boolean isEnabledInTargets(String fileName, DlcType type) {
        List<Path> targetDirs = switch (type) {
            case MOD -> List.of(DlcPaths.modsDir());
            case RESOURCE_PACK -> DlcPathConfig.getResourcepackRoots();
            case DATA_PACK -> DlcPathConfig.getDatapackRoots();
            case TACZ_PACK -> List.of(DlcPaths.taczDir());
            default -> DlcPathConfig.getOtherRoots();
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
        for (Path root : DlcPathConfig.getTaczDlcRoots()) {
            if (!Files.exists(root) || !Files.isDirectory(root)) {
                continue;
            }
            Map<String, DlcTooltipLoader.TooltipData> tooltips = tooltipLoader.loadTooltips(root);
            Path infoDir = root.resolve("info");
            try (var stream = Files.list(root)) {
                stream.forEach(path -> {
                    if (isUnderInfoDir(path, infoDir)) {
                        return;
                    }
                    String name = path.getFileName().toString();
                    if (Files.isDirectory(path)) {
                        addItem(grouped, root, path, DlcType.TACZ_PACK, tooltips, tooltipLoader, infoDir);
                    } else if (name.endsWith(".zip")) {
                        addItem(grouped, root, path, DlcType.TACZ_PACK, tooltips, tooltipLoader, infoDir);
                    }
                });
            } catch (IOException ignored) {
                // Best-effort TACZ scan.
            }
        }
    }
}
