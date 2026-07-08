package com.cozooo.dlc_manager.core

enum class DlcApplyMode(private val labelKey: String) {
    RESTART_ONLY("dlc_manager.apply_mode.restart_load"),
    TRY_HOT_RELOAD("dlc_manager.apply_mode.reload_resources"),
    ;

    fun getLabelKey(): String {
        return labelKey
    }
}
