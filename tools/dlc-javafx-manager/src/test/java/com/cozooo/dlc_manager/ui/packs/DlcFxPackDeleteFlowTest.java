package com.cozooo.dlc_manager.ui.packs;

import com.cozooo.dlc_manager.ui.common.*;
import com.cozooo.dlc_manager.ui.management.*;
import com.cozooo.dlc_manager.ui.packs.*;
import com.cozooo.dlc_manager.ui.remote.*;
import com.cozooo.dlc_manager.ui.required.*;
import com.cozooo.dlc_manager.ui.update.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DlcFxPackDeleteFlowTest {
    @Test
    void disabledPackFunctionReportsDisabledWithoutConfirming() {
        FakeActions actions = new FakeActions();
        DlcFxPackDeleteFlow<Item> flow = new DlcFxPackDeleteFlow<>(actions);

        flow.confirmDelete(new Item("pack", false));

        assertEquals(List.of("disabled"), actions.events);
    }

    @Test
    void enabledDeleteBuildsPlanAndRunsDeleteAfterConfirmation() {
        FakeActions actions = new FakeActions();
        actions.enabled = true;
        DlcFxPackDeleteFlow<Item> flow = new DlcFxPackDeleteFlow<>(actions);

        flow.confirmDelete(new Item("pack", true));
        assertEquals(List.of("confirm:pack:true"), actions.events);

        actions.events.clear();
        actions.runConfirm();
        assertEquals(List.of("delete:pack:true"), actions.events);
    }

    private record Item(String displayName, boolean installed) {
    }

    private static final class FakeActions implements DlcFxPackDeleteFlow.Actions<Item> {
        private final List<String> events = new ArrayList<>();
        private boolean enabled;
        private Runnable confirm = () -> {};

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public String displayName(Item item) {
            return item.displayName();
        }

        @Override
        public boolean installed(Item item) {
            return item.installed();
        }

        @Override
        public void disabled() {
            events.add("disabled");
        }

        @Override
        public void confirm(DlcFxPackDeletePlan plan, Runnable onConfirm) {
            events.add("confirm:" + plan.displayName() + ":" + plan.uninstallFirst());
            confirm = onConfirm;
        }

        @Override
        public void delete(Item item, DlcFxPackDeletePlan plan) {
            events.add("delete:" + item.displayName() + ":" + plan.uninstallFirst());
        }

        private void runConfirm() {
            confirm.run();
        }
    }
}
