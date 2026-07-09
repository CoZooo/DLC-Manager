package com.cozooo.dlc_manager.core;

public enum DlcType {
    MOD("dlc_manager.group.mods"),
    RESOURCE_PACK("dlc_manager.group.resource_packs"),
    DATA_PACK("dlc_manager.group.data_packs"),
    TACZ_PACK("dlc_manager.group.tacz_packs"),
    DLC_PACK("dlc_manager.group.dlc_packs"),
    UNKNOWN("dlc_manager.group.other");

    private final String displayKey;

    DlcType(String displayKey) {
        this.displayKey = displayKey;
    }

    public String getDisplayKey() {
        return displayKey;
    }
}
