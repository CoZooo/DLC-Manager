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

final class DlcFxPackVersionFlowTest {
    @Test
    void ignoresMissingGroupOrEntry() {
        FakeActions actions = new FakeActions();
        DlcFxPackVersionFlow<Group, Entry> flow = new DlcFxPackVersionFlow<>(actions);

        flow.select(null, new Entry("v1"));
        flow.select(new Group("pack"), null);

        assertEquals(List.of(), actions.events);
    }

    @Test
    void selectionUsesLocalOrderThenRefreshes() {
        FakeActions actions = new FakeActions();
        DlcFxPackVersionFlow<Group, Entry> flow = new DlcFxPackVersionFlow<>(actions);

        flow.select(new Group("pack"), new Entry("v2"));

        assertEquals(List.of("select:v2", "update:pack:v2", "refresh"), actions.events);
    }

    private record Group(String id) {
    }

    private record Entry(String key) {
    }

    private static final class FakeActions implements DlcFxPackVersionFlow.Actions<Group, Entry> {
        private final List<String> events = new ArrayList<>();

        @Override
        public String groupIdentifier(Group group) {
            return group.id();
        }

        @Override
        public String entryKey(Entry entry) {
            return entry.key();
        }

        @Override
        public void select(Entry entry) {
            events.add("select:" + entry.key());
        }

        @Override
        public void updateGroup(String identifier, String selectedVersionKey) {
            events.add("update:" + identifier + ":" + selectedVersionKey);
        }

        @Override
        public void refresh() {
            events.add("refresh");
        }
    }
}
