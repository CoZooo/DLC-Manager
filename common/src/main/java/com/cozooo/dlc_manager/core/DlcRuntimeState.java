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
    private static final Map<String, DlcItem> ITEMS_BY_IDENTIFIER = new HashMap<>();
    private static final Map<String, java.util.List<DlcItem>> DEPENDENTS_BY_IDENTIFIER = new HashMap<>();

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

    public static Boolean getBaselineEnabled(String id) {
        if (id == null) {
            return null;
        }
        return BASELINE_ENABLED.get(id);
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
        ITEMS_BY_IDENTIFIER.clear();
        DEPENDENTS_BY_IDENTIFIER.clear();
        if (items == null || items.isEmpty()) {
            return;
        }
        for (DlcItem item : items) {
            if (item == null) {
                continue;
            }
            String identifier = item.getIdentifier();
            if (identifier != null && !identifier.isBlank()) {
                ITEMS_BY_IDENTIFIER.put(identifier, item);
            }
        }
        for (DlcItem item : items) {
            if (item == null || item.getIdentifier() == null) {
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
                if (ITEMS_BY_IDENTIFIER.containsKey(depName)) {
                    DEPENDENTS_BY_IDENTIFIER.computeIfAbsent(depName, key -> new java.util.ArrayList<>()).add(item);
                }
            }
        }
    }

    public static DlcItem getItemByIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        return ITEMS_BY_IDENTIFIER.get(identifier);
    }

    public static java.util.List<DlcItem> getDependents(String identifier) {
        if (identifier == null) {
            return java.util.List.of();
        }
        java.util.List<DlcItem> items = DEPENDENTS_BY_IDENTIFIER.get(identifier);
        return items == null ? java.util.List.of() : items;
    }

    public static java.util.Set<DlcItem> collectDependentsRecursive(DlcItem item) {
        java.util.LinkedHashSet<DlcItem> result = new java.util.LinkedHashSet<>();
        if (item == null) {
            return result;
        }
        java.util.ArrayDeque<DlcItem> queue = new java.util.ArrayDeque<>();
        queue.add(item);
        while (!queue.isEmpty()) {
            DlcItem current = queue.poll();
            if (current == null || current.getIdentifier() == null) {
                continue;
            }
            if (!result.add(current)) {
                continue;
            }
            for (DlcItem dependent : getDependents(current.getIdentifier())) {
                if (dependent != null && !result.contains(dependent)) {
                    queue.add(dependent);
                }
            }
        }
        return result;
    }

    public static java.util.Set<DlcItem> collectDependenciesRecursive(DlcItem item) {
        java.util.LinkedHashSet<DlcItem> result = new java.util.LinkedHashSet<>();
        if (item == null) {
            return result;
        }
        java.util.ArrayDeque<DlcItem> queue = new java.util.ArrayDeque<>();
        java.util.Set<String> visited = new java.util.HashSet<>();
        queue.add(item);
        while (!queue.isEmpty()) {
            DlcItem current = queue.poll();
            if (current == null || current.getIdentifier() == null) {
                continue;
            }
            if (!visited.add(current.getIdentifier())) {
                continue;
            }
            java.util.List<String> deps = current.getDependencies();
            if (deps == null || deps.isEmpty()) {
                continue;
            }
            for (String depName : deps) {
                if (depName == null || depName.isBlank()) {
                    continue;
                }
                DlcItem depItem = getItemByIdentifier(depName);
                if (depItem == null) {
                    continue;
                }
                if (result.add(depItem)) {
                    queue.add(depItem);
                }
            }
        }
        return result;
    }

    public static java.util.Set<DlcItem> collectHiddenDependenciesToDisable(java.util.Collection<DlcItem> disabledItems) {
        java.util.LinkedHashSet<DlcItem> disabledSet = new java.util.LinkedHashSet<>();
        if (disabledItems != null) {
            disabledSet.addAll(disabledItems);
        }
        java.util.LinkedHashSet<DlcItem> candidates = new java.util.LinkedHashSet<>();
        for (DlcItem item : disabledSet) {
            if (item == null) {
                continue;
            }
            for (DlcItem dep : collectDependenciesRecursive(item)) {
                if (dep != null && dep.isHidden()) {
                    candidates.add(dep);
                }
            }
        }
        java.util.LinkedHashSet<DlcItem> toDisable = new java.util.LinkedHashSet<>();
        for (DlcItem candidate : candidates) {
            boolean required = false;
            for (DlcItem dependent : getDependents(candidate.getIdentifier())) {
                if (dependent == null) {
                    continue;
                }
                if (disabledSet.contains(dependent)) {
                    continue;
                }
                if (dependent.isEnabled()) {
                    required = true;
                    break;
                }
            }
            if (!required) {
                toDisable.add(candidate);
            }
        }
        return toDisable;
    }

    public static DependencyBatchResult collectMissingDependencies(java.util.Collection<DlcItem> targets) {
        java.util.List<DependencyLine> lines = new java.util.ArrayList<>();
        java.util.List<DlcItem> toEnable = new java.util.ArrayList<>();
        if (targets == null || targets.isEmpty()) {
            return new DependencyBatchResult(lines, toEnable);
        }
        java.util.Set<String> targetIdentifiers = new java.util.HashSet<>();
        java.util.ArrayDeque<DlcItem> queue = new java.util.ArrayDeque<>();
        for (DlcItem item : targets) {
            if (item != null && item.getIdentifier() != null) {
                targetIdentifiers.add(item.getIdentifier());
                queue.add(item);
            }
        }
        java.util.Set<String> seenLines = new java.util.HashSet<>();
        java.util.Set<String> processed = new java.util.HashSet<>();
        while (!queue.isEmpty()) {
            DlcItem item = queue.poll();
            if (item == null || item.getIdentifier() == null) {
                continue;
            }
            if (!processed.add(item.getIdentifier())) {
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
                DlcItem depItem = getItemByIdentifier(depName);
                boolean satisfied = depItem != null && (depItem.isEnabled() || targetIdentifiers.contains(depItem.getIdentifier()))
                    || depItem == null && isRelationPathPresent(depName);
                if (!satisfied) {
                    String key = item.getIdentifier() + "|" + depName;
                    if (seenLines.add(key)) {
                        lines.add(new DependencyLine(item.getIdentifier(), depName));
                    }
                    if (depItem != null && !toEnable.contains(depItem)) {
                        toEnable.add(depItem);
                        targetIdentifiers.add(depItem.getIdentifier());
                    }
                }
                if (depItem != null) {
                    queue.add(depItem);
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
        java.util.Map<String, DlcItem> plannedById = new java.util.LinkedHashMap<>();
        java.util.ArrayDeque<DlcItem> queue = new java.util.ArrayDeque<>();
        for (DlcItem item : targets) {
            if (item == null || item.getIdentifier() == null || item.getIdentifier().isBlank()) {
                continue;
            }
            if (plannedById.putIfAbsent(item.getIdentifier(), item) == null) {
                queue.add(item);
            }
        }
        while (!queue.isEmpty()) {
            DlcItem item = queue.poll();
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
                DlcItem depItem = getItemByIdentifier(depName);
                if (depItem == null || depItem.getIdentifier() == null || depItem.getIdentifier().isBlank()) {
                    continue;
                }
                if (plannedById.putIfAbsent(depItem.getIdentifier(), depItem) == null) {
                    queue.add(depItem);
                }
            }
        }
        java.util.Set<String> targetIdentifiers = plannedById.keySet();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (DlcItem item : plannedById.values()) {
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
                DlcItem conflictItem = getItemByIdentifier(conflictName);
                if (conflictItem != null) {
                    boolean conflictEnabled = conflictItem.isEnabled() || targetIdentifiers.contains(conflictItem.getIdentifier());
                    if (conflictEnabled) {
                        addConflictLine(lines, seen, item.getIdentifier(), conflictItem.getIdentifier());
                    }
                }
            }
        }
        for (DlcItem existing : ITEMS_BY_IDENTIFIER.values()) {
            if (existing == null || existing.getIdentifier() == null || existing.getIdentifier().isBlank()) {
                continue;
            }
            if (targetIdentifiers.contains(existing.getIdentifier())) {
                continue;
            }
            if (!existing.isEnabled()) {
                continue;
            }
            java.util.List<String> conflicts = existing.getConflicts();
            if (conflicts == null || conflicts.isEmpty()) {
                continue;
            }
            for (String conflictName : conflicts) {
                if (conflictName == null || conflictName.isBlank()) {
                    continue;
                }
                if (!targetIdentifiers.contains(conflictName)) {
                    continue;
                }
                addConflictLine(lines, seen, existing.getIdentifier(), conflictName);
            }
        }
        return new ConflictBatchResult(lines);
    }

    private static boolean isRelationPathPresent(String value) {
        return !DlcPathUtil.findMatches(DlcPaths.gameDir(), value).isEmpty();
    }

    private static void addConflictLine(java.util.List<ConflictLine> lines, java.util.Set<String> seen, String left, String right) {
        if (left == null || right == null || left.isBlank() || right.isBlank()) {
            return;
        }
        String a = left;
        String b = right;
        String key = a.compareToIgnoreCase(b) <= 0 ? a + "|" + b : b + "|" + a;
        if (!seen.add(key)) {
            return;
        }
        if (a.compareToIgnoreCase(b) > 0) {
            String tmp = a;
            a = b;
            b = tmp;
        }
        lines.add(new ConflictLine(a, b));
    }

    public static java.util.Set<String> collectMissingDependencyIdentifiers(DlcItem item) {
        java.util.LinkedHashSet<String> missing = new java.util.LinkedHashSet<>();
        if (item == null || item.getIdentifier() == null) {
            return missing;
        }
        java.util.ArrayDeque<DlcItem> queue = new java.util.ArrayDeque<>();
        java.util.Set<String> visited = new java.util.HashSet<>();
        queue.add(item);
        while (!queue.isEmpty()) {
            DlcItem current = queue.poll();
            if (current == null || current.getIdentifier() == null) {
                continue;
            }
            if (!visited.add(current.getIdentifier())) {
                continue;
            }
            java.util.List<String> deps = current.getDependencies();
            if (deps == null || deps.isEmpty()) {
                continue;
            }
            for (String depName : deps) {
                if (depName == null || depName.isBlank()) {
                    continue;
                }
                DlcItem depItem = getItemByIdentifier(depName);
                if (depItem == null) {
                    if (isRelationPathPresent(depName)) {
                        continue;
                    }
                    missing.add(depName);
                } else {
                    queue.add(depItem);
                }
            }
        }
        return missing;
    }

    public static boolean hasMissingDependencies(DlcItem item) {
        return !collectMissingDependencyIdentifiers(item).isEmpty();
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
