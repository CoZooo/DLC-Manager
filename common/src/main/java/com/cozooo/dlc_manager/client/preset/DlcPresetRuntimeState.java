package com.cozooo.dlc_manager.client.preset;

import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DlcPresetRuntimeState {
    private static final Map<String, DlcPresetEntry> PRESETS_BY_ID = new LinkedHashMap<>();
    private static final Set<String> ENABLED_PRESETS = new LinkedHashSet<>();
    private static final Set<String> LAST_APPLIED_IDENTIFIERS = new LinkedHashSet<>();
    private static Map<String, Set<String>> BLOCKED_DLC = new LinkedHashMap<>();
    private static Map<String, Set<String>> BLOCKED_PRESET = new LinkedHashMap<>();
    private static boolean presetDirty;

    private DlcPresetRuntimeState() {
    }

    public static void reload() {
        Set<String> previousEnabled = presetDirty ? new LinkedHashSet<>(ENABLED_PRESETS) : null;
        List<DlcPresetEntry> entries = DlcPresetLoader.loadAll();
        PRESETS_BY_ID.clear();
        for (DlcPresetEntry entry : entries) {
            if (entry != null && entry.getId() != null && !entry.getId().isBlank()) {
                PRESETS_BY_ID.put(entry.getId(), entry);
            }
        }
        ENABLED_PRESETS.clear();
        if (presetDirty && previousEnabled != null) {
            for (DlcPresetEntry entry : PRESETS_BY_ID.values()) {
                if (entry == null) {
                    continue;
                }
                if (previousEnabled.contains(entry.getId())) {
                    ENABLED_PRESETS.add(entry.getId());
                    entry.setStatus(DlcPresetStatus.ON);
                } else {
                    entry.setStatus(DlcPresetStatus.OFF);
                }
            }
        } else {
            Map<String, DlcPresetStatus> saved = DlcPresetStateStore.loadPresetStatus();
            for (DlcPresetEntry entry : PRESETS_BY_ID.values()) {
                if (entry == null) {
                    continue;
                }
                DlcPresetStatus status = saved.getOrDefault(entry.getId(), DlcPresetStatus.OFF);
                entry.setStatus(status);
                if (status.isEnabled()) {
                    ENABLED_PRESETS.add(entry.getId());
                }
            }
            presetDirty = false;
        }
        updateEntryStatusesFromRuntime();
        rebuildBlocks();
        applyOverrides();
    }

    public static List<DlcPresetEntry> getPresets() {
        return new ArrayList<>(PRESETS_BY_ID.values());
    }

    public static boolean isPresetEnabled(String id) {
        return id != null && ENABLED_PRESETS.contains(id);
    }

    public static DlcPresetStatus getPresetStatus(String id) {
        if (id == null || id.isBlank()) {
            return DlcPresetStatus.OFF;
        }
        DlcPresetEntry entry = PRESETS_BY_ID.get(id);
        if (entry == null) {
            return DlcPresetStatus.OFF;
        }
        if (!ENABLED_PRESETS.contains(id)) {
            return DlcPresetStatus.OFF;
        }
        return computeStatus(entry);
    }

    public static Set<String> getEnabledPresetIds() {
        return new LinkedHashSet<>(ENABLED_PRESETS);
    }

    public static void restoreEnabledPresets(Set<String> presetIds) {
        ENABLED_PRESETS.clear();
        if (presetIds != null) {
            for (String id : presetIds) {
                if (id != null && PRESETS_BY_ID.containsKey(id)) {
                    ENABLED_PRESETS.add(id);
                }
            }
        }
        updateEntryStatusesFromRuntime();
        rebuildBlocks();
        applyOverrides();
        presetDirty = false;
    }

    public static boolean setPresetEnabled(String id, boolean enabled) {
        if (id == null || id.isBlank()) {
            return false;
        }
        if (enabled) {
            if (isPresetBlocked(id)) {
                return false;
            }
            if (!ENABLED_PRESETS.add(id)) {
                return true;
            }
        } else {
            if (!ENABLED_PRESETS.remove(id)) {
                return true;
            }
        }
        updateEntryStatus(id, enabled ? DlcPresetStatus.ON : DlcPresetStatus.OFF);
        rebuildBlocks();
        applyOverrides();
        presetDirty = true;
        return true;
    }

    public static boolean isPresetBlocked(String presetId) {
        if (presetId == null || presetId.isBlank()) {
            return false;
        }
        if (ENABLED_PRESETS.contains(presetId)) {
            return false;
        }
        Set<String> blockers = BLOCKED_PRESET.get(presetId);
        return blockers != null && !blockers.isEmpty();
    }

    public static List<String> getPresetBlockerNames(String presetId, String lang) {
        Set<String> blockers = BLOCKED_PRESET.get(presetId);
        if (blockers == null || blockers.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String id : blockers) {
            DlcPresetEntry entry = PRESETS_BY_ID.get(id);
            if (entry != null) {
                names.add(entry.resolveDisplayName(lang));
            }
        }
        return names;
    }

    public static boolean isDlcBlocked(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return false;
        }
        Set<String> blockers = BLOCKED_DLC.get(identifier);
        return blockers != null && !blockers.isEmpty();
    }

    public static List<String> getDlcBlockerNames(String identifier, String lang) {
        Set<String> blockers = BLOCKED_DLC.get(identifier);
        if (blockers == null || blockers.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String id : blockers) {
            DlcPresetEntry entry = PRESETS_BY_ID.get(id);
            if (entry != null) {
                names.add(entry.resolveDisplayName(lang));
            }
        }
        return names;
    }

    public static void clearAll() {
        PRESETS_BY_ID.clear();
        ENABLED_PRESETS.clear();
        LAST_APPLIED_IDENTIFIERS.clear();
        BLOCKED_DLC = new LinkedHashMap<>();
        BLOCKED_PRESET = new LinkedHashMap<>();
        presetDirty = false;
    }

    public static void syncStatusFromRuntime() {
        if (PRESETS_BY_ID.isEmpty()) {
            return;
        }
        Map<String, DlcPresetStatus> statusMap = new LinkedHashMap<>();
        for (DlcPresetEntry entry : PRESETS_BY_ID.values()) {
            if (entry == null) {
                continue;
            }
            DlcPresetStatus status = ENABLED_PRESETS.contains(entry.getId()) ? computeStatus(entry) : DlcPresetStatus.OFF;
            if (status != entry.getStatus()) {
                entry.setStatus(status);
            }
            statusMap.put(entry.getId(), status);
        }
        DlcPresetStateStore.savePresetStatus(statusMap);
        presetDirty = false;
    }

    public static void syncStatusFromExternal(Map<String, DlcPresetStatus> statuses) {
        if (PRESETS_BY_ID.isEmpty()) {
            return;
        }
        if (statuses == null) {
            statuses = Map.of();
        }
        Map<String, DlcPresetStatus> normalized = new LinkedHashMap<>();
        ENABLED_PRESETS.clear();
        for (DlcPresetEntry entry : PRESETS_BY_ID.values()) {
            if (entry == null) {
                continue;
            }
            DlcPresetStatus status = statuses.getOrDefault(entry.getId(), DlcPresetStatus.OFF);
            entry.setStatus(status);
            normalized.put(entry.getId(), status);
            if (status.isEnabled()) {
                ENABLED_PRESETS.add(entry.getId());
            }
        }
        DlcPresetStateStore.savePresetStatus(normalized);
        presetDirty = false;
        rebuildBlocks();
    }

    public static void applyOverridesToRuntime() {
        if (PRESETS_BY_ID.isEmpty()) {
            return;
        }
        rebuildBlocks();
        applyOverrides();
    }

    private static void rebuildBlocks() {
        Map<String, Set<String>> dlcBlockers = new LinkedHashMap<>();
        Map<String, Set<String>> presetBlockers = new LinkedHashMap<>();
        for (String presetId : ENABLED_PRESETS) {
            DlcPresetEntry preset = PRESETS_BY_ID.get(presetId);
            if (preset == null) {
                continue;
            }
            for (String conflict : preset.getConflicts()) {
                if (conflict == null || conflict.isBlank()) {
                    continue;
                }
                dlcBlockers.computeIfAbsent(conflict, key -> new LinkedHashSet<>()).add(presetId);
            }
        }
        if (!dlcBlockers.isEmpty()) {
            for (DlcPresetEntry preset : PRESETS_BY_ID.values()) {
                if (preset == null) {
                    continue;
                }
                if (ENABLED_PRESETS.contains(preset.getId())) {
                    continue;
                }
                for (String enable : preset.getEnable()) {
                    if (enable == null || enable.isBlank()) {
                        continue;
                    }
                    Set<String> blockers = dlcBlockers.get(enable);
                    if (blockers != null && !blockers.isEmpty()) {
                        presetBlockers.computeIfAbsent(preset.getId(), key -> new LinkedHashSet<>()).addAll(blockers);
                    }
                }
            }
        }
        BLOCKED_DLC = dlcBlockers;
        BLOCKED_PRESET = presetBlockers;
    }

    private static DlcPresetStatus computeStatus(DlcPresetEntry entry) {
        if (entry == null) {
            return DlcPresetStatus.OFF;
        }
        if (!ENABLED_PRESETS.contains(entry.getId())) {
            return DlcPresetStatus.OFF;
        }
        boolean allEnabled = true;
        for (String identifier : entry.getEnable()) {
            if (identifier == null || identifier.isBlank()) {
                continue;
            }
            DlcItem item = DlcRuntimeState.getItemByIdentifier(identifier);
            if (item == null || !item.isEnabled()) {
                allEnabled = false;
                break;
            }
        }
        return allEnabled ? DlcPresetStatus.ON : DlcPresetStatus.PART;
    }

    private static void updateEntryStatusesFromRuntime() {
        for (DlcPresetEntry entry : PRESETS_BY_ID.values()) {
            if (entry == null) {
                continue;
            }
            DlcPresetStatus status = ENABLED_PRESETS.contains(entry.getId()) ? computeStatus(entry) : DlcPresetStatus.OFF;
            if (status != entry.getStatus()) {
                entry.setStatus(status);
            }
        }
    }

    private static void updateEntryStatus(String id, DlcPresetStatus status) {
        if (id == null) {
            return;
        }
        DlcPresetEntry entry = PRESETS_BY_ID.get(id);
        if (entry == null) {
            return;
        }
        if (status != entry.getStatus()) {
            entry.setStatus(status);
        }
    }

    private static void applyOverrides() {
        Set<String> desired = new LinkedHashSet<>();
        for (String presetId : ENABLED_PRESETS) {
            DlcPresetEntry preset = PRESETS_BY_ID.get(presetId);
            if (preset == null) {
                continue;
            }
            boolean partial = preset.getStatus() == DlcPresetStatus.PART;
            for (String enable : preset.getEnable()) {
                if (enable == null || enable.isBlank()) {
                    continue;
                }
                if (partial) {
                    DlcItem item = DlcRuntimeState.getItemByIdentifier(enable);
                    if (item != null && item.isEnabled()) {
                        desired.add(enable);
                    }
                } else {
                    desired.add(enable);
                }
            }
        }

        Set<String> toDisable = new LinkedHashSet<>(LAST_APPLIED_IDENTIFIERS);
        toDisable.removeAll(desired);

        for (String identifier : desired) {
            DlcItem item = DlcRuntimeState.getItemByIdentifier(identifier);
            if (item != null) {
                DlcRuntimeState.setOverride(item.getId(), true);
            }
        }
        for (String identifier : toDisable) {
            DlcItem item = DlcRuntimeState.getItemByIdentifier(identifier);
            if (item != null) {
                DlcRuntimeState.setOverride(item.getId(), false);
            }
        }

        LAST_APPLIED_IDENTIFIERS.clear();
        LAST_APPLIED_IDENTIFIERS.addAll(desired);
    }
}
