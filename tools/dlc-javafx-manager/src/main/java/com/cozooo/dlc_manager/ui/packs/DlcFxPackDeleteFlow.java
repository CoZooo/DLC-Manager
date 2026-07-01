package com.cozooo.dlc_manager.ui.packs;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackDeletePlan;

public final class DlcFxPackDeleteFlow<T> {
    public interface Actions<T> {
        boolean enabled();
        String displayName(T item);
        boolean installed(T item);
        void disabled();
        void confirm(DlcFxPackDeletePlan plan, Runnable onConfirm);
        void delete(T item, DlcFxPackDeletePlan plan);
    }

    private final Actions<T> actions;

    public DlcFxPackDeleteFlow(Actions<T> actions) {
        this.actions = actions;
    }

    public void confirmDelete(T item) {
        if (!actions.enabled()) {
            actions.disabled();
            return;
        }
        if (item == null) {
            return;
        }
        DlcFxPackDeletePlan plan = DlcFxPackDeletePlan.of(actions.displayName(item), actions.installed(item));
        actions.confirm(plan, () -> actions.delete(item, plan));
    }
}
