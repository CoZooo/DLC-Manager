package com.cozooo.dlc_manager.ui.update;

import com.cozooo.dlc_manager.client.update.DlcUpdateRowPlanner;

public final class DlcFxUpdateActionFlow {
    public interface Actions {
        void targetMissing();
        void managerRollback(String targetVersion);
        void rollback(String targetVersion);
        void reapply(DlcUpdateRowPlanner.UpdateRow row);
        void apply(DlcUpdateRowPlanner.UpdateRow row);
    }

    private final Actions actions;

    public DlcFxUpdateActionFlow(Actions actions) {
        this.actions = actions;
    }

    public void perform(DlcUpdateRowPlanner.UpdateRow row) {
        if (row == null || row.action == DlcUpdateRowPlanner.Action.NONE) {
            return;
        }
        switch (row.action) {
            case MANAGER_ROLLBACK -> performRollback(row.rollbackTarget, true);
            case ROLLBACK -> performRollback(row.rollbackTarget, false);
            case REUPDATE -> actions.reapply(row);
            case UPDATE -> actions.apply(row);
            default -> {
            }
        }
    }

    private void performRollback(String targetVersion, boolean managerRollback) {
        if (targetVersion == null || targetVersion.isBlank()) {
            actions.targetMissing();
            return;
        }
        if (managerRollback) {
            actions.managerRollback(targetVersion);
        } else {
            actions.rollback(targetVersion);
        }
    }
}
