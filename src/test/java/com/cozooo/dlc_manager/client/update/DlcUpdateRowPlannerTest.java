package com.cozooo.dlc_manager.client.update;

import com.cozooo.dlc_manager.client.server.DlcServerUpdateInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcUpdateRowPlannerTest {
    @Test
    void visibleOnlyFiltersHiddenAndNullHistory() {
        DlcUpdateMeta visible = meta("1.0.0", "1.0.1", "1.0.0", null, false);
        DlcUpdateMeta hidden = meta("1.0.1", "1.0.2", "1.0.0", null, true);

        assertEquals(List.of(visible), DlcUpdateRowPlanner.visibleOnly(List.of(visible, hidden)));
        assertEquals(List.of(), DlcUpdateRowPlanner.visibleOnly(null));
    }

    @Test
    void serverRowsIncludeVisibleHistoryRollbackRowsAndVisibleUpdateRows() {
        DlcUpdateMeta history = meta("1.0.0", "1.0.1", "1.0.0", "1.1.0", false);
        DlcServerUpdateInfo update = new DlcServerUpdateInfo();
        update.dlcOld = "1.0.1";
        update.dlcNew = "1.0.2";
        update.baseOld = "1.1.0";
        update.baseNew = "1.1.0";
        update.changelog = Map.of("en_us", "Update log");
        DlcServerUpdateInfo hidden = new DlcServerUpdateInfo();
        hidden.hidden = true;

        DlcUpdateRowPlanner.PlanResult result = DlcUpdateRowPlanner.planServerRows(
            List.of(history),
            List.of(update, hidden),
            false,
            "1.1.0",
            "1.0.1"
        );

        assertTrue(result.hasVisibleUpdates);
        assertEquals(2, result.rows.size());
        assertEquals(DlcUpdateRowPlanner.Action.ROLLBACK, result.rows.get(0).action);
        assertEquals("1.0.0", result.rows.get(0).oldVersion);
        assertEquals("1.0.1", result.rows.get(0).newVersion);
        assertEquals("1.0.0+1.0.0", result.rows.get(0).rollbackTarget);
        assertEquals("1.0.0+1.0.0", result.rows.get(0).displayOldVersion);
        assertEquals("1.1.0+1.0.1", result.rows.get(0).displayNewVersion);
        assertEquals(DlcUpdateRowPlanner.Action.UPDATE, result.rows.get(1).action);
        assertEquals("Update log", result.rows.get(1).meta.resolveChangelog("en_us"));
    }

    @Test
    void rolledBackModeDoesNotShowNewUpdatesAndMarksFutureHistoryAsReupdate() {
        DlcUpdateMeta futureHistory = meta("1.0.0", "1.0.2", "1.2.0", null, false);
        DlcServerUpdateInfo update = new DlcServerUpdateInfo();
        update.dlcOld = "1.0.2";
        update.dlcNew = "1.0.3";
        update.baseOld = "1.2.0";

        DlcUpdateRowPlanner.PlanResult result = DlcUpdateRowPlanner.planServerRows(
            List.of(futureHistory),
            List.of(update),
            true,
            "1.0.0",
            "1.0.0"
        );

        assertFalse(result.hasVisibleUpdates);
        assertEquals(1, result.rows.size());
        assertEquals(DlcUpdateRowPlanner.Action.REUPDATE, result.rows.get(0).action);
    }

    private static DlcUpdateMeta meta(String dlcOld, String dlcNew, String baseOld, String baseNew, boolean hidden) {
        DlcUpdateMeta meta = new DlcUpdateMeta();
        meta.setDlcOld(dlcOld);
        meta.setDlcNew(dlcNew);
        meta.setBaseOld(baseOld);
        meta.setBaseNew(baseNew);
        meta.setHidden(hidden);
        return meta;
    }
}
