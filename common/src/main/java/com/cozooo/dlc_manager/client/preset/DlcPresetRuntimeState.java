package com.cozooo.dlc_manager.client.preset;

import com.cozooo.dlc_manager.core.DlcItem;
import com.cozooo.dlc_manager.core.DlcPaths;
import com.cozooo.dlc_manager.core.DlcRuntimeState;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DlcPresetRuntimeState {
    private static final String PRESET_REF_PREFIX = "preset:";
    private static final Map<String, DlcPresetEntry> PRESETS_BY_ID = new LinkedHashMap<>();
    private static final Set<String> ENABLED_PRESETS = new LinkedHashSet<>();
    private static final Set<String> LAST_APPLIED_IDENTIFIERS = new LinkedHashSet<>();
    private static final Map<String, Set<String>> APPLIED_DISABLED_RELATIVE_PATHS = new LinkedHashMap<>();
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
            APPLIED_DISABLED_RELATIVE_PATHS.clear();
            presetDirty = false;
        }
        filterAppliedDisabledRelativePaths();
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
        rollbackLastAppliedToBaseline();
        PRESETS_BY_ID.clear();
        ENABLED_PRESETS.clear();
        LAST_APPLIED_IDENTIFIERS.clear();
        APPLIED_DISABLED_RELATIVE_PATHS.clear();
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

    public static Map<String, Set<String>> getDesiredDisabledRelativePathsByPreset() {
        Map<String, Set<String>> enableCache = new LinkedHashMap<>();
        Map<String, Set<String>> conflictCache = new LinkedHashMap<>();
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String presetId : ENABLED_PRESETS) {
            Set<String> paths = new LinkedHashSet<>();
            for (String token : resolveConflictTokensForPreset(presetId, enableCache, conflictCache, new LinkedHashSet<>(), new LinkedHashSet<>())) {
                String relativePath = normalizeRelativeConflictPath(token);
                if (relativePath != null && !relativePath.isBlank()) {
                    paths.add(relativePath);
                }
            }
            if (!paths.isEmpty()) {
                result.put(presetId, Set.copyOf(paths));
            }
        }
        return result;
    }

    public static boolean hasPendingFileConflictChanges() {
        return !normalizeRelativePathMap(APPLIED_DISABLED_RELATIVE_PATHS).equals(
            normalizeRelativePathMap(getDesiredDisabledRelativePathsByPreset()));
    }

    public static Map<String, Set<String>> getAppliedDisabledRelativePathsByPreset() {
        return copyRelativePathMap(APPLIED_DISABLED_RELATIVE_PATHS);
    }

    public static void setAppliedDisabledRelativePathsByPreset(Map<String, Set<String>> applied) {
        APPLIED_DISABLED_RELATIVE_PATHS.clear();
        APPLIED_DISABLED_RELATIVE_PATHS.putAll(normalizeRelativePathMap(applied));
        filterAppliedDisabledRelativePaths();
    }

    private static void rebuildBlocks() {
        Map<String, Set<String>> dlcBlockers = new LinkedHashMap<>();
        Map<String, Set<String>> presetBlockers = new LinkedHashMap<>();
        Map<String, Set<String>> activeEnabledOwners = new LinkedHashMap<>();
        Map<String, Set<String>> enableCache = new LinkedHashMap<>();
        Map<String, Set<String>> conflictCache = new LinkedHashMap<>();
        for (String presetId : ENABLED_PRESETS) {
            DlcPresetEntry preset = PRESETS_BY_ID.get(presetId);
            if (preset == null) {
                continue;
            }
            boolean partial = preset.getStatus() == DlcPresetStatus.PART;
            for (String enable : resolveEnableIdentifiersForPreset(preset.getId(), enableCache, new LinkedHashSet<>())) {
                if (enable == null || enable.isBlank()) {
                    continue;
                }
                if (partial) {
                    DlcItem item = DlcRuntimeState.getItemByIdentifier(enable);
                    if (item == null || !item.isEnabled()) {
                        continue;
                    }
                }
                activeEnabledOwners.computeIfAbsent(enable, key -> new LinkedHashSet<>()).add(presetId);
            }
            for (String conflict : resolveConflictIdentifiersForPreset(preset.getId(), enableCache, conflictCache, new LinkedHashSet<>(), new LinkedHashSet<>())) {
                if (conflict == null || conflict.isBlank()) {
                    continue;
                }
                dlcBlockers.computeIfAbsent(conflict, key -> new LinkedHashSet<>()).add(presetId);
            }
        }
        if (!activeEnabledOwners.isEmpty()) {
            for (Map.Entry<String, Set<String>> entry : activeEnabledOwners.entrySet()) {
                String identifier = entry.getKey();
                DlcItem item = DlcRuntimeState.getItemByIdentifier(identifier);
                if (item == null || item.getConflicts() == null || item.getConflicts().isEmpty()) {
                    continue;
                }
                Set<String> owners = entry.getValue();
                for (String conflict : item.getConflicts()) {
                    if (conflict == null || conflict.isBlank()) {
                        continue;
                    }
                    if (activeEnabledOwners.containsKey(conflict)) {
                        continue;
                    }
                    dlcBlockers.computeIfAbsent(conflict, key -> new LinkedHashSet<>()).addAll(owners);
                }
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
                for (String enable : resolveEnableIdentifiersForPreset(preset.getId(), enableCache, new LinkedHashSet<>())) {
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
        for (String identifier : resolveEnableIdentifiersForPreset(entry.getId(), new LinkedHashMap<>(), new LinkedHashSet<>())) {
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
        Set<String> desiredEnable = new LinkedHashSet<>();
        Set<String> desiredDisableIdentifiers = new LinkedHashSet<>();
        Map<String, Set<String>> enableCache = new LinkedHashMap<>();
        Map<String, Set<String>> conflictCache = new LinkedHashMap<>();
        for (String presetId : ENABLED_PRESETS) {
            DlcPresetEntry preset = PRESETS_BY_ID.get(presetId);
            if (preset == null) {
                continue;
            }
            boolean partial = preset.getStatus() == DlcPresetStatus.PART;
            for (String enable : resolveEnableIdentifiersForPreset(preset.getId(), enableCache, new LinkedHashSet<>())) {
                if (enable == null || enable.isBlank()) {
                    continue;
                }
                if (partial) {
                    DlcItem item = DlcRuntimeState.getItemByIdentifier(enable);
                    if (item != null && item.isEnabled()) {
                        desiredEnable.add(enable);
                    }
                } else {
                    desiredEnable.add(enable);
                }
            }
            for (String conflict : resolveConflictIdentifiersForPreset(preset.getId(), enableCache, conflictCache, new LinkedHashSet<>(), new LinkedHashSet<>())) {
                if (conflict == null || conflict.isBlank()) {
                    continue;
                }
                desiredDisableIdentifiers.add(conflict);
            }
        }
        desiredDisableIdentifiers.addAll(collectInfoConflicts(desiredEnable));
        desiredDisableIdentifiers = expandDisabledByDependents(desiredDisableIdentifiers);
        desiredEnable.removeAll(desiredDisableIdentifiers);

        Set<String> toDisable = new LinkedHashSet<>(LAST_APPLIED_IDENTIFIERS);
        Set<String> desiredAll = new LinkedHashSet<>(desiredEnable);
        desiredAll.addAll(desiredDisableIdentifiers);
        toDisable.removeAll(desiredAll);

        for (String identifier : desiredEnable) {
            DlcItem item = DlcRuntimeState.getItemByIdentifier(identifier);
            if (item != null) {
                DlcRuntimeState.setOverride(item.getId(), true);
            }
        }
        for (String identifier : desiredDisableIdentifiers) {
            DlcItem item = DlcRuntimeState.getItemByIdentifier(identifier);
            if (item != null) {
                DlcRuntimeState.setOverride(item.getId(), false);
            }
        }
        for (String identifier : toDisable) {
            DlcItem item = DlcRuntimeState.getItemByIdentifier(identifier);
            if (item != null) {
                Boolean baseline = DlcRuntimeState.getBaselineEnabled(item.getId());
                if (baseline != null) {
                    DlcRuntimeState.setOverride(item.getId(), baseline);
                } else {
                    DlcRuntimeState.clearOverride(item.getId());
                }
            }
        }

        LAST_APPLIED_IDENTIFIERS.clear();
        LAST_APPLIED_IDENTIFIERS.addAll(desiredAll);
    }

    public static Set<String> resolveEffectiveEnableIdentifiers(String presetId) {
        return resolveEnableIdentifiersForPreset(presetId, new LinkedHashMap<>(), new LinkedHashSet<>());
    }

    public static Set<String> resolveEffectiveConflictIdentifiers(String presetId) {
        Map<String, Set<String>> enableCache = new LinkedHashMap<>();
        return resolveConflictIdentifiersForPreset(presetId, enableCache, new LinkedHashMap<>(), new LinkedHashSet<>(), new LinkedHashSet<>());
    }

    private static Set<String> resolveEnableIdentifiersForPreset(String presetId,
                                                                 Map<String, Set<String>> cache,
                                                                 Set<String> stack) {
        if (presetId == null || presetId.isBlank()) {
            return Set.of();
        }
        Set<String> cached = cache.get(presetId);
        if (cached != null) {
            return cached;
        }
        if (!stack.add(presetId)) {
            return Set.of();
        }
        DlcPresetEntry preset = PRESETS_BY_ID.get(presetId);
        if (preset == null) {
            stack.remove(presetId);
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String token : preset.getEnable()) {
            String value = normalizeToken(token);
            if (value.isBlank()) {
                continue;
            }
            String refPresetId = resolvePresetReference(value);
            if (refPresetId != null) {
                result.addAll(resolveEnableIdentifiersForPreset(refPresetId, cache, stack));
            } else {
                result.add(value);
            }
        }
        stack.remove(presetId);
        Set<String> unmodifiable = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(result));
        cache.put(presetId, unmodifiable);
        return unmodifiable;
    }

    private static Set<String> resolveConflictTokensForPreset(String presetId,
                                                              Map<String, Set<String>> enableCache,
                                                              Map<String, Set<String>> conflictCache,
                                                              Set<String> stack,
                                                              Set<String> enableStack) {
        if (presetId == null || presetId.isBlank()) {
            return Set.of();
        }
        Set<String> cached = conflictCache.get(presetId);
        if (cached != null) {
            return cached;
        }
        if (!stack.add(presetId)) {
            return Set.of();
        }
        DlcPresetEntry preset = PRESETS_BY_ID.get(presetId);
        if (preset == null) {
            stack.remove(presetId);
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String token : preset.getConflicts()) {
            String value = normalizeToken(token);
            if (value.isBlank()) {
                continue;
            }
            String refPresetId = resolvePresetReference(value);
            if (refPresetId != null) {
                result.addAll(resolveEnableIdentifiersForPreset(refPresetId, enableCache, enableStack));
            } else {
                result.add(value);
            }
        }
        stack.remove(presetId);
        Set<String> unmodifiable = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(result));
        conflictCache.put(presetId, unmodifiable);
        return unmodifiable;
    }

    private static Set<String> resolveConflictIdentifiersForPreset(String presetId,
                                                                   Map<String, Set<String>> enableCache,
                                                                   Map<String, Set<String>> conflictCache,
                                                                   Set<String> stack,
                                                                   Set<String> enableStack) {
        Set<String> tokens = resolveConflictTokensForPreset(presetId, enableCache, conflictCache, stack, enableStack);
        if (tokens.isEmpty()) {
            return tokens;
        }
        Set<String> identifiers = new LinkedHashSet<>();
        for (String token : tokens) {
            if (isIdentifierConflictToken(token)) {
                identifiers.add(token);
            }
        }
        return java.util.Collections.unmodifiableSet(identifiers);
    }

    private static String resolvePresetReference(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        boolean forcedPreset = false;
        String value = token.trim();
        if (value.regionMatches(true, 0, PRESET_REF_PREFIX, 0, PRESET_REF_PREFIX.length())) {
            forcedPreset = true;
            value = value.substring(PRESET_REF_PREFIX.length()).trim();
            if (value.isBlank()) {
                return null;
            }
        }
        String resolved = findPresetId(value);
        if (resolved == null) {
            return null;
        }
        if (!forcedPreset && DlcRuntimeState.getItemByIdentifier(value) != null) {
            return null;
        }
        return resolved;
    }

    private static String findPresetId(String value) {
        DlcPresetEntry exact = PRESETS_BY_ID.get(value);
        if (exact != null && exact.getId() != null && !exact.getId().isBlank()) {
            return exact.getId();
        }
        for (DlcPresetEntry entry : PRESETS_BY_ID.values()) {
            if (entry == null || entry.getId() == null || entry.getId().isBlank()) {
                continue;
            }
            if (entry.getId().equalsIgnoreCase(value)) {
                return entry.getId();
            }
            if (entry.getFileName() != null && !entry.getFileName().isBlank() && entry.getFileName().equalsIgnoreCase(value)) {
                return entry.getId();
            }
        }
        return null;
    }

    private static String normalizeToken(String token) {
        if (token == null) {
            return "";
        }
        return token.trim();
    }

    private static boolean isIdentifierConflictToken(String token) {
        String value = normalizeToken(token);
        if (value.isBlank()) {
            return false;
        }
        return DlcRuntimeState.getItemByIdentifier(value) != null;
    }

    private static String normalizeRelativeConflictPath(String token) {
        String value = normalizeToken(token);
        if (value.isBlank()) {
            return null;
        }
        if (DlcRuntimeState.getItemByIdentifier(value) != null) {
            return null;
        }
        Path rawPath = DlcPaths.parsePathLenient(value);
        if (rawPath == null || rawPath.isAbsolute()) {
            return null;
        }
        Path gameDir = DlcPaths.gameDir();
        if (gameDir == null) {
            return null;
        }
        Path resolved = DlcPaths.resolveAgainstGameDir(value);
        if (resolved == null) {
            return null;
        }
        Path normalizedGameDir = gameDir.toAbsolutePath().normalize();
        if (!resolved.startsWith(normalizedGameDir)) {
            return null;
        }
        return normalizedGameDir.relativize(resolved).toString().replace('\\', '/');
    }

    private static Set<String> collectInfoConflicts(Set<String> enabledIdentifiers) {
        Set<String> result = new LinkedHashSet<>();
        if (enabledIdentifiers == null || enabledIdentifiers.isEmpty()) {
            return result;
        }
        for (String identifier : enabledIdentifiers) {
            if (identifier == null || identifier.isBlank()) {
                continue;
            }
            DlcItem item = DlcRuntimeState.getItemByIdentifier(identifier);
            if (item == null) {
                continue;
            }
            List<String> conflicts = item.getConflicts();
            if (conflicts == null || conflicts.isEmpty()) {
                continue;
            }
            for (String conflict : conflicts) {
                if (conflict == null || conflict.isBlank()) {
                    continue;
                }
                if (enabledIdentifiers.contains(conflict)) {
                    continue;
                }
                result.add(conflict);
            }
        }
        return result;
    }

    private static Set<String> expandDisabledByDependents(Set<String> disabledIdentifiers) {
        Set<String> expanded = new LinkedHashSet<>();
        if (disabledIdentifiers == null || disabledIdentifiers.isEmpty()) {
            return expanded;
        }
        java.util.ArrayDeque<DlcItem> queue = new java.util.ArrayDeque<>();
        for (String identifier : disabledIdentifiers) {
            if (identifier == null || identifier.isBlank()) {
                continue;
            }
            expanded.add(identifier);
            DlcItem root = DlcRuntimeState.getItemByIdentifier(identifier);
            if (root != null) {
                queue.add(root);
            }
        }
        while (!queue.isEmpty()) {
            DlcItem current = queue.poll();
            if (current == null || current.getIdentifier() == null || current.getIdentifier().isBlank()) {
                continue;
            }
            for (DlcItem dependent : DlcRuntimeState.getDependents(current.getIdentifier())) {
                if (dependent == null || dependent.getIdentifier() == null || dependent.getIdentifier().isBlank()) {
                    continue;
                }
                if (expanded.add(dependent.getIdentifier())) {
                    queue.add(dependent);
                }
            }
        }
        return expanded;
    }

    private static void rollbackLastAppliedToBaseline() {
        if (LAST_APPLIED_IDENTIFIERS.isEmpty()) {
            return;
        }
        for (String identifier : LAST_APPLIED_IDENTIFIERS) {
            DlcItem item = DlcRuntimeState.getItemByIdentifier(identifier);
            if (item == null) {
                continue;
            }
            Boolean baseline = DlcRuntimeState.getBaselineEnabled(item.getId());
            if (baseline != null) {
                DlcRuntimeState.setOverride(item.getId(), baseline);
            } else {
                DlcRuntimeState.clearOverride(item.getId());
            }
        }
    }

    private static void filterAppliedDisabledRelativePaths() {
        APPLIED_DISABLED_RELATIVE_PATHS.entrySet().removeIf(entry ->
            entry.getKey() == null || !PRESETS_BY_ID.containsKey(entry.getKey()));
    }

    private static Map<String, Set<String>> copyRelativePathMap(Map<String, Set<String>> source) {
        return normalizeRelativePathMap(source);
    }

    private static Map<String, Set<String>> normalizeRelativePathMap(Map<String, Set<String>> source) {
        Map<String, Set<String>> normalized = new LinkedHashMap<>();
        if (source == null || source.isEmpty()) {
            return normalized;
        }
        for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
            String presetId = entry.getKey();
            if (presetId == null || presetId.isBlank()) {
                continue;
            }
            Set<String> values = new LinkedHashSet<>();
            if (entry.getValue() != null) {
                for (String value : entry.getValue()) {
                    if (value == null || value.isBlank()) {
                        continue;
                    }
                    values.add(value.replace('\\', '/'));
                }
            }
            if (!values.isEmpty()) {
                normalized.put(presetId, Set.copyOf(values));
            }
        }
        return normalized;
    }
}
