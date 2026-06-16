package com.cozooo.dlc_manager.core

enum class DlcType(private val displayKey: String) {
    MOD("dlc_manager.group.mods"),
    RESOURCE_PACK("dlc_manager.group.resource_packs"),
    DATA_PACK("dlc_manager.group.data_packs"),
    TACZ_PACK("dlc_manager.group.tacz_packs"),
    DLC_PACK("dlc_manager.group.dlc_packs"),
    UNKNOWN("dlc_manager.group.other"),
    ;

    fun getDisplayKey(): String {
        return displayKey
    }
}
