package com.cozooo.dlc_manager.ui.required;

public final class DlcFxRequiredRefreshFlow {
    public interface Actions {
        boolean enabled();
        void disabled();
        void refresh();
    }

    private final Actions actions;

    public DlcFxRequiredRefreshFlow(Actions actions) {
        this.actions = actions;
    }

    public void refresh() {
        if (!actions.enabled()) {
            actions.disabled();
            return;
        }
        actions.refresh();
    }
}
