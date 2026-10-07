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

final class DlcFxPackToggleFlowTest {
    @Test
    void disabledPackFunctionReportsDisabledWithoutToggling() {
        FakeActions actions = new FakeActions();
        DlcFxPackToggleFlow<Group, Entry> flow = new DlcFxPackToggleFlow<>(actions);

        flow.toggle(new Group(new Entry("pack")));

        assertEquals(List.of("disabled"), actions.events);
    }

    @Test
    void enabledToggleUsesDisplayedEntryThenRefreshes() {
        FakeActions actions = new FakeActions();
        actions.enabled = true;
        DlcFxPackToggleFlow<Group, Entry> flow = new DlcFxPackToggleFlow<>(actions);

        flow.toggle(new Group(new Entry("pack")));

        assertEquals(List.of("toggle:pack", "refresh"), actions.events);
    }

    @Test
    void missingDisplayedEntryDoesNothing() {
        FakeActions actions = new FakeActions();
        actions.enabled = true;
        DlcFxPackToggleFlow<Group, Entry> flow = new DlcFxPackToggleFlow<>(actions);

        flow.toggle(new Group(null));

        assertEquals(List.of(), actions.events);
    }

    private record Group(Entry entry) {
    }

    private record Entry(String id) {
    }

    private static final class FakeActions implements DlcFxPackToggleFlow.Actions<Group, Entry> {
        private final List<String> events = new ArrayList<>();
        private boolean enabled;

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public Entry displayEntry(Group group) {
            return group.entry();
        }

        @Override
        public void disabled() {
            events.add("disabled");
        }

        @Override
        public void toggle(Group group, Entry entry) {
            events.add("toggle:" + entry.id());
        }

        @Override
        public void refresh() {
            events.add("refresh");
        }
    }
}
