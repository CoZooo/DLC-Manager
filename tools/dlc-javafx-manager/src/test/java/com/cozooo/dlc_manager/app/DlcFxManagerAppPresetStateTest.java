package com.cozooo.dlc_manager.app;

import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import com.cozooo.dlc_manager.core.DlcType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class DlcFxManagerAppPresetStateTest {
    @AfterEach
    void clearRuntime() {
        DlcRuntimeState.clearOverrides();
        DlcRuntimeState.updateItemIndex(List.of());
    }

    @Test
    void presetOwnedDlcIsDisabledWhenNoEnabledPresetStillNeedsIt() {
        DlcFxManagerApp app = new DlcFxManagerApp();
        DlcItem item = item("fx-owned-dlc-id", "fx-owned-dlc", false);
        DlcRuntimeState.ensureBaseline(item.getId(), false);
        DlcRuntimeState.updateItemIndex(List.of(item));
        DlcRuntimeState.updateBaseline(item.getId(), true);
        item.setEnabled(true);
        app.rememberLocalPresetOwnedDlcForTest(item.getId());

        app.applyLocalPresetOwnedDlcOverrides();

        assertFalse(item.isEnabled());
        assertEquals(Boolean.FALSE, DlcRuntimeState.getOverride(item.getId()));
    }

    @Test
    void restoringPresetSnapshotClearsPresetOwnedDlcTracking() {
        DlcFxManagerApp app = new DlcFxManagerApp();
        DlcItem item = item("fx-owned-cancel-id", "fx-owned-cancel", true);
        DlcRuntimeState.updateItemIndex(List.of(item));
        app.rememberLocalPresetOwnedDlcForTest(item.getId());

        app.restoreLocalPresetSnapshotForTest();

        assertFalse(app.hasLocalPresetOwnedDlcForTest(item.getId()));
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
