package com.cozooo.dlc_manager.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DlcItemFiles {
    public record Target(Path source, Path target, boolean searchByHash) {}
    private DlcItemFiles() {}

    public static List<Target> expected(DlcItem item) {
        List<Target> result = new ArrayList<>();
        if (item.getType() == DlcType.UNKNOWN) {
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
    }

    public static List<Path> installed(DlcItem item) throws IOException {
        List<Path> result = new ArrayList<>();
        for (Target pair : expected(item)) {
            Path target = pair.searchByHash() ? DlcFileMatch.find(pair.source(), pair.target().getParent(), item.getMatchMode())
                    : DlcFileMatch.matches(pair.source(), pair.target(), item.getMatchMode()) ? pair.target() : null;
            if (target == null) return List.of();
            if (item.getType() == DlcType.RESOURCE_PACK && !com.cozooo.dlc_manager.platform.DlcPlatformServices.isResourcePackSelected(target.getFileName().toString())) return List.of();
            result.add(target);
        }
        return result;
    }
}
