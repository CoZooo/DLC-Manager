package com.cozooo.dlc_manager.client.preset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            enable = [
              "mods/a.jar",
              " config/a.toml "
            ]
            conflicts = [
              "mods/b.jar",
              ""
            ]
            alarm = true

            [DisplayName]
            zh_cn = "中文"

            [tip]
            en_us = "Tip"
            """);
        Files.writeString(nested, """
            enable = ["mods/nested.jar"]
            """);

        List<DlcPresetEntry> entries = DlcPresetLoader.loadAll(List.of(tempDir));

        assertEquals(2, entries.size());
        assertEquals("group/alpha", entries.get(0).getId());
        assertEquals("alpha", entries.get(0).getFileName());
        assertEquals(List.of("mods/nested.jar"), entries.get(0).getEnable());
        assertEquals("zeta", entries.get(1).getId());
        assertEquals(List.of("mods/a.jar", "config/a.toml"), entries.get(1).getEnable());
        assertEquals(List.of("mods/b.jar"), entries.get(1).getConflicts());
        assertTrue(entries.get(1).isAlarm());
        assertEquals("中文", entries.get(1).resolveDisplayName("zh_cn"));
        assertEquals("Tip", entries.get(1).resolveTip("zh_cn"));
    }

    @Test
    void ensurePresetRootCreatesPresetDirectory() {
        Path root = tempDir.resolve("DLC");

        Path presetRoot = DlcPresetLoader.ensurePresetRoot(root);

        assertEquals(root.resolve("preset"), presetRoot);
        assertTrue(Files.isDirectory(presetRoot));
    }
}
