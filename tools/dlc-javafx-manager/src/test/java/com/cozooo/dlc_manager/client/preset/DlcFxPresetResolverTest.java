package com.cozooo.dlc_manager.client.preset;

import com.cozooo.dlc_manager.ui.preset.DlcFxPresetResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DlcFxPresetResolverTest {
    @Test
    void expandsPresetReferencesButKeepsDlcIdentifierWhenItemExists() {
        DlcPresetEntry base = entry("Base", List.of("rail-core"), List.of());
        DlcPresetEntry target = entry("Target", List.of("Base", "preset:Base"), List.of());
        Map<String, DlcPresetEntry> byId = DlcFxPresetResolver.byId(List.of(base, target));

        Set<String> identifiers = DlcFxPresetResolver.resolveEnableIdentifiers(
                "Target",
                byId,
                id -> "Base".equals(id)
        );

        assertEquals(Set.of("Base", "rail-core"), identifiers);
    }

    @Test
    void expandsPresetConflictsToReferencedPresetEnableIdentifiers() {
        DlcPresetEntry base = entry("Base", List.of("rail-core", "track-api"), List.of());
        DlcPresetEntry target = entry("Target", List.of("cars"), List.of("preset:Base"));
        Map<String, DlcPresetEntry> byId = DlcFxPresetResolver.byId(List.of(base, target));

        Set<String> identifiers = DlcFxPresetResolver.resolveConflictIdentifiers(
                "Target",
                byId,
                id -> false
        );

        assertEquals(Set.of("rail-core", "track-api"), identifiers);
    }

    private static DlcPresetEntry entry(String id, List<String> enable, List<String> conflicts) {
        return new DlcPresetEntry(id, id + ".dc", null, Map.of(), Map.of(), enable, conflicts, false, DlcPresetStatus.OFF);
    }
}
