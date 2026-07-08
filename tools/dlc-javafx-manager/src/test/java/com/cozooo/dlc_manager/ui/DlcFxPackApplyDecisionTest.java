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

final class DlcFxPackApplyDecisionTest {
    @Test
    void disabledPackFunctionStopsAndReportsDisabled() {
        DlcFxPackApplyDecision decision = DlcFxPackApplyDecision.decide(false, false, true, true, "", "");

        assertEquals(DlcFxPackApplyDecision.Action.DISABLED, decision.action());
    }

    @Test
    void busyOrNoPendingChangesDoesNothing() {
        assertEquals(DlcFxPackApplyDecision.Action.NOOP,
            DlcFxPackApplyDecision.decide(true, true, true, true, "", "").action());
        assertEquals(DlcFxPackApplyDecision.Action.NOOP,
            DlcFxPackApplyDecision.decide(true, false, false, true, "", "").action());
    }

    @Test
    void orderOnlyChangesCommitWithoutRuntimeApply() {
        DlcFxPackApplyDecision decision = DlcFxPackApplyDecision.decide(true, false, true, false, "", "");

        assertEquals(DlcFxPackApplyDecision.Action.COMMIT_ORDER_ONLY, decision.action());
    }

    @Test
    void replacementConfirmationTakesPrecedenceOverRuntimeConfirmation() {
        DlcFxPackApplyDecision decision = DlcFxPackApplyDecision.decide(true, false, true, true, "replace", "runtime");

        assertEquals(DlcFxPackApplyDecision.Action.CONFIRM_REPLACEMENT, decision.action());
        assertEquals("replace", decision.message());
        assertFalse(decision.allowRuntimeChanges());
    }

    @Test
    void runtimeConfirmationStartsApplyWithRuntimePermissionAfterConfirmation() {
        DlcFxPackApplyDecision decision = DlcFxPackApplyDecision.decide(true, false, true, true, "", "runtime");

        assertEquals(DlcFxPackApplyDecision.Action.CONFIRM_RUNTIME, decision.action());
        assertEquals("runtime", decision.message());
        assertTrue(decision.allowRuntimeChanges());
    }

    @Test
    void operationChangesWithoutConfirmationsStartStrictApply() {
        DlcFxPackApplyDecision decision = DlcFxPackApplyDecision.decide(true, false, true, true, "", "");

        assertEquals(DlcFxPackApplyDecision.Action.START_APPLY, decision.action());
        assertFalse(decision.allowRuntimeChanges());
    }
}
