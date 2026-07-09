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

final class DlcFxRequiredOperationModelTest {
    @Test
    void tracksDownloadAndInstallPhasesSeparately() {
        DlcFxRequiredOperationModel model = new DlcFxRequiredOperationModel();

        model.beginBatch(List.of("a", "b"));
        model.markDownloading("a", 0.5f);
        model.markInstalling("a");

        assertTrue(model.isActive("a"));
        assertTrue(model.isInstalling("a"));
        assertEquals(1f, model.progress("a"), 0.0001f);
        assertEquals("100%", model.progressText("a"));
        assertEquals(0.5f, model.batchProgress(), 0.0001f);
    }

    @Test
    void requestCancelStopsFutureWorkButKeepsActiveProgressUntilFinished() {
        DlcFxRequiredOperationModel model = new DlcFxRequiredOperationModel();
        model.beginBatch(List.of("a"));
        model.markDownloading("a", 0.25f);

        model.requestCancel("a");

        assertTrue(model.isCancelRequested("a"));
        assertTrue(model.isActive("a"));
        assertEquals(0.25f, model.progress("a"), 0.0001f);

        model.finish("a");

        assertFalse(model.isActive("a"));
        assertFalse(model.isCancelRequested("a"));
    }

    @Test
    void appliesRemoteItemProgressByBytes() {
        DlcFxRequiredOperationModel model = new DlcFxRequiredOperationModel();
        model.beginBatch(List.of("a", "b"));

        model.applyProgress("a", 25, 100, false);
        model.applyProgress("b", 100, 100, true);

        assertEquals(0.25f, model.progress("a"), 0.0001f);
        assertEquals(1f, model.progress("b"), 0.0001f);
        assertFalse(model.isActive("b"));
        assertEquals(0.625f, model.batchProgress(), 0.0001f);
    }
}
