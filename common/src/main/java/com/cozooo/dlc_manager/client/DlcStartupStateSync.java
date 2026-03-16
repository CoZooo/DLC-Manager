package com.cozooo.dlc_manager.client;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcScanner;
import com.cozooo.dlc_manager.core.DlcType;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class DlcStartupStateSync {
    private static boolean syncedThisLaunch;

    private DlcStartupStateSync() {
    }

    public static synchronized void syncOnStartup() {
        if (syncedThisLaunch) {
            return;
        }
        syncedThisLaunch = true;
        boolean changed = false;
        Map<DlcType, List<DlcItem>> grouped = new DlcScanner().scan(true);
        for (List<DlcItem> items : grouped.values()) {
            if (items == null || items.isEmpty()) {
                continue;
            }
            for (DlcItem item : items) {
                if (item == null) {
                    continue;
                }
                DlcConfig.DlcState current = DlcConfig.getState(item.getId());
                boolean enabled = item.isEnabled();
                if (enabled) {
                    List<String> appliedTargets = item.getAppliedTargets().stream()
                        .map(DlcStartupStateSync::toStoredPath)
                        .filter(path -> path != null && !path.isBlank())
                        .toList();
                    if (current != null && current.enabled && sameTargets(current.appliedTargets, appliedTargets)) {
                        continue;
                    }
                    DlcConfig.DlcState next = new DlcConfig.DlcState();
                    next.enabled = true;
                    next.appliedTargets = appliedTargets;
                    DlcConfig.setState(item.getId(), next);
                    changed = true;
                    continue;
                }
                if (current != null && current.enabled) {
                    DlcConfig.DlcState next = new DlcConfig.DlcState();
                    next.enabled = false;
                    next.appliedTargets = List.of();
                    DlcConfig.setState(item.getId(), next);
                    changed = true;
                }
            }
        }
        if (changed) {
            DlcConfig.save();
        }
    }

    private static boolean sameTargets(List<String> left, List<String> right) {
        List<String> a = left == null ? List.of() : left;
        List<String> b = right == null ? List.of() : right;
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!normalize(a.get(i)).equals(normalize(b.get(i)))) {
                return false;
            }
        }
        return true;
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        java.nio.file.Path path = com.cozooo.dlc_manager.core.DlcPaths.parsePathLenient(raw);
        if (path == null) {
            return raw;
        }
        if (path.isAbsolute()) {
            return path.normalize().toString();
        }
        return com.cozooo.dlc_manager.core.DlcPaths.gameDir().resolve(path).normalize().toString();
    }

    private static String toStoredPath(Path path) {
        if (path == null) {
            return null;
        }
        Path gameDir = DlcPaths.gameDir().toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(gameDir)) {
            return gameDir.relativize(normalized).toString();
        }
        return normalized.toString();
    }
}
