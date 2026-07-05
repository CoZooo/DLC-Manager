package com.cozooo.dlc_manager.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcFxSelectionModelTest {

    @Test
    void selectsOnlyEligibleVisibleItems() {
        DlcFxSelectionModel<Item> model = new DlcFxSelectionModel<>(item -> item.id);
        Item missing = new Item("missing", true);
        Item existing = new Item("existing", false);

        model.selectAll(List.of(missing, existing), item -> item.selectable);

        assertTrue(model.isSelected(missing));
        assertFalse(model.isSelected(existing));
        assertEquals(List.of(missing), model.selectedItems(List.of(missing, existing)));
    }

    @Test
    void invertOnlyAffectsEligibleVisibleItems() {
        DlcFxSelectionModel<Item> model = new DlcFxSelectionModel<>(item -> item.id);
        Item first = new Item("first", true);
        Item second = new Item("second", true);
        Item disabled = new Item("disabled", false);

        model.setSelected(first, true);
        model.invert(List.of(first, second, disabled), item -> item.selectable);

        assertFalse(model.isSelected(first));
        assertTrue(model.isSelected(second));
        assertFalse(model.isSelected(disabled));
    }

    @Test
    void invertKeepsSelectionsOutsideTheVisibleCollection() {
        DlcFxSelectionModel<Item> model = new DlcFxSelectionModel<>(item -> item.id);
        Item hidden = new Item("hidden", true);
        Item visible = new Item("visible", true);
        model.setSelected(hidden, true);

        model.invertVisible(List.of(visible), item -> item.selectable);

        assertTrue(model.isSelected(hidden));
        assertTrue(model.isSelected(visible));
    }

    @Test
    void pruneRemovesEntriesNoLongerLoaded() {
        DlcFxSelectionModel<Item> model = new DlcFxSelectionModel<>(item -> item.id);
        Item kept = new Item("kept", true);
        Item removed = new Item("removed", true);
        model.selectAll(List.of(kept, removed), item -> true);

        model.pruneTo(List.of(kept));

        assertTrue(model.isSelected(kept));
        assertFalse(model.isSelected(removed));
    }

    private record Item(String id, boolean selectable) {
    }
}
