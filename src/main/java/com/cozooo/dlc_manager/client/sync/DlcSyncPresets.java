package com.cozooo.dlc_manager.client.sync;

import com.cozooo.dlc_manager.sync.DlcSyncFailure;

import com.cozooo.dlc_manager.client.preset.*;
import com.cozooo.dlc_manager.core.*;
import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;

final class DlcSyncPresets {
    private DlcSyncPresets() {}
    static boolean apply(String identifier, Boolean enabled, BooleanSupplier canceled) throws IOException {
        if (!DlcConfig.isPresetFuncOn()) throw new DlcSyncFailure("presets_disabled");
        List<DlcItem> items = new DlcScanner().scan(false).values().stream().flatMap(List::stream).toList();
        DlcRuntimeState.updateItemIndex(items);
        for (DlcItem item : items) DlcRuntimeState.ensureBaseline(item.getId(), item.isEnabled());
        DlcPresetRuntimeState.reload(false);
        if (!DlcPresetRuntimeState.hasPreset(identifier)) throw new DlcSyncFailure("preset_missing", identifier);
        boolean desired = enabled == null ? DlcPresetRuntimeState.isPresetEnabled(identifier) : enabled;
        if (!DlcPresetRuntimeState.setPresetEnabled(identifier, desired)) throw new DlcSyncFailure("preset_blocked", identifier);
        boolean restart = false;
        for (DlcItem item : items) {
            if (canceled.getAsBoolean()) throw new DlcSyncFailure("canceled");
            Boolean override = DlcRuntimeState.getOverride(item.getId());
            if (override == null || override == item.isEnabled()) continue;
            item.setEnabled(override); DlcApplier.applySynced(item); restart = true;
        }
        if (canceled.getAsBoolean()) throw new DlcSyncFailure("canceled");
        restart |= DlcApplier.applyPresetConflictsSynced();
        DlcRuntimeState.updateItemIndex(new DlcScanner().scan(false).values().stream().flatMap(List::stream).toList());
        DlcPresetRuntimeState.syncStatusFromRuntime();
        if (DlcPresetStateStore.loadPresetStatus().getOrDefault(identifier, DlcPresetStatus.OFF).isEnabled() != desired) throw new DlcSyncFailure("preset_state_save", identifier);
        return restart;
    }
    static void reload() {
        Set<String> enabled = new LinkedHashSet<>();
        DlcPresetStateStore.loadPresetStatus().forEach((id, state) -> { if (state.isEnabled()) enabled.add(id); });
        DlcPresetRuntimeState.restoreEnabledPresets(enabled);
        DlcPresetRuntimeState.reload(false);
    }
}
