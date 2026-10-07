package com.cozooo.dlc_manager.ui.packs;

import com.cozooo.dlc_manager.ui.common.*;
import com.cozooo.dlc_manager.ui.management.*;
import com.cozooo.dlc_manager.ui.packs.*;
import com.cozooo.dlc_manager.ui.remote.*;
import com.cozooo.dlc_manager.ui.required.*;
import com.cozooo.dlc_manager.ui.update.*;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcFxPackRowStateTest {
    @Test
    void exposesOrderButtonVisibilityWithReservedSlots() {
        DlcFxPackRowState first = DlcFxPackRowState.of(0, 3, 1, Map.of(), false);
        DlcFxPackRowState middle = DlcFxPackRowState.of(1, 3, 1, Map.of(), false);
        DlcFxPackRowState last = DlcFxPackRowState.of(2, 3, 1, Map.of(), false);

        assertFalse(first.showMoveUp());
        assertTrue(first.showMoveDown());
        assertTrue(middle.showMoveUp());
        assertTrue(middle.showMoveDown());
        assertTrue(last.showMoveUp());
        assertFalse(last.showMoveDown());
    }

    @Test
    void derivesLinkAndVersionVisibility() {
        DlcFxPackRowState state = DlcFxPackRowState.of(0, 1, 2, Map.of(
            "homepage", "https://example.test",
            "issue", "",
            "sponsor", "https://sponsor.test"
        ), true);

        assertTrue(state.showHomepageHint());
        assertFalse(state.showIssueAction());
        assertTrue(state.showSponsorAction());
        assertTrue(state.canSwitchVersion());
        assertTrue(state.currentVersion());
    }
}
