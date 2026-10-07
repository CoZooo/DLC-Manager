package com.cozooo.dlc_manager.ui.required;

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

final class DlcFxRequiredRefreshFlowTest {
    @Test
    void refreshUsesSharedFlow() {
        FakeActions actions = new FakeActions();
        actions.enabled = true;
        DlcFxRequiredRefreshFlow flow = new DlcFxRequiredRefreshFlow(actions);

        flow.refresh();

        assertEquals(List.of("refresh"), actions.events);
    }

    @Test
    void disabledRefreshReportsDisabled() {
        FakeActions actions = new FakeActions();
        DlcFxRequiredRefreshFlow flow = new DlcFxRequiredRefreshFlow(actions);

        flow.refresh();

        assertEquals(List.of("disabled"), actions.events);
    }

    private static final class FakeActions implements DlcFxRequiredRefreshFlow.Actions {
        private final List<String> events = new ArrayList<>();
        private boolean enabled;

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public void disabled() {
            events.add("disabled");
        }

        @Override
        public void refresh() {
            events.add("refresh");
        }
    }
}
