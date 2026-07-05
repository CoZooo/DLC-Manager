package com.cozooo.dlc_manager.ui.packs;

public record DlcFxPackActionState(
    boolean refreshDisabled,
    boolean importDisabled,
    boolean importUrlDisabled,
    boolean applyDisabled,
    boolean cancelDisabled,
    boolean cancelVisible,
    String applyTextKey,
    String cancelTextKey
) {
    public static DlcFxPackActionState compute(boolean available, boolean busy, boolean hasPendingChanges, boolean busyApplyCanControl) {
        if (!available) {
            return idle(true, true, true, true, false, false);
        }
        if (busy) {
            return new DlcFxPackActionState(
                true,
                true,
                true,
                !busyApplyCanControl,
                false,
                true,
                busyApplyCanControl ? "dlc_manager.pack.pause_install" : "dlc_manager.pack.apply",
                "dlc_manager.pack.cancel_install"
            );
        }
        return idle(false, false, false, !hasPendingChanges, false, hasPendingChanges);
    }

    public static DlcFxPackActionState computeBusy(boolean applying,
                                                   boolean paused,
                                                   boolean cancelRequested,
                                                   boolean cancelApplyRequested,
                                                   boolean deleting) {
        String applyText = "dlc_manager.pack.apply";
        if (paused && applying) {
            applyText = "dlc_manager.pack.continue_install";
        } else if (applying && cancelRequested && !cancelApplyRequested) {
            applyText = "dlc_manager.pack.pausing";
        } else if (applying) {
            applyText = "dlc_manager.pack.pause_install";
        }
        return new DlcFxPackActionState(
            true,
            true,
            true,
            !applying || !paused && cancelApplyRequested,
            deleting || cancelRequested && !applying,
            true,
            applyText,
            applying
                ? "dlc_manager.pack.cancel_install"
                : cancelRequested ? "dlc_manager.pack.canceling" : "dlc_manager.pack.cancel"
        );
    }

    private static DlcFxPackActionState idle(boolean refreshDisabled,
                                            boolean importDisabled,
                                            boolean importUrlDisabled,
                                            boolean applyDisabled,
                                            boolean cancelDisabled,
                                            boolean cancelVisible) {
        return new DlcFxPackActionState(
            refreshDisabled,
            importDisabled,
            importUrlDisabled,
            applyDisabled,
            cancelDisabled,
            cancelVisible,
            "dlc_manager.pack.apply",
            "dlc_manager.pack.cancel_changes"
        );
    }
}
