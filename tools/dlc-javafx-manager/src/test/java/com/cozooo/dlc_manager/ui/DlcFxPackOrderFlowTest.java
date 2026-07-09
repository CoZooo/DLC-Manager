package com.cozooo.dlc_manager.ui;

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

final class DlcFxPackOrderFlowTest {
    @Test
    void ignoresMissingOrOutOfBoundsMoves() {
        FakeActions actions = new FakeActions(List.of("a", "b", "c"));
        DlcFxPackOrderFlow<String> flow = new DlcFxPackOrderFlow<>(actions);

        flow.move("x", -1);
        flow.move("a", -1);
        flow.move("c", 1);

        assertEquals(List.of(), actions.events);
        assertEquals(List.of("a", "b", "c"), actions.items);
    }

    @Test
    void movesItemUsingLocalReorderOrderThenApplies() {
        FakeActions actions = new FakeActions(List.of("a", "b", "c"));
        DlcFxPackOrderFlow<String> flow = new DlcFxPackOrderFlow<>(actions);

        flow.move("b", -1);

        assertEquals(List.of("apply:b:b,a,c"), actions.events);
        assertEquals(List.of("b", "a", "c"), actions.items);
    }

    private static final class FakeActions implements DlcFxPackOrderFlow.Actions<String> {
        private final List<String> events = new ArrayList<>();
        private List<String> items;

        private FakeActions(List<String> items) {
            this.items = new ArrayList<>(items);
        }

        @Override
        public List<String> items() {
            return items;
        }

        @Override
        public List<String> collectOrder(List<String> reordered) {
            return List.copyOf(reordered);
        }

        @Override
        public void apply(List<String> reordered, String moved, List<String> order) {
            items = new ArrayList<>(reordered);
            events.add("apply:" + moved + ":" + String.join(",", order));
        }
    }
}
