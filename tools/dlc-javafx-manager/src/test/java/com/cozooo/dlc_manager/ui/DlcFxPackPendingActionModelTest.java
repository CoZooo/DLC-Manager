package com.cozooo.dlc_manager.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DlcFxPackPendingActionModelTest {
    @Test
    void togglesInstalledSameVersionAsPendingUninstall() {
        DlcFxPackPendingActionModel<Entry> model = model();
        Entry installed = new Entry("rail", "1.0.0");

        model.toggle(installed, installed);

        assertTrue(model.pendingUninstallIds().contains("rail"));
        assertEquals(DlcFxPackPendingActionModel.Action.PENDING_UNINSTALL, model.action(installed, installed));

        model.toggle(installed, installed);

        assertFalse(model.hasOperationChanges());
    }

    @Test
    void togglesMissingEntryAsPendingInstall() {
        DlcFxPackPendingActionModel<Entry> model = model();
        Entry target = new Entry("rail", "1.0.0");

        model.toggle(target, null);

        assertEquals(target, model.pendingInstalls().get("rail"));
        assertEquals(DlcFxPackPendingActionModel.Action.PENDING_INSTALL, model.action(target, null));

        model.toggle(target, null);

        assertFalse(model.hasOperationChanges());
    }

    @Test
    void classifiesReplacementAsUpgradeOrDowngrade() {
        DlcFxPackPendingActionModel<Entry> model = model();
        Entry installed = new Entry("rail", "1.0.0");
        Entry newer = new Entry("rail", "2.0.0");
        Entry older = new Entry("rail", "0.9.0");

        model.toggle(newer, installed);
        assertEquals(DlcFxPackPendingActionModel.Action.PENDING_UPGRADE, model.action(newer, installed));

        model.clear();
        model.toggle(older, installed);
        assertEquals(DlcFxPackPendingActionModel.Action.PENDING_DOWNGRADE, model.action(older, installed));
    }

    @Test
    void supportsInstalledVersionWithoutInstalledEntryObject() {
        DlcFxPackPendingActionModel<Entry> model = model();
        Entry newer = new Entry("rail", "2.0.0");

        model.toggleWithInstalledVersion(newer, "1.0.0");

        assertEquals(DlcFxPackPendingActionModel.Action.PENDING_UPGRADE, model.actionWithInstalledVersion(newer, "1.0.0"));
    }

    @Test
    void mapsActionsToSharedTranslationKeys() {
        assertEquals("dlc_manager.pack.install", DlcFxPackPendingActionModel.translationKey(DlcFxPackPendingActionModel.Action.INSTALL));
        assertEquals("dlc_manager.pack.pending_uninstall", DlcFxPackPendingActionModel.translationKey(DlcFxPackPendingActionModel.Action.PENDING_UNINSTALL));
    }

    private static DlcFxPackPendingActionModel<Entry> model() {
        return new DlcFxPackPendingActionModel<>(entry -> entry.id, entry -> entry.version);
    }

    private record Entry(String id, String version) {
    }
}
