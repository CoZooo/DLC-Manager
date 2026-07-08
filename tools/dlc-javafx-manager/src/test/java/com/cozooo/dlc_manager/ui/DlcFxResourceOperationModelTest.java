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

final class DlcFxResourceOperationModelTest {
    @Test
    void tracksBatchProgressAndCancellation() {
        DlcFxResourceOperationModel model = new DlcFxResourceOperationModel();

        model.beginBatch(List.of("a", "b"));
        model.setProgress("a", 0.25f);
        model.setProgress("b", 0.75f);

        assertTrue(model.isBatchActive("a"));
        assertEquals(0.5f, model.batchProgress(), 0.0001f);
        assertFalse(model.isBatchCancelRequested());

        model.requestBatchCancel();

        assertTrue(model.isBatchCancelRequested());
    }

    @Test
    void tracksSingleOperationAndCleansUpWithoutRemovingBatchProgress() {
        DlcFxResourceOperationModel model = new DlcFxResourceOperationModel();
        model.beginBatch(List.of("a"));
        model.beginSingle("a");

        model.requestSingleCancel("a");

        assertTrue(model.isSingleCancelRequested("a"));

        model.finishSingle("a");

        assertFalse(model.isSingleActive("a"));
        assertTrue(model.isBatchActive("a"));
        assertEquals(0f, model.progress("a"), 0.0001f);
    }

    @Test
    void resetClearsAllOperationState() {
        DlcFxResourceOperationModel model = new DlcFxResourceOperationModel();
        model.beginBatch(List.of("a"));
        model.beginSingle("b");
        model.requestBatchCancel();

        model.reset();

        assertFalse(model.isBatchActive("a"));
        assertFalse(model.isSingleActive("b"));
        assertFalse(model.isBatchCancelRequested());
    }
}
