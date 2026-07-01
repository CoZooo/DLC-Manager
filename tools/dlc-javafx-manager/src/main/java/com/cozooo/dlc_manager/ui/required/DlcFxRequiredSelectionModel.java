package com.cozooo.dlc_manager.ui.required;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DlcFxRequiredSelectionModel {
    private DlcFxRequiredSelectionModel() {
    }

    public record Item(String id, boolean hasFile, boolean needsApply, List<String> dependencies) {
        public Item {
            dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        }
    }

    public static List<String> installTargetIds(Collection<Item> allItems, Collection<Item> selectedItems) {
        return targetIds(allItems, selectedItems, item -> !item.hasFile());
    }

    public static List<String> applyTargetIds(Collection<Item> allItems, Collection<Item> selectedItems) {
        return targetIds(allItems, selectedItems, item -> item.hasFile() && item.needsApply());
    }

    public static List<Item> dependencyClosure(Collection<Item> allItems, Collection<Item> selectedItems) {
        Map<String, Item> byId = index(allItems);
        List<Item> out = new ArrayList<>();
        Set<String> visiting = new LinkedHashSet<>();
        Set<String> visited = new LinkedHashSet<>();
        for (Item item : selectedItems == null ? List.<Item>of() : selectedItems) {
            visit(item, byId, visiting, visited, out);
        }
        return out;
    }

    private static List<String> targetIds(Collection<Item> allItems, Collection<Item> selectedItems, java.util.function.Predicate<Item> filter) {
        List<String> ids = new ArrayList<>();
        for (Item item : dependencyClosure(allItems, selectedItems)) {
            if (item != null && valid(item.id()) && filter.test(item)) {
                ids.add(item.id());
            }
        }
        return ids;
    }

    private static void visit(Item item, Map<String, Item> byId, Set<String> visiting, Set<String> visited, List<Item> out) {
        if (item == null || !valid(item.id()) || visited.contains(item.id()) || visiting.contains(item.id())) {
            return;
        }
        visiting.add(item.id());
        for (String dependencyId : item.dependencies()) {
            Item dependency = byId.get(dependencyId);
            if (dependency != null) {
                visit(dependency, byId, visiting, visited, out);
            }
        }
        visiting.remove(item.id());
        visited.add(item.id());
        out.add(item);
    }

    private static Map<String, Item> index(Collection<Item> items) {
        Map<String, Item> byId = new LinkedHashMap<>();
        for (Item item : items == null ? List.<Item>of() : items) {
            if (item != null && valid(item.id())) {
                byId.put(item.id(), item);
            }
        }
        return byId;
    }

    private static boolean valid(String id) {
        return id != null && !id.isBlank();
    }
}
