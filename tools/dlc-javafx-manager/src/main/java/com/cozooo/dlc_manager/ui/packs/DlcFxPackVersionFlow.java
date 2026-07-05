package com.cozooo.dlc_manager.ui.packs;

public final class DlcFxPackVersionFlow<G, E> {
    public interface Actions<G, E> {
        String groupIdentifier(G group);
        String entryKey(E entry);
        void select(E entry);
        void updateGroup(String identifier, String selectedVersionKey);
        void refresh();
    }

    private final Actions<G, E> actions;

    public DlcFxPackVersionFlow(Actions<G, E> actions) {
        this.actions = actions;
    }

    public void select(G group, E entry) {
        if (group == null || entry == null) {
            return;
        }
        actions.select(entry);
        actions.updateGroup(actions.groupIdentifier(group), actions.entryKey(entry));
        actions.refresh();
    }
}
