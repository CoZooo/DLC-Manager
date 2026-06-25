package com.cozooo.dlc_manager.core;

public enum DlcApplyMode {
    RESTART_ONLY("dlc_manager.apply_mode.restart_load"),
    TRY_HOT_RELOAD("dlc_manager.apply_mode.reload_resources");

    private final String labelKey;

    DlcApplyMode(String labelKey) {
        this.labelKey = labelKey;
    }

    public String getLabelKey() {
        return labelKey;
    }
}
