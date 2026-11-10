package com.cozooo.dlc_manager.client.sync

import com.cozooo.dlc_manager.sync.DlcSyncFailure

import com.cozooo.dlc_manager.client.preset.*
import com.cozooo.dlc_manager.core.*
import java.io.IOException
import java.util.function.BooleanSupplier

internal object DlcSyncPresets {
    fun apply(identifier: String, enabled: Boolean?, canceled: BooleanSupplier): Boolean {
        if (!DlcConfig.isPresetFuncOn()) throw DlcSyncFailure("presets_disabled")
        val items = DlcScanner().scan(false).values.filterNotNull().flatMap { it.filterNotNull() }
        DlcRuntimeState.updateItemIndex(items)
        for (item in items) DlcRuntimeState.ensureBaseline(item.getId(), item.isEnabled())
        DlcPresetRuntimeState.reload(false)
        if (!DlcPresetRuntimeState.hasPreset(identifier)) throw DlcSyncFailure("preset_missing", "$identifier")
        val desired = enabled ?: DlcPresetRuntimeState.isPresetEnabled(identifier)
        if (!DlcPresetRuntimeState.setPresetEnabled(identifier, desired)) throw DlcSyncFailure("preset_blocked", "$identifier")
        var restart = false
        for (item in items) {
            if (canceled.asBoolean) throw DlcSyncFailure("canceled")
            val override = DlcRuntimeState.getOverride(item.getId()) ?: continue
            if (override == item.isEnabled()) continue
            item.setEnabled(override); DlcApplier.applySynced(item); restart = true
        }
        if (canceled.asBoolean) throw DlcSyncFailure("canceled")
        restart = DlcApplier.applyPresetConflictsSynced() || restart
        DlcRuntimeState.updateItemIndex(DlcScanner().scan(false).values.filterNotNull().flatMap { it.filterNotNull() })
        DlcPresetRuntimeState.syncStatusFromRuntime()
        if (DlcPresetStateStore.loadPresetStatus().getOrDefault(identifier, DlcPresetStatus.OFF).isEnabled() != desired) throw DlcSyncFailure("preset_state_save", "$identifier")
        return restart
    }
    fun reload() {
        val enabled = DlcPresetStateStore.loadPresetStatus().filterValues { it.isEnabled() }.keys
        DlcPresetRuntimeState.restoreEnabledPresets(enabled)
        DlcPresetRuntimeState.reload(false)
    }
}
