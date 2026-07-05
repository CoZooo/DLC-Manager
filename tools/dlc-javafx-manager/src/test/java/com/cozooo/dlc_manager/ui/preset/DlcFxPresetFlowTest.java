package com.cozooo.dlc_manager.ui.preset;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcFxPresetFlowTest {
    @Test
    void pendingToggleIsRemovedWhenItMatchesOriginalState() {
        DlcFxPresetFlow<Item> flow = new DlcFxPresetFlow<>(Item::id, Item::enabled);
        Item entry = new Item("rail", true);

        flow.setPending(entry, false);
        assertTrue(flow.hasPendingChanges());
        assertEquals(false, flow.effectiveEnabled(entry));

        flow.setPending(entry, true);
        assertFalse(flow.hasPendingChanges());
        assertEquals(true, flow.effectiveEnabled(entry));
    }

    @Test
    void setAllOnlyKeepsEntriesThatActuallyChange() {
        DlcFxPresetFlow<Item> flow = new DlcFxPresetFlow<>(Item::id, Item::enabled);
        Item enabled = new Item("enabled", true);
        Item disabled = new Item("disabled", false);

        flow.setAll(List.of(enabled, disabled), false);

        assertEquals(1, flow.pendingChanges().size());
        assertEquals(false, flow.pendingChanges().get("enabled"));
        assertFalse(flow.isPending(disabled));
    }

    private record Item(String id, boolean enabled) {
    }
}
