package com.cozooo.dlc_manager.ui.packs;
import com.cozooo.dlc_manager.ui.common.DlcFxI18n;

public record DlcFxPackDeletePlan(
    String displayName,
    boolean uninstallFirst,
    String titleKey,
    String confirmMessageKey,
    String busyStatusKey,
    String successKey
) {
    public static DlcFxPackDeletePlan of(String displayName, boolean uninstallFirst) {
        return new DlcFxPackDeletePlan(
            displayName == null ? "" : displayName,
            uninstallFirst,
            "dlc_manager.pack.delete_archive_title",
            uninstallFirst ? "dlc_manager.pack.delete_confirm_uninstall" : "dlc_manager.pack.delete_confirm",
            uninstallFirst ? "dlc_manager.pack.operation_uninstalling" : "dlc_manager.status.deleting",
            uninstallFirst ? "dlc_manager.pack.delete_uninstalled_success" : "dlc_manager.pack.delete_success"
        );
    }

    public String title() {
        return DlcFxI18n.tr(titleKey);
    }

    public String confirmMessage() {
        return DlcFxI18n.tr(confirmMessageKey, displayName);
    }

    public String busyStatus() {
        return DlcFxI18n.tr(busyStatusKey);
    }

    public String successMessage() {
        return DlcFxI18n.tr(successKey, displayName);
    }
}
