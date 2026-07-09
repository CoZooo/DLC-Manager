package com.cozooo.dlc_manager.ui;

import com.cozooo.dlc_manager.ui.common.*;
import com.cozooo.dlc_manager.ui.management.*;
import com.cozooo.dlc_manager.ui.packs.*;
import com.cozooo.dlc_manager.ui.remote.*;
import com.cozooo.dlc_manager.ui.required.*;
import com.cozooo.dlc_manager.ui.update.*;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcFxPackOrderModelTest {
    @Test
    void movingGroupCreatesPendingOrderChangeWithoutCommittingBaseline() {
        DlcFxPackOrderModel<String> model = new DlcFxPackOrderModel<>(value -> value);
        model.reset(List.of("a", "b", "c"));

        List<String> moved = model.move(List.of("a", "b", "c"), "b", -1);

        assertEquals(List.of("b", "a", "c"), moved);
        assertEquals(List.of("b", "a", "c"), model.currentOrder());
        assertEquals(List.of("a", "b", "c"), model.entryOrder());
        assertTrue(model.hasOrderChanges());
    }

    @Test
    void cancelRestoresEntryOrderAndCommitAcceptsCurrentOrder() {
        DlcFxPackOrderModel<String> model = new DlcFxPackOrderModel<>(value -> value);
        model.reset(List.of("a", "b", "c"));
        model.move(List.of("a", "b", "c"), "c", -1);

        model.cancel();

        assertEquals(List.of("a", "b", "c"), model.currentOrder());
        assertFalse(model.hasOrderChanges());

        model.move(List.of("a", "b", "c"), "b", 1);
        model.commit();

        assertEquals(List.of("a", "c", "b"), model.entryOrder());
        assertFalse(model.hasOrderChanges());
    }
}
