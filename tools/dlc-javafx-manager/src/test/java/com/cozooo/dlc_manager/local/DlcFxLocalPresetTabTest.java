package com.cozooo.dlc_manager.local;

import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import com.cozooo.dlc_manager.core.DlcType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcFxLocalPresetTabTest {
    @AfterEach
    void clearOverrides() {
        DlcRuntimeState.clearOverrides();
        DlcRuntimeState.updateItemIndex(List.of());
    }

    @Test
    void presetOverrideMatchingBaselineDoesNotCountAsPendingDlcChange() {
        DlcItem item = item("fx-preset-no-change-id", "fx-preset-no-change", false);
        DlcRuntimeState.ensureBaseline(item.getId(), false);
        DlcRuntimeState.updateItemIndex(List.of(item));
        DlcRuntimeState.setOverride(item.getId(), false);

        assertFalse(DlcFxLocalPresetTab.syncIndexedDlcOverridesAndHasPendingChanges());
        assertFalse(item.isEnabled());
    }

    @Test
    void presetOverrideDifferentFromBaselineCountsAsPendingDlcChange() {
        DlcItem item = item("fx-preset-change-id", "fx-preset-change", false);
        DlcRuntimeState.ensureBaseline(item.getId(), false);
        DlcRuntimeState.updateItemIndex(List.of(item));
        DlcRuntimeState.setOverride(item.getId(), true);

        assertTrue(DlcFxLocalPresetTab.syncIndexedDlcOverridesAndHasPendingChanges());
        assertTrue(item.isEnabled());
        assertTrue(DlcRuntimeState.isBaselineDifferent(item.getId(), item.isEnabled()));
    }

    private static DlcItem item(String id, String identifier, boolean enabled) {
        return new DlcItem(
            id,
            identifier,
            identifier,
            DlcType.MOD,
            Path.of("mods", identifier + ".jar"),
            "",
            enabled,
            Map.of(),
            Map.of(),
            List.of(),
            List.of(),
            true,
            false
        );
    }
}
