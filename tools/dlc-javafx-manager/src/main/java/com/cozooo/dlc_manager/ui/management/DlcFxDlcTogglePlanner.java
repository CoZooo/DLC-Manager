package com.cozooo.dlc_manager.ui.management;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DlcFxDlcTogglePlanner {
    private DlcFxDlcTogglePlanner() {
    }

    public record Item(String id,
                       String identifier,
                       boolean enabled,
                       List<String> dependencies,
                       List<String> conflicts,
                       boolean allowEnableByAll,
                       boolean alarm,
                       boolean hidden) {
        public Item {
            dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
            conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        }
    }

    public record Conflict(String leftId, String rightId) {
    }

    public record Dependency(String itemId, String dependencyId) {
    }

    public record EnablePlan(List<String> idsToEnable, List<Dependency> dependencies, List<Conflict> conflicts, boolean hasAlarm) {
        public EnablePlan {
            idsToEnable = idsToEnable == null ? List.of() : List.copyOf(idsToEnable);
            dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
            conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        }
    }

    public static EnablePlan planEnable(Collection<Item> allItems, Collection<String> selectedIds) {
        Map<String, Item> byId = indexById(allItems);
        Map<String, Item> byIdentifier = indexByIdentifier(allItems);
        List<Item> closure = new ArrayList<>();
        List<Dependency> dependencies = new ArrayList<>();
        Set<String> visiting = new LinkedHashSet<>();
        Set<String> visited = new LinkedHashSet<>();
        Set<String> dependencyLines = new LinkedHashSet<>();
        Set<String> selected = new LinkedHashSet<>();
        for (String id : selectedIds == null ? List.<String>of() : selectedIds) {
            if (valid(id)) {
                selected.add(id);
            }
        }
        for (String id : selectedIds == null ? List.<String>of() : selectedIds) {
            Item item = byId.get(id);
            visitDependencies(item, byId, byIdentifier, visiting, visited, closure, dependencies, dependencyLines, selected);
        }
        List<String> ids = new ArrayList<>();
        Set<String> planned = new LinkedHashSet<>();
        boolean hasAlarm = false;
        for (Item item : closure) {
            if (item == null || !valid(item.id())) {
                continue;
            }
            if (!item.enabled()) {
                ids.add(item.id());
                planned.add(item.id());
            }
            hasAlarm = hasAlarm || item.alarm();
        }
        List<Conflict> conflicts = collectConflicts(closure, byId, byIdentifier, planned);
        return new EnablePlan(ids, dependencies, conflicts, hasAlarm);
    }

    public static List<String> planDisable(Collection<Item> allItems, Collection<String> selectedIds) {
        Map<String, Item> byId = indexById(allItems);
        Map<String, Item> byIdentifier = indexByIdentifier(allItems);
        Set<String> out = new LinkedHashSet<>();
        for (String id : selectedIds == null ? List.<String>of() : selectedIds) {
            collectDependents(id, byId, out);
        }
        collectHiddenDependenciesToDisable(byId, byIdentifier, out);
        return new ArrayList<>(out);
    }

    public static List<Conflict> collectConflictsForIds(Collection<Item> allItems, Collection<String> selectedIds) {
        Map<String, Item> byId = indexById(allItems);
        Map<String, Item> byIdentifier = indexByIdentifier(allItems);
        List<Item> selectedItems = new ArrayList<>();
        Set<String> planned = new LinkedHashSet<>();
        for (String id : selectedIds == null ? List.<String>of() : selectedIds) {
            Item item = byId.get(id);
            if (item == null || !valid(item.id())) {
                continue;
            }
            selectedItems.add(item);
            if (!item.enabled()) {
                planned.add(item.id());
            }
        }
        return collectConflicts(selectedItems, byId, byIdentifier, planned);
    }

    public static List<String> filterConflictTargetIds(Collection<String> selectedIds, Collection<Conflict> conflicts) {
        if (selectedIds == null || selectedIds.isEmpty() || conflicts == null || conflicts.isEmpty()) {
            return selectedIds == null ? List.of() : new ArrayList<>(selectedIds);
        }
        Set<String> selected = new LinkedHashSet<>();
        for (String id : selectedIds) {
            if (valid(id)) {
                selected.add(id);
            }
        }
        Set<String> skipped = new LinkedHashSet<>();
        for (Conflict conflict : conflicts) {
            if (conflict == null) {
                continue;
            }
            if (selected.contains(conflict.leftId())) {
                skipped.add(conflict.leftId());
            }
            if (selected.contains(conflict.rightId())) {
                skipped.add(conflict.rightId());
            }
        }
        List<String> out = new ArrayList<>();
        for (String id : selected) {
            if (!skipped.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    private static void visitDependencies(Item item, Map<String, Item> byId, Map<String, Item> byIdentifier,
                                          Set<String> visiting, Set<String> visited, List<Item> out,
                                          List<Dependency> dependencies, Set<String> dependencyLines, Set<String> selectedIds) {
        if (item == null || !valid(item.id()) || visited.contains(item.id()) || visiting.contains(item.id())) {
            return;
        }
        visiting.add(item.id());
        for (String dependency : item.dependencies()) {
            Item dependencyItem = firstNonNull(byIdentifier.get(dependency), byId.get(dependency));
            if (dependencyItem != null && valid(dependencyItem.id()) && !dependencyItem.enabled() && !selectedIds.contains(dependencyItem.id())) {
                String key = item.id() + "\u0000" + dependencyItem.id();
                if (dependencyLines.add(key)) {
                    dependencies.add(new Dependency(item.id(), dependencyItem.id()));
                }
            }
            visitDependencies(dependencyItem, byId, byIdentifier, visiting, visited, out, dependencies, dependencyLines, selectedIds);
        }
        visiting.remove(item.id());
        visited.add(item.id());
        out.add(item);
    }

    private static List<Conflict> collectConflicts(List<Item> closure, Map<String, Item> byId, Map<String, Item> byIdentifier,
                                                   Set<String> plannedIds) {
        List<Conflict> conflicts = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Item item : closure) {
            if (item == null || !valid(item.id())) {
                continue;
            }
            for (String conflictId : item.conflicts()) {
                Item other = firstNonNull(byIdentifier.get(conflictId), byId.get(conflictId));
                if (other == null || !valid(other.id())) {
                    continue;
                }
                if (!other.enabled() && !plannedIds.contains(other.id())) {
                    continue;
                }
                addConflict(conflicts, seen, item.id(), other.id());
            }
        }
        for (Item existing : byId.values()) {
            if (existing == null || !valid(existing.id()) || !existing.enabled() || plannedIds.contains(existing.id())) {
                continue;
            }
            for (String conflictId : existing.conflicts()) {
                Item other = firstNonNull(byIdentifier.get(conflictId), byId.get(conflictId));
                if (other == null || !valid(other.id()) || !plannedIds.contains(other.id())) {
                    continue;
                }
                addConflict(conflicts, seen, existing.id(), other.id());
            }
        }
        return conflicts;
    }

    private static void addConflict(List<Conflict> conflicts, Set<String> seen, String left, String right) {
        if (!valid(left) || !valid(right)) {
            return;
        }
        String a = left;
        String b = right;
        String key = a.compareToIgnoreCase(b) <= 0 ? a + "\u0000" + b : b + "\u0000" + a;
        if (!seen.add(key)) {
            return;
        }
        if (a.compareToIgnoreCase(b) > 0) {
            String tmp = a;
            a = b;
            b = tmp;
        }
        conflicts.add(new Conflict(a, b));
    }

    private static void collectDependents(String id, Map<String, Item> byId, Set<String> out) {
        if (!valid(id) || !out.add(id)) {
            return;
        }
        Item base = byId.get(id);
        String identifier = base == null ? id : base.identifier();
        for (Item item : byId.values()) {
            if (item == null || !item.enabled() || !valid(item.id()) || out.contains(item.id())) {
                continue;
            }
            if (item.dependencies().contains(id) || (identifier != null && item.dependencies().contains(identifier))) {
                collectDependents(item.id(), byId, out);
            }
        }
    }

    private static void collectHiddenDependenciesToDisable(Map<String, Item> byId, Map<String, Item> byIdentifier, Set<String> disabledIds) {
        Set<String> candidates = new LinkedHashSet<>();
        for (String id : new ArrayList<>(disabledIds)) {
            Item item = byId.get(id);
            collectHiddenDependencies(item, byId, byIdentifier, candidates, new LinkedHashSet<>());
        }
        for (String candidateId : candidates) {
            Item candidate = byId.get(candidateId);
            if (candidate == null || !candidate.hidden()) {
                continue;
            }
            if (!hasEnabledDependentOutsideDisabled(candidate, byId, disabledIds)) {
                disabledIds.add(candidateId);
            }
        }
    }

    private static void collectHiddenDependencies(Item item, Map<String, Item> byId, Map<String, Item> byIdentifier,
                                                  Set<String> out, Set<String> visited) {
        if (item == null || !valid(item.id()) || !visited.add(item.id())) {
            return;
        }
        for (String dependency : item.dependencies()) {
            Item dependencyItem = firstNonNull(byIdentifier.get(dependency), byId.get(dependency));
            if (dependencyItem == null) {
                continue;
            }
            if (dependencyItem.hidden()) {
                out.add(dependencyItem.id());
            }
            collectHiddenDependencies(dependencyItem, byId, byIdentifier, out, visited);
        }
    }

    private static boolean hasEnabledDependentOutsideDisabled(Item candidate, Map<String, Item> byId, Set<String> disabledIds) {
        for (Item item : byId.values()) {
            if (item == null || !item.enabled() || disabledIds.contains(item.id())) {
                continue;
            }
            if (item.dependencies().contains(candidate.id()) || item.dependencies().contains(candidate.identifier())) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Item> indexById(Collection<Item> items) {
        Map<String, Item> byId = new LinkedHashMap<>();
        for (Item item : items == null ? List.<Item>of() : items) {
            if (item != null && valid(item.id())) {
                byId.put(item.id(), item);
            }
        }
        return byId;
    }

    private static Map<String, Item> indexByIdentifier(Collection<Item> items) {
        Map<String, Item> byIdentifier = new LinkedHashMap<>();
        for (Item item : items == null ? List.<Item>of() : items) {
            if (item != null && valid(item.identifier())) {
                byIdentifier.put(item.identifier(), item);
            }
        }
        return byIdentifier;
    }

    private static <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private static boolean valid(String value) {
        return value != null && !value.isBlank();
    }
}
