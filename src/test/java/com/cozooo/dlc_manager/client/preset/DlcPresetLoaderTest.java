package com.cozooo.dlc_manager.client.preset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcPresetLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadAllReadsPresetDcFilesAndSortsByFileName() throws Exception {
        Path first = tempDir.resolve("preset").resolve("zeta.dc");
        Path nested = tempDir.resolve("preset").resolve("group").resolve("alpha.dc");
        Files.createDirectories(first.getParent());
        Files.createDirectories(nested.getParent());
        Files.writeString(first, """
            [basic]
            identifier = "performance"
            alarm = true

            [dependencies]
            "mods/a.jar"
            " config/a.toml "

            [conflicts]
            "mods/b.jar"

            [DisplayName]
            zh_cn = "中文"

            [tip]
            en_us = "Tip"
            """);
        Files.writeString(nested, """
            [basic]
            identifier = "nested"

            [dependencies]
            "mods/nested.jar"
            """);

        List<DlcPresetEntry> entries = DlcPresetLoader.loadAll(List.of(tempDir));

        assertEquals(2, entries.size());
        assertEquals("nested", entries.get(0).getId());
        assertEquals("alpha", entries.get(0).getFileName());
        assertEquals(List.of("mods/nested.jar"), entries.get(0).getEnable());
        assertEquals("performance", entries.get(1).getId());
        assertEquals(List.of("mods/a.jar", "config/a.toml"), entries.get(1).getEnable());
        assertEquals(List.of("mods/b.jar"), entries.get(1).getConflicts());
        assertTrue(entries.get(1).isAlarm());
        assertEquals("中文", entries.get(1).resolveDisplayName("zh_cn"));
        assertEquals("Tip", entries.get(1).resolveTip("zh_cn"));
    }

    @Test
    void loadAllMigratesLegacyEnableAndConflictsToRelationTables() throws Exception {
        Path preset = tempDir.resolve("preset").resolve("legacy.dc");
        Files.createDirectories(preset.getParent());
        Files.writeString(preset, """
            enable = [
              "mod-a",
              "mod-b"
            ]
            conflicts = [
              "conflict-a"
            ]
            """);

        List<DlcPresetEntry> entries = DlcPresetLoader.loadAll(List.of(tempDir));
        String migrated = Files.readString(preset);

        assertEquals(1, entries.size());
        assertEquals("legacy", entries.get(0).getId());
        assertEquals(List.of("mod-a", "mod-b"), entries.get(0).getEnable());
        assertEquals(List.of("conflict-a"), entries.get(0).getConflicts());
        assertFalse(migrated.contains("enable ="));
        assertFalse(migrated.contains("conflicts ="));
        assertTrue(migrated.contains("[basic]"));
        assertTrue(migrated.contains("identifier = \"legacy\""));
        assertTrue(migrated.contains("alarm = false"));
        assertTrue(migrated.contains("[dependencies]"));
        assertTrue(migrated.contains("\"mod-a\""));
        assertTrue(migrated.contains("[conflicts]"));
        assertTrue(migrated.contains("\"conflict-a\""));
    }

    @Test
    void loadAllKeepsUppercaseDcPresetFileWhenMigrating() throws Exception {
        Path preset = tempDir.resolve("preset").resolve("&IR.DC");
        Files.createDirectories(preset.getParent());
        Files.writeString(preset, """
            enable = [
              "Immersive Railroading",
              "Track API",
              "Universal Mod Core"
            ]
            conflicts = [
              ""
            ]

            [DisplayName]
            en_us = "§7#Γ Preset §rAll [IR]Immersive Railroading-related Mods"
            zh_cn = "§7#Γ 预设 §r[IR]沉浸铁路 全家桶"

            [tip]
            en_us = "Enabling this preset will activate all IR-related mods."
            zh_cn = "启用此预设，即启用所有 沉浸铁路 相关模组。"
            """);

        List<DlcPresetEntry> entries = DlcPresetLoader.loadAll(List.of(tempDir));

        assertEquals(1, entries.size());
        assertEquals("&IR", entries.get(0).getId());
        assertTrue(Files.exists(preset));
        String migrated = Files.readString(preset);
        assertTrue(migrated.contains("[basic]"));
        assertTrue(migrated.contains("identifier = \"&IR\""));
        assertTrue(migrated.contains("[dependencies]"));
        assertTrue(migrated.contains("\"Immersive Railroading\""));
        assertTrue(migrated.contains("[DisplayName]"));
    }

    @Test
    void loadAllMigratesRootIdentifierAndAlarmToBasic() throws Exception {
        Path preset = tempDir.resolve("preset").resolve("file-name.dc");
        Files.createDirectories(preset.getParent());
        Files.writeString(preset, """
            identifier = "configured-id"
            alarm = true
            """);

        List<DlcPresetEntry> entries = DlcPresetLoader.loadAll(List.of(tempDir));
        String migrated = Files.readString(preset);

        assertEquals(1, entries.size());
        assertEquals("configured-id", entries.get(0).getId());
        assertEquals("file-name", entries.get(0).getFileName());
        assertTrue(entries.get(0).isAlarm());
        assertTrue(migrated.contains("[basic]"));
        assertTrue(migrated.contains("identifier = \"configured-id\""));
        assertTrue(migrated.contains("alarm = true"));
    }

    @Test
    void migrationRetainsUnknownDataAndCommentsAndIsIdempotent() throws Exception {
        Path preset = tempDir.resolve("preset").resolve("custom.dc");
        Files.createDirectories(preset.getParent());
        Files.writeString(preset, """
            # custom preset
            identifier = "custom" # selected identifier
            enable = [
              "main", # required choice
              "optional"
            ]
            future = {"version": 123456789012345678901234567890}

            [extension.provider]
            value = 'unchanged' # preserved format
            """);

        assertEquals(1, DlcPresetLoader.loadAll(List.of(tempDir)).size());
        String migrated = Files.readString(preset);
        assertTrue(migrated.contains("[basic]"));
        assertTrue(migrated.contains("[dependencies]"));
        assertTrue(migrated.contains("# selected identifier"));
        assertTrue(migrated.contains("# required choice"));
        assertTrue(migrated.contains("future = {\"version\": 123456789012345678901234567890}"));
        assertTrue(migrated.contains("[extension.provider]\nvalue = 'unchanged' # preserved format\n"));
        var parsed = com.cozooo.dclanguage.DcDocuments.read(preset).toJson();
        assertFalse(parsed.has("identifier"));
        assertFalse(parsed.has("enable"));
        assertEquals("custom", parsed.getAsJsonObject("basic").get("identifier").getAsString());
        DlcPresetLoader.loadAll(List.of(tempDir));
        assertEquals(migrated, Files.readString(preset));
    }

    @Test
    void ensurePresetRootCreatesPresetDirectory() {
        Path root = tempDir.resolve("DLC");

        Path presetRoot = DlcPresetLoader.ensurePresetRoot(root);

        assertEquals(root.resolve("preset"), presetRoot);
        assertTrue(Files.isDirectory(presetRoot));
    }
}
