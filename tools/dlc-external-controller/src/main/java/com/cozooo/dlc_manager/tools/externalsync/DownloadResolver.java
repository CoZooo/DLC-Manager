package com.cozooo.dlc_manager.tools.externalsync;

import java.util.ArrayList;
import java.util.List;

final class DownloadResolver {
    private final CurseForgeClient curseForge;
    private final ModrinthClient modrinth;

    DownloadResolver(int timeoutMs) {
        this.curseForge = new CurseForgeClient(timeoutMs);
        this.modrinth = new ModrinthClient(timeoutMs);
    }

    List<SyncModels.DownloadCandidate> resolve(SyncModels.RemoteEntry entry, String minecraftVersion, String loader) {
        if (entry == null) {
            return List.of();
        }
        List<SyncModels.DownloadCandidate> candidates = new ArrayList<>();
        for (String source : order(entry.priority)) {
            try {
                if ("directurl".equals(source)) {
                    addDirect(candidates, entry);
                } else if ("curseforge".equals(source)) {
                    candidates.addAll(curseForge.resolve(entry.curseforgeProjectId, entry.curseforgeFileId, minecraftVersion, loader));
                } else if ("modrinth".equals(source)) {
                    SyncModels.DownloadCandidate candidate = modrinth.resolve(entry.modrinthProjectId, entry.modrinthVersionId, minecraftVersion, loader);
                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                }
            } catch (ExternalSyncException ignored) {
            }
        }
        return candidates;
    }

    private static List<String> order(List<String> priority) {
        List<String> order = new ArrayList<>();
        if (priority != null) {
            for (String item : priority) {
                String source = normalize(item);
                if (!source.isBlank() && !order.contains(source)) {
                    order.add(source);
                }
            }
        }
        for (String fallback : List.of("curseforge", "modrinth", "directurl")) {
            if (!order.contains(fallback)) {
                order.add(fallback);
            }
        }
        return order;
    }

    private static void addDirect(List<SyncModels.DownloadCandidate> candidates, SyncModels.RemoteEntry entry) {
        for (String url : entry.directUrls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            SyncModels.DownloadCandidate candidate = new SyncModels.DownloadCandidate();
            candidate.source = "directurl";
            candidate.url = url;
            candidate.fileName = DcRemoteLoader.extractFileName(url);
            candidates.add(candidate);
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase().replace("-", "").replace("_", "");
        return switch (normalized) {
            case "direct", "directurl", "url" -> "directurl";
            case "curse", "curseforge", "cf" -> "curseforge";
            case "modrinth", "mr" -> "modrinth";
            default -> "";
        };
    }
}
