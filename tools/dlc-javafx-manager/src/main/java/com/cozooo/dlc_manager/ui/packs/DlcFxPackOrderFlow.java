package com.cozooo.dlc_manager.ui.packs;

import java.util.ArrayList;
import java.util.List;

public final class DlcFxPackOrderFlow<T> {
    public interface Actions<T> {
        List<T> items();
        List<String> collectOrder(List<T> reordered);
        void apply(List<T> reordered, T moved, List<String> order);
    }

    private final Actions<T> actions;

    public DlcFxPackOrderFlow(Actions<T> actions) {
        this.actions = actions;
    }

    public void move(T item, int direction) {
        List<T> reordered = new ArrayList<>(actions.items() == null ? List.of() : actions.items());
        int index = reordered.indexOf(item);
        int target = index + direction;
        if (index < 0 || target < 0 || target >= reordered.size()) {
            return;
        }
        T moved = reordered.remove(index);
        reordered.add(target, moved);
        actions.apply(List.copyOf(reordered), moved, actions.collectOrder(reordered));
    }
}
