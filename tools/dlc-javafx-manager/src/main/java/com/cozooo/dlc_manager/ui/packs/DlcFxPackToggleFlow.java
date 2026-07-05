package com.cozooo.dlc_manager.ui.packs;

public final class DlcFxPackToggleFlow<G, E> {
    public interface Actions<G, E> {
        boolean enabled();
        E displayEntry(G group);
        void disabled();
        void toggle(G group, E entry);
        void refresh();
    }

    private final Actions<G, E> actions;

    public DlcFxPackToggleFlow(Actions<G, E> actions) {
        this.actions = actions;
    }

    public void toggle(G group) {
        if (!actions.enabled()) {
            actions.disabled();
            return;
        }
        E entry = actions.displayEntry(group);
        if (entry == null) {
            return;
        }
        actions.toggle(group, entry);
        actions.refresh();
    }
}
