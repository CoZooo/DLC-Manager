package com.cozooo.dlc_manager.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DlcItemFiles {
    public record Target(Path source, Path target, boolean searchByHash) {}
    private DlcItemFiles() {}

    public static List<Target> expected(DlcItem item) {
        return expected(item, true);
    }

    public static List<Target> expected(DlcItem item, boolean allowCollectionDownloads) {
        return expected(item, allowCollectionDownloads, new HashSet<>());
    }

    private static List<Target> expected(DlcItem item, boolean allowCollectionDownloads, Set<String> visited) {
        List<Target> result = new ArrayList<>();
        String id = item.getId();
        if (id != null && !visited.add(id)) return result;
        try {
            if (item.getType() == DlcType.COLLECTION) {
                try {
                    List<Path> sources = item.getSourcePaths().isEmpty() ? (item.getSourcePath() == null ? List.of() : List.of(item.getSourcePath())) : item.getSourcePaths();
                    for (Path source : sources) {
                        for (var entry : DlcCollectionSource.entries(source, item.getDlcRoot(), DlcPaths.gameDir(), allowCollectionDownloads, item.getIdentifier(), item.getCollectionRemoteEntries())) {
                            result.add(new Target(entry.source, DlcPathUtil.resolveInside(DlcPaths.gameDir(), entry.relativePath), false));
                        }
                    }
                    for (DlcItem included : item.getCollectionIncludedItems()) result.addAll(expected(included, allowCollectionDownloads, visited));
                } catch (IOException ignored) {
                }
            } else if (item.getType() == DlcType.UNKNOWN) {
            Path target = DlcPaths.resolveAppliedTarget(item.getAppliedTargetRaw());
            List<Path> sources = item.getSourcePaths();
            if (target == null || sources.isEmpty()) return result;
            boolean directory = DlcPaths.isAppliedTargetDirectory(item.getAppliedTargetRaw(), target, sources.get(0));
            if (!directory && sources.size() > 1) return result;
            for (Path source : sources) {
                if (source != null) result.add(new Target(source, directory ? target.resolve(source.getFileName()) : target, false));
            }
            } else {
            List<Path> directories = switch (item.getType()) {
                case MOD -> List.of(DlcPaths.modsDir());
                case RESOURCE_PACK -> DlcConfig.getResourcepackRoots();
                case DATA_PACK -> DlcConfig.getDatapackRoots();
                case TACZ_PACK -> DlcConfig.getTaczRoots();
                default -> List.of();
            };
            for (Path directory : directories) result.add(new Target(item.getSourcePath(), directory.resolve(item.getSourcePath().getFileName()), true));
            }
            return result;
        } finally {
            if (id != null) visited.remove(id);
        }
    }

    public static List<Path> installed(DlcItem item) throws IOException {
        List<Path> result = new ArrayList<>();
        for (Target pair : expected(item, false)) {
            Path target = pair.searchByHash() ? DlcFileMatch.find(pair.source(), pair.target().getParent(), item.getMatchMode())
                    : DlcFileMatch.matches(pair.source(), pair.target(), item.getMatchMode()) ? pair.target() : null;
            if (target == null) return List.of();
            if (item.getType() == DlcType.RESOURCE_PACK && !com.cozooo.dlc_manager.platform.DlcPlatformServices.isResourcePackSelected(target.getFileName().toString())) return List.of();
            result.add(target);
        }
        return result;
    }
}
