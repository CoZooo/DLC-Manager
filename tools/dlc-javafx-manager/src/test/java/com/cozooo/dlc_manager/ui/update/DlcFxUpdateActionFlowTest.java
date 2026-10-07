package com.cozooo.dlc_manager.ui.update;

import com.cozooo.dlc_manager.ui.common.*;
import com.cozooo.dlc_manager.ui.management.*;
import com.cozooo.dlc_manager.ui.packs.*;
import com.cozooo.dlc_manager.ui.remote.*;
import com.cozooo.dlc_manager.ui.required.*;
import com.cozooo.dlc_manager.ui.update.*;

import com.cozooo.dlc_manager.client.update.DlcUpdateRowPlanner;
import com.cozooo.dlc_manager.local.DlcFxLocalUpdateTab;
import com.cozooo.dlc_manager.server.DlcFxServerUpdateTab;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DlcFxUpdateActionFlowTest {
    @Test
    void dispatchesUpdateRowsUsingLocalActionOrder() {
        FakeActions actions = new FakeActions();
        DlcFxUpdateActionFlow flow = new DlcFxUpdateActionFlow(actions);

        flow.perform(row(DlcUpdateRowPlanner.Action.MANAGER_ROLLBACK, "manager-old"));
        flow.perform(row(DlcUpdateRowPlanner.Action.ROLLBACK, "base+dlc"));
        flow.perform(row(DlcUpdateRowPlanner.Action.REUPDATE, ""));
        flow.perform(row(DlcUpdateRowPlanner.Action.UPDATE, ""));
        flow.perform(row(DlcUpdateRowPlanner.Action.NONE, ""));
        flow.perform(null);

        assertEquals(List.of(
                "managerRollback:manager-old",
                "rollback:base+dlc",
                "reapply",
                "apply"
        ), actions.events);
    }

    @Test
    void rollbackActionsWithoutTargetReportMissingTarget() {
        FakeActions actions = new FakeActions();
        DlcFxUpdateActionFlow flow = new DlcFxUpdateActionFlow(actions);

        flow.perform(row(DlcUpdateRowPlanner.Action.MANAGER_ROLLBACK, ""));
        flow.perform(row(DlcUpdateRowPlanner.Action.ROLLBACK, null));

        assertEquals(List.of("missingTarget", "missingTarget"), actions.events);
    }

    @Test
    void localAndRemoteUpdateTabsDelegateRowActionsToSharedFlow() throws Exception {
        assertEquals(DlcFxUpdateActionFlow.class, actionFlowFieldType(DlcFxLocalUpdateTab.class));
        assertEquals(DlcFxUpdateActionFlow.class, actionFlowFieldType(DlcFxServerUpdateTab.class));
        assertFalse(hasDeclaredMethod(DlcFxLocalUpdateTab.class, "confirmRowAction"));
        assertFalse(hasDeclaredMethod(DlcFxServerUpdateTab.class, "confirmApplyRow"));
    }

    @Test
    void localAndRemoteUpdateTabsShouldUseSharedToolbarFlow() throws Exception {
        assertEquals(DlcFxUpdateToolbarFlow.class, toolbarFlowFieldType(DlcFxLocalUpdateTab.class));
        assertEquals(DlcFxUpdateToolbarFlow.class, toolbarFlowFieldType(DlcFxServerUpdateTab.class));
    }

    private static DlcUpdateRowPlanner.UpdateRow row(DlcUpdateRowPlanner.Action action, String rollbackTarget) {
        return new DlcUpdateRowPlanner.UpdateRow(null, action, DlcUpdateRowPlanner.Type.UPDATE,
                "old", "new", rollbackTarget, "old", "new");
    }

    private static final class FakeActions implements DlcFxUpdateActionFlow.Actions {
        private final List<String> events = new ArrayList<>();

        @Override
        public void targetMissing() {
            events.add("missingTarget");
        }

        @Override
        public void managerRollback(String targetVersion) {
            events.add("managerRollback:" + targetVersion);
        }

        @Override
        public void rollback(String targetVersion) {
            events.add("rollback:" + targetVersion);
        }

        @Override
        public void reapply(DlcUpdateRowPlanner.UpdateRow row) {
            events.add("reapply");
        }

        @Override
        public void apply(DlcUpdateRowPlanner.UpdateRow row) {
            events.add("apply");
        }
    }
}
