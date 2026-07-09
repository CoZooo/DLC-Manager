package com.cozooo.dlc_manager.client.preset;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DlcPresetEntryTest {
    @Test
    void resolveDisplayNameUsesLanguageThenEnglishThenFileName() {
        DlcPresetEntry entry = new DlcPresetEntry(
            "preset",
            "preset.dc",
            Path.of("preset.dc"),
            Map.of("zh_cn", "中文", "en_us", "English"),
            Map.of(),
            List.of("mods/a.jar"),
            List.of("mods/b.jar"),
            false,
            DlcPresetStatus.ON
        );

        assertEquals("中文", entry.resolveDisplayName("zh_cn"));
        assertEquals("English", entry.resolveDisplayName("ja_jp"));
        assertEquals("preset.dc", new DlcPresetEntry("id", "preset.dc", null, Map.of(), Map.of(), null, null, false, null).resolveDisplayName("zh_cn"));
    }

    @Test
    void resolveTipUsesLanguageThenEnglishThenFirstNonBlankValue() {
        LinkedHashMap<String, String> tips = new LinkedHashMap<>();
        tips.put("zh_cn", "中文提示");
        tips.put("fr_fr", "French tip");
        DlcPresetEntry entry = new DlcPresetEntry("preset", "preset.dc", null, Map.of(), tips, null, null, false, null);

        assertEquals("中文提示", entry.resolveTip("zh_cn"));
        assertEquals("中文提示", entry.resolveTip("ja_jp"));

        DlcPresetEntry english = new DlcPresetEntry("preset", "preset.dc", null, Map.of(), Map.of("en_us", "English tip"), null, null, false, null);
        assertEquals("English tip", english.resolveTip("zh_cn"));
        assertNull(new DlcPresetEntry("preset", "preset.dc", null, Map.of(), Map.of(), null, null, false, null).resolveTip("zh_cn"));
    }

    @Test
    void enableAndConflictsAreImmutableCopies() {
        DlcPresetEntry entry = new DlcPresetEntry(
            "preset",
            "preset.dc",
            null,
            null,
            null,
            List.of("mods/a.jar"),
            List.of("mods/b.jar"),
            true,
            null
        );

        assertEquals(List.of("mods/a.jar"), entry.getEnable());
        assertEquals(List.of("mods/b.jar"), entry.getConflicts());
        assertEquals(true, entry.isAlarm());
        assertEquals(DlcPresetStatus.OFF, entry.getStatus());
        assertThrows(UnsupportedOperationException.class, () -> entry.getEnable().add("mods/c.jar"));
    }
}
