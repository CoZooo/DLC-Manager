package com.cozooo.dlc_manager.ui;

import com.cozooo.dlc_manager.ui.common.*;
import com.cozooo.dlc_manager.ui.management.*;
import com.cozooo.dlc_manager.ui.packs.*;
import com.cozooo.dlc_manager.ui.remote.*;
import com.cozooo.dlc_manager.ui.required.*;
import com.cozooo.dlc_manager.ui.update.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DlcFxPackDeletePlanTest {
    @Test
    void uninstalledPackUsesPlainDeleteConfirmationAndSuccessKey() {
        DlcFxPackDeletePlan plan = DlcFxPackDeletePlan.of("Pack", false);

        assertEquals("dlc_manager.pack.delete_archive_title", plan.titleKey());
        assertEquals("dlc_manager.pack.delete_confirm", plan.confirmMessageKey());
        assertEquals("Pack", plan.displayName());
        assertEquals("dlc_manager.status.deleting", plan.busyStatusKey());
        assertEquals("dlc_manager.pack.delete_success", plan.successKey());
    }

    @Test
    void installedPackUsesUninstallBeforeDeleteConfirmationAndSuccessKey() {
        DlcFxPackDeletePlan plan = DlcFxPackDeletePlan.of("Pack", true);

        assertEquals("dlc_manager.pack.delete_confirm_uninstall", plan.confirmMessageKey());
        assertEquals("dlc_manager.pack.operation_uninstalling", plan.busyStatusKey());
        assertEquals("dlc_manager.pack.delete_uninstalled_success", plan.successKey());
    }
}
