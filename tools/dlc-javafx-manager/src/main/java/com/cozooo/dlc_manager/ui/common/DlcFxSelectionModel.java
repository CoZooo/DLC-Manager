package com.cozooo.dlc_manager.ui.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public final class DlcFxSelectionModel<T> {
    private final Function<T, String> idResolver;
    private final Set<String> selectedIds = new LinkedHashSet<>();

    public DlcFxSelectionModel(Function<T, String> idResolver) {
        this.idResolver = idResolver == null ? item -> String.valueOf(item) : idResolver;
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

    public void clear() {
        selectedIds.clear();
    }

    public void selectAll(Collection<T> items, Predicate<T> selectable) {
        selectedIds.clear();
        if (items == null) {
            return;
        }
        Predicate<T> filter = selectable == null ? item -> true : selectable;
        for (T item : items) {
            if (item != null && filter.test(item)) {
                selectedIds.add(id(item));
            }
        }
    }

    public void invert(Collection<T> items, Predicate<T> selectable) {
        Set<String> next = new LinkedHashSet<>();
        if (items != null) {
            Predicate<T> filter = selectable == null ? item -> true : selectable;
            for (T item : items) {
                if (item == null || !filter.test(item)) {
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

    public void invertVisible(Collection<T> items, Predicate<T> selectable) {
        if (items == null) {
            return;
        }
        Predicate<T> filter = selectable == null ? item -> true : selectable;
        for (T item : items) {
            if (item == null || !filter.test(item)) {
                continue;
            }
            String id = id(item);
            if (selectedIds.contains(id)) {
                selectedIds.remove(id);
            } else {
                selectedIds.add(id);
            }
        }
    }

    public void pruneTo(Collection<T> items) {
        Set<String> available = new LinkedHashSet<>();
        if (items != null) {
            for (T item : items) {
                if (item != null) {
                    available.add(id(item));
                }
            }
        }
        selectedIds.removeIf(id -> !available.contains(id));
    }

    public List<T> selectedItems(Collection<T> items) {
        if (items == null || selectedIds.isEmpty()) {
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

    private String id(T item) {
        String id = idResolver.apply(item);
        return id == null ? "" : id;
    }
}
