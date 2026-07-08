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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcFxPendingToggleModelTest {

    @Test
    void checkboxSelectionDoesNotChangeEffectiveState() {
        TestItem item = new TestItem("a", false);
        DlcFxPendingToggleModel<TestItem> model = model(List.of(item));

        model.setSelected(item, true);

        assertTrue(model.isSelected(item));
        assertFalse(item.enabled);
        assertFalse(model.hasPendingChanges());
    }

    @Test
    void enabledChangesArePendingUntilCommittedOrReset() {
        TestItem item = new TestItem("a", false);
        DlcFxPendingToggleModel<TestItem> model = model(List.of(item));

        model.setEffectiveEnabled(item, true);

        assertTrue(item.enabled);
        assertTrue(model.isDirty(item));
        assertTrue(model.hasPendingChanges());
        assertEquals(List.of(item), model.changedItems());

        model.resetChanges();

        assertFalse(item.enabled);
        assertFalse(model.hasPendingChanges());

        model.setEffectiveEnabled(item, true);
        model.commitCurrentState();

        assertTrue(item.enabled);
        assertFalse(model.hasPendingChanges());
    }

    @Test
    void batchSelectionAndEnableUsesSharedState() {
        TestItem first = new TestItem("a", false);
        TestItem second = new TestItem("b", true);
        DlcFxPendingToggleModel<TestItem> model = model(List.of(first, second));

        model.selectAll(List.of(first, second));
        model.setEffectiveEnabled(model.selectedItems(), false);

        assertFalse(first.enabled);
        assertFalse(second.enabled);
        assertEquals(List.of(second), model.changedItems());

        model.invertSelection(List.of(first, second));

        assertFalse(model.isSelected(first));
        assertFalse(model.isSelected(second));
    }

    private static DlcFxPendingToggleModel<TestItem> model(List<TestItem> items) {
        return new DlcFxPendingToggleModel<>(
                items,
                item -> item.id,
                item -> item.enabled,
                (item, enabled) -> item.enabled = enabled
        );
    }

    private static final class TestItem {
        final String id;
        boolean enabled;

        TestItem(String id, boolean enabled) {
            this.id = id;
            this.enabled = enabled;
        }
    }
}
