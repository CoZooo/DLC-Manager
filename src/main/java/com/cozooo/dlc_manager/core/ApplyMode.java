package com.cozooo.dlc_manager.core;

public enum ApplyMode {
    RESTART_ONLY("dlc_manager.apply_mode.restart_load"),
    TRY_HOT_RELOAD("dlc_manager.apply_mode.reload_resources");

    private final String labelKey;

    ApplyMode(String labelKey) {
        this.labelKey = labelKey;
    }

    public String getLabelKey() {
        return labelKey;
    }
}
