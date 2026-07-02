package com.cozooo.dlc_manager.ui.preset;

import com.cozooo.dlc_manager.client.preset.DlcPresetEntry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class DlcFxPresetResolver {
    private static final String PRESET_REF_PREFIX = "preset:";

    private DlcFxPresetResolver() {
    }

    public static Map<String, DlcPresetEntry> byId(List<DlcPresetEntry> entries) {
        Map<String, DlcPresetEntry> byId = new LinkedHashMap<>();
        for (DlcPresetEntry entry : entries == null ? List.<DlcPresetEntry>of() : entries) {
            if (entry != null && entry.getId() != null && !entry.getId().isBlank()) {
                byId.put(entry.getId(), entry);
            }
        }
        return byId;
    }

    public static Set<String> resolveEnableIdentifiers(String presetId,
                                                       Map<String, DlcPresetEntry> presetsById,
                                                       Predicate<String> itemExists) {
        return resolveEnableIdentifiers(presetId, presetsById, itemExists, new LinkedHashMap<>(), new LinkedHashSet<>());
    }

    public static Set<String> resolveConflictIdentifiers(String presetId,
                                                         Map<String, DlcPresetEntry> presetsById,
                                                         Predicate<String> itemExists) {
        return resolveConflictIdentifiers(
                presetId,
                presetsById,
                itemExists,
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                new LinkedHashSet<>(),
                new LinkedHashSet<>()
        );
    }

    private static Set<String> resolveEnableIdentifiers(String presetId,
                                                        Map<String, DlcPresetEntry> presetsById,
                                                        Predicate<String> itemExists,
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
        DlcPresetEntry preset = presetsById == null ? null : presetsById.get(presetId);
        if (preset == null) {
            stack.remove(presetId);
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String token : preset.getEnable()) {
            String value = clean(token);
            if (value.isBlank()) {
                continue;
            }
            String refPresetId = resolvePresetReference(value, presetsById, itemExists);
            if (refPresetId != null) {
                result.addAll(resolveEnableIdentifiers(refPresetId, presetsById, itemExists, cache, stack));
            } else {
                result.add(value);
            }
        }
        stack.remove(presetId);
        Set<String> unmodifiable = Collections.unmodifiableSet(new LinkedHashSet<>(result));
        cache.put(presetId, unmodifiable);
        return unmodifiable;
    }

    private static Set<String> resolveConflictIdentifiers(String presetId,
                                                          Map<String, DlcPresetEntry> presetsById,
                                                          Predicate<String> itemExists,
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
        DlcPresetEntry preset = presetsById == null ? null : presetsById.get(presetId);
        if (preset == null) {
            stack.remove(presetId);
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String token : preset.getConflicts()) {
            String value = clean(token);
            if (value.isBlank()) {
                continue;
            }
            String refPresetId = resolvePresetReference(value, presetsById, itemExists);
            if (refPresetId != null) {
                result.addAll(resolveEnableIdentifiers(refPresetId, presetsById, itemExists, enableCache, enableStack));
            } else {
                result.add(value);
            }
        }
        stack.remove(presetId);
        Set<String> unmodifiable = Collections.unmodifiableSet(new LinkedHashSet<>(result));
        conflictCache.put(presetId, unmodifiable);
        return unmodifiable;
    }

    private static String resolvePresetReference(String token,
                                                 Map<String, DlcPresetEntry> presetsById,
                                                 Predicate<String> itemExists) {
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
        String presetId = findPresetId(value, presetsById);
        if (presetId == null) {
            return null;
        }
        if (!forcedPreset && itemExists != null && itemExists.test(value)) {
            return null;
        }
        return presetId;
    }

    private static String findPresetId(String value, Map<String, DlcPresetEntry> presetsById) {
        if (value == null || value.isBlank() || presetsById == null || presetsById.isEmpty()) {
            return null;
        }
        DlcPresetEntry exact = presetsById.get(value);
        if (exact != null && exact.getId() != null && !exact.getId().isBlank()) {
            return exact.getId();
        }
        for (DlcPresetEntry entry : presetsById.values()) {
            if (entry == null || entry.getId() == null || entry.getId().isBlank()) {
                continue;
            }
            if (entry.getId().equalsIgnoreCase(value)) {
                return entry.getId();
            }
            if (entry.getFileName() != null && !entry.getFileName().isBlank()
                    && entry.getFileName().equalsIgnoreCase(value)) {
                return entry.getId();
            }
        }
        return null;
    }

    public static String normalizeIdentifier(String identifier) {
        return identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
