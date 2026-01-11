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
    private static final Map<String, DlcItem> ITEMS_BY_NAME = new HashMap<>();

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

    public static void updateItemIndex(java.util.Collection<DlcItem> items) {
        ITEMS_BY_NAME.clear();
        if (items == null || items.isEmpty()) {
            return;
        }
        for (DlcItem item : items) {
            if (item == null) {
                continue;
            }
            String name = item.getName();
            if (name != null && !name.isBlank()) {
                ITEMS_BY_NAME.put(name, item);
            }
        }
    }

    public static DlcItem getItemByName(String name) {
        if (name == null) {
            return null;
        }
        return ITEMS_BY_NAME.get(name);
    }

    public static DependencyBatchResult collectMissingDependencies(java.util.Collection<DlcItem> targets) {
        java.util.List<DependencyLine> lines = new java.util.ArrayList<>();
        java.util.List<DlcItem> toEnable = new java.util.ArrayList<>();
        if (targets == null || targets.isEmpty()) {
            return new DependencyBatchResult(lines, toEnable);
        }
        java.util.Set<String> targetNames = new java.util.HashSet<>();
        for (DlcItem item : targets) {
            if (item != null && item.getName() != null) {
                targetNames.add(item.getName());
            }
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (DlcItem item : targets) {
            if (item == null) {
                continue;
            }
            java.util.List<String> deps = item.getDependencies();
            if (deps == null || deps.isEmpty()) {
                continue;
            }
            for (String depName : deps) {
                if (depName == null || depName.isBlank()) {
                    continue;
                }
                DlcItem depItem = getItemByName(depName);
                boolean satisfied = depItem != null && (depItem.isEnabled() || targetNames.contains(depItem.getName()));
                if (!satisfied) {
                    String key = item.getName() + "|" + depName;
                    if (seen.add(key)) {
                        lines.add(new DependencyLine(item.getName(), depName));
                    }
                    if (depItem != null && !toEnable.contains(depItem)) {
                        toEnable.add(depItem);
                    }
                }
            }
        }
        return new DependencyBatchResult(lines, toEnable);
    }

    public static ConflictBatchResult collectConflicts(java.util.Collection<DlcItem> targets) {
        java.util.List<ConflictLine> lines = new java.util.ArrayList<>();
        if (targets == null || targets.isEmpty()) {
            return new ConflictBatchResult(lines);
        }
        java.util.Set<String> targetNames = new java.util.HashSet<>();
        for (DlcItem item : targets) {
            if (item != null && item.getName() != null) {
                targetNames.add(item.getName());
            }
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (DlcItem item : targets) {
            if (item == null) {
                continue;
            }
            java.util.List<String> conflicts = item.getConflicts();
            if (conflicts == null || conflicts.isEmpty()) {
                continue;
            }
            for (String conflictName : conflicts) {
                if (conflictName == null || conflictName.isBlank()) {
                    continue;
                }
                DlcItem conflictItem = getItemByName(conflictName);
                if (conflictItem == null) {
                    continue;
                }
                boolean conflictEnabled = conflictItem.isEnabled() || targetNames.contains(conflictItem.getName());
                if (conflictEnabled) {
                    String a = item.getName();
                    String b = conflictItem.getName();
                    if (a == null || b == null) {
                        continue;
                    }
                    String key = a.compareToIgnoreCase(b) <= 0 ? a + "|" + b : b + "|" + a;
                    if (seen.add(key)) {
                        if (a.compareToIgnoreCase(b) > 0) {
                            String tmp = a;
                            a = b;
                            b = tmp;
                        }
                        lines.add(new ConflictLine(a, b));
                    }
                }
            }
        }
        return new ConflictBatchResult(lines);
    }

    public static final class DependencyBatchResult {
        private final java.util.List<DependencyLine> lines;
        private final java.util.List<DlcItem> itemsToEnable;

        private DependencyBatchResult(java.util.List<DependencyLine> lines, java.util.List<DlcItem> itemsToEnable) {
            this.lines = lines;
            this.itemsToEnable = itemsToEnable;
        }

        public java.util.List<DependencyLine> getLines() {
            return lines;
        }

        public java.util.List<DlcItem> getItemsToEnable() {
            return itemsToEnable;
        }
    }

    public static final class ConflictBatchResult {
        private final java.util.List<ConflictLine> lines;

        private ConflictBatchResult(java.util.List<ConflictLine> lines) {
            this.lines = lines;
        }

        public java.util.List<ConflictLine> getLines() {
            return lines;
        }
    }

    public static final class DependencyLine {
        private final String itemName;
        private final String dependencyName;

        private DependencyLine(String itemName, String dependencyName) {
            this.itemName = itemName;
            this.dependencyName = dependencyName;
        }

        public String getItemName() {
            return itemName;
        }

        public String getDependencyName() {
            return dependencyName;
        }
    }

    public static final class ConflictLine {
        private final String leftName;
        private final String rightName;

        private ConflictLine(String leftName, String rightName) {
            this.leftName = leftName;
            this.rightName = rightName;
        }

        public String getLeftName() {
            return leftName;
        }

        public String getRightName() {
            return rightName;
        }
    }
}
