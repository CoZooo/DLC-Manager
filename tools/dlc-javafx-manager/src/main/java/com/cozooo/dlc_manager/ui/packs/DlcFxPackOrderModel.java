package com.cozooo.dlc_manager.ui.packs;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class DlcFxPackOrderModel<T> {
    private final Function<T, String> orderKeyResolver;
    private List<String> entryOrder = List.of();
    private List<String> currentOrder = List.of();

    public DlcFxPackOrderModel(Function<T, String> orderKeyResolver) {
        this.orderKeyResolver = orderKeyResolver;
    }

    public void reset(List<String> order) {
        entryOrder = List.copyOf(order == null ? List.of() : order);
        currentOrder = List.copyOf(entryOrder);
    }

    public List<T> move(List<T> items, T item, int direction) {
        List<T> reordered = new ArrayList<>(items == null ? List.of() : items);
        int index = reordered.indexOf(item);
        int target = index + direction;
        if (index < 0 || target < 0 || target >= reordered.size()) {
            return reordered;
        }
        T moved = reordered.remove(index);
        reordered.add(target, moved);
        currentOrder = collectOrder(reordered);
        return reordered;
    }

    public void setCurrentOrder(List<String> order) {
        currentOrder = List.copyOf(order == null ? List.of() : order);
    }

    public void commit() {
        entryOrder = List.copyOf(currentOrder);
    }

    public void cancel() {
        currentOrder = List.copyOf(entryOrder);
    }

    public boolean hasOrderChanges() {
        return !Objects.equals(currentOrder, entryOrder);
    }

    public List<String> entryOrder() {
        return entryOrder;
    }

    public List<String> currentOrder() {
        return currentOrder;
    }

    public List<String> collectOrder(List<T> items) {
        List<String> out = new ArrayList<>();
        for (T item : items == null ? List.<T>of() : items) {
            String key = orderKeyResolver.apply(item);
            if (key != null && !key.isBlank() && !out.contains(key)) {
                out.add(key);
            }
        }
        return List.copyOf(out);
    }
}
