package com.cozooo.dlc_manager.ui.management;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class DlcFxPendingToggleModel<T> {
    private final Function<T, String> idResolver;
    private final Predicate<T> baselineResolver;
    private final Predicate<T> enabledResolver;
    private final BiConsumer<T, Boolean> enabledWriter;
    private final Map<String, Boolean> baseline = new LinkedHashMap<>();
    private final Set<String> selectedIds = new LinkedHashSet<>();
    private List<T> items = List.of();

    public DlcFxPendingToggleModel(Collection<T> items,
                                   Function<T, String> idResolver,
                                   Predicate<T> enabledResolver,
                                   BiConsumer<T, Boolean> enabledWriter) {
        this(items, idResolver, enabledResolver, enabledResolver, enabledWriter);
    }

    public DlcFxPendingToggleModel(Collection<T> items,
                                   Function<T, String> idResolver,
                                   Predicate<T> baselineResolver,
                                   Predicate<T> enabledResolver,
                                   BiConsumer<T, Boolean> enabledWriter) {
        this.idResolver = idResolver == null ? item -> String.valueOf(item) : idResolver;
        this.baselineResolver = baselineResolver == null ? item -> false : baselineResolver;
        this.enabledResolver = enabledResolver == null ? item -> false : enabledResolver;
        this.enabledWriter = enabledWriter == null ? (item, enabled) -> {} : enabledWriter;
        resetItems(items);
    }

    public void resetItems(Collection<T> nextItems) {
        this.items = nextItems == null ? List.of() : List.copyOf(nextItems);
        baseline.clear();
        for (T item : this.items) {
            if (item != null) {
                baseline.put(id(item), baselineResolver.test(item));
            }
        }
        selectedIds.removeIf(id -> !baseline.containsKey(id));
    }

    public List<T> items() {
        return items;
    }

    public Set<String> selectedIds() {
        return selectedIds;
    }

    public boolean isSelected(T item) {
        return item != null && selectedIds.contains(id(item));
    }

    public void setSelected(T item, boolean selected) {
        if (item == null) {
            return;
        }
        String id = id(item);
        if (selected) {
            selectedIds.add(id);
        } else {
            selectedIds.remove(id);
        }
    }

    public void selectAll(Collection<T> visibleItems) {
        selectedIds.clear();
        if (visibleItems == null) {
            return;
        }
        for (T item : visibleItems) {
            if (item != null) {
                selectedIds.add(id(item));
            }
        }
    }

    public void clearSelection() {
        selectedIds.clear();
    }

    public void invertSelection(Collection<T> visibleItems) {
        Set<String> next = new LinkedHashSet<>();
        if (visibleItems != null) {
            for (T item : visibleItems) {
                if (item == null) {
                    continue;
                }
                String id = id(item);
                if (!selectedIds.contains(id)) {
                    next.add(id);
                }
            }
        }
        selectedIds.clear();
        selectedIds.addAll(next);
    }

    public List<T> selectedItems() {
        if (selectedIds.isEmpty()) {
            return List.of();
        }
        List<T> out = new ArrayList<>();
        for (T item : items) {
            if (item != null && selectedIds.contains(id(item))) {
                out.add(item);
            }
        }
        return out;
    }

    public boolean baselineEnabled(T item) {
        Boolean value = item == null ? null : baseline.get(id(item));
        return value != null && value;
    }

    public boolean effectiveEnabled(T item) {
        return item != null && enabledResolver.test(item);
    }

    public void setEffectiveEnabled(T item, boolean enabled) {
        if (item != null) {
            enabledWriter.accept(item, enabled);
        }
    }

    public void setEffectiveEnabled(Collection<T> targetItems, boolean enabled) {
        if (targetItems == null) {
            return;
        }
        for (T item : targetItems) {
            setEffectiveEnabled(item, enabled);
        }
    }

    public boolean isDirty(T item) {
        return item != null && baselineEnabled(item) != effectiveEnabled(item);
    }

    public boolean hasPendingChanges() {
        for (T item : items) {
            if (isDirty(item)) {
                return true;
            }
        }
        return false;
    }

    public List<T> changedItems() {
        List<T> out = new ArrayList<>();
        for (T item : items) {
            if (isDirty(item)) {
                out.add(item);
            }
        }
        return out;
    }

    public void resetChanges() {
        for (T item : items) {
            if (item != null) {
                setEffectiveEnabled(item, baselineEnabled(item));
            }
        }
        clearSelection();
    }

    public void commitCurrentState() {
        baseline.clear();
        for (T item : items) {
            if (item != null) {
                baseline.put(id(item), enabledResolver.test(item));
            }
        }
        clearSelection();
    }

    private String id(T item) {
        String id = idResolver.apply(item);
        return id == null ? "" : id;
    }
}
