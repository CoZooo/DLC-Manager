package com.cozooo.dlc_manager.core;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class DlcRuntimeState {
    private static final Set<String> PENDING_RESTART = new HashSet<>();
    private static final Set<String> PENDING_RELOAD = new HashSet<>();
    private static final Map<String, Boolean> BASELINE_ENABLED = new HashMap<>();
    private static final Map<String, Boolean> OVERRIDE_ENABLED = new HashMap<>();

    private DlcRuntimeState() {
    }

    public static boolean isPending(String id) {
        return PENDING_RESTART.contains(id) || PENDING_RELOAD.contains(id);
    }

    public static boolean isPendingRestart(String id) {
        return PENDING_RESTART.contains(id);
    }

    public static boolean isPendingReload(String id) {
        return PENDING_RELOAD.contains(id);
    }

    public static void markPendingRestart(Collection<DlcItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (DlcItem item : items) {
            if (item == null) {
                continue;
            }
            PENDING_RESTART.add(item.getId());
        }
    }

    public static void markPendingReload(Collection<DlcItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (DlcItem item : items) {
            if (item == null) {
                continue;
            }
            PENDING_RELOAD.add(item.getId());
        }
    }

    public static void clearPendingReload(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        PENDING_RELOAD.removeAll(ids);
    }

    public static void clearPendingRestart(String id) {
        if (id == null) {
            return;
        }
        PENDING_RESTART.remove(id);
    }

    public static void clearPendingReload(String id) {
        if (id == null) {
            return;
        }
        PENDING_RELOAD.remove(id);
    }

    public static void clearAllPendingReload() {
        PENDING_RELOAD.clear();
    }

    public static Set<String> getPendingRestartIds() {
        return Collections.unmodifiableSet(PENDING_RESTART);
    }

    public static Set<String> getPendingReloadIds() {
        return Collections.unmodifiableSet(PENDING_RELOAD);
    }

    public static void ensureBaseline(String id, boolean enabled) {
        if (id == null) {
            return;
        }
        BASELINE_ENABLED.putIfAbsent(id, enabled);
    }

    public static void updateBaseline(String id, boolean enabled) {
        if (id == null) {
            return;
        }
        BASELINE_ENABLED.put(id, enabled);
    }

    public static boolean isBaselineDifferent(String id, boolean enabled) {
        if (id == null) {
            return false;
        }
        Boolean baseline = BASELINE_ENABLED.get(id);
        if (baseline == null) {
            return false;
        }
        return baseline != enabled;
    }

    public static void setOverride(String id, boolean enabled) {
        if (id == null) {
            return;
        }
        OVERRIDE_ENABLED.put(id, enabled);
    }

    public static Boolean getOverride(String id) {
        if (id == null) {
            return null;
        }
        return OVERRIDE_ENABLED.get(id);
    }

    public static void clearOverride(String id) {
        if (id == null) {
            return;
        }
        OVERRIDE_ENABLED.remove(id);
    }

    public static void clearOverrides() {
        OVERRIDE_ENABLED.clear();
    }
}
