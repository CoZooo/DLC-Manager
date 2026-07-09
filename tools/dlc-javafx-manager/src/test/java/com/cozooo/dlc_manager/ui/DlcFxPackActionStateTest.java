package com.cozooo.dlc_manager.ui;

import com.cozooo.dlc_manager.ui.common.*;
import com.cozooo.dlc_manager.ui.management.*;
import com.cozooo.dlc_manager.ui.packs.*;
import com.cozooo.dlc_manager.ui.remote.*;
import com.cozooo.dlc_manager.ui.required.*;
import com.cozooo.dlc_manager.ui.update.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcFxPackActionStateTest {
    @Test
    void disablesAllActionsWhenUnavailable() {
        DlcFxPackActionState state = DlcFxPackActionState.compute(false, false, false, false);

        assertTrue(state.refreshDisabled());
        assertTrue(state.importDisabled());
        assertTrue(state.importUrlDisabled());
        assertTrue(state.applyDisabled());
        assertFalse(state.cancelVisible());
    }

    @Test
    void allowsRefreshAndImportWhenAvailableWithoutChanges() {
        DlcFxPackActionState state = DlcFxPackActionState.compute(true, false, false, false);

        assertFalse(state.refreshDisabled());
        assertFalse(state.importDisabled());
        assertFalse(state.importUrlDisabled());
        assertTrue(state.applyDisabled());
        assertFalse(state.cancelVisible());
    }

    @Test
    void showsApplyAndCancelOnlyWhenThereArePendingChanges() {
        DlcFxPackActionState state = DlcFxPackActionState.compute(true, false, true, false);

        assertFalse(state.applyDisabled());
        assertTrue(state.cancelVisible());
        assertFalse(state.cancelDisabled());
    }

    @Test
    void busyApplyCanExposePauseResumeControls() {
        DlcFxPackActionState state = DlcFxPackActionState.compute(true, true, true, true);

        assertTrue(state.refreshDisabled());
        assertTrue(state.importDisabled());
        assertFalse(state.applyDisabled());
        assertTrue(state.cancelVisible());
    }

    @Test
    void idleTextUsesApplyAndDiscardChangesKeys() {
        DlcFxPackActionState state = DlcFxPackActionState.compute(true, false, true, false);

        assertEquals("dlc_manager.pack.apply", state.applyTextKey());
        assertEquals("dlc_manager.pack.cancel_changes", state.cancelTextKey());
    }

    @Test
    void busyPausedTextUsesContinueAndCancelInstallKeys() {
        DlcFxPackActionState state = DlcFxPackActionState.computeBusy(true, true, false, false, false);

        assertEquals("dlc_manager.pack.continue_install", state.applyTextKey());
        assertEquals("dlc_manager.pack.cancel_install", state.cancelTextKey());
        assertFalse(state.applyDisabled());
        assertFalse(state.cancelDisabled());
        assertTrue(state.cancelVisible());
    }

    @Test
    void busyCancelingTextDisablesCancel() {
        DlcFxPackActionState state = DlcFxPackActionState.computeBusy(false, false, true, false, false);

        assertEquals("dlc_manager.pack.apply", state.applyTextKey());
        assertEquals("dlc_manager.pack.canceling", state.cancelTextKey());
        assertTrue(state.cancelDisabled());
    }

    @Test
    void deleteBusyStateCannotCancel() {
        DlcFxPackActionState state = DlcFxPackActionState.computeBusy(false, false, false, false, true);

        assertTrue(state.cancelDisabled());
        assertTrue(state.cancelVisible());
    }

    @Test
    void busyPausingTextUsesLocalPausingKey() {
        DlcFxPackActionState state = DlcFxPackActionState.computeBusy(true, false, true, false, false);

        assertEquals("dlc_manager.pack.pausing", state.applyTextKey());
        assertEquals("dlc_manager.pack.cancel_install", state.cancelTextKey());
        assertFalse(state.cancelDisabled());
    }

    @Test
    void busyCancelApplyRequestedDisablesApplyWhileApplying() {
        DlcFxPackActionState state = DlcFxPackActionState.computeBusy(true, false, true, true, false);

        assertTrue(state.applyDisabled());
        assertEquals("dlc_manager.pack.pause_install", state.applyTextKey());
    }
}
