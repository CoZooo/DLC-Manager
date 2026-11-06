package com.cozooo.dlc_manager.client.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcUpdateMetaTest {
    @TempDir
    Path tempDir;

    @Test
    void saveAndLoadRoundTripsUpdateMetadata() {
        Path path = tempDir.resolve("meta.dc");
        DlcUpdateMeta meta = new DlcUpdateMeta();
        meta.setDlcOld("1.0.0");
        meta.setDlcNew("1.0.1");
        meta.setBaseOld("2.0.0");
        meta.setBaseNew("2.0.1");
        meta.setTimestamp("2026-06-12T00:00:00Z");
        meta.setHidden(true);
        meta.setChangelog(Map.of("ZH_CN", "中文日志", "en_us", "English log"));
        meta.getRestorePaths().add("mods/old.jar");
        meta.getCreatedPaths().add("mods/new.jar");

        meta.save(path);
        DlcUpdateMeta loaded = DlcUpdateMeta.load(path);

        assertEquals("1.0.0", loaded.getDlcOld());
        assertEquals("1.0.1", loaded.getDlcNew());
        assertEquals("2.0.0", loaded.getBaseOld());
        assertEquals("2.0.1", loaded.getBaseNew());
        assertEquals("2026-06-12T00:00:00Z", loaded.getTimestamp());
        assertTrue(loaded.isHidden());
        assertEquals("中文日志", loaded.resolveChangelog("zh_cn"));
        assertEquals("English log", loaded.resolveChangelog("en_us"));
        assertEquals("mods/old.jar", loaded.getRestorePaths().get(0));
        assertEquals("mods/new.jar", loaded.getCreatedPaths().get(0));
    }

    @Test
    void changelogFallsBackToEnglishThenFirstEntry() {
        DlcUpdateMeta meta = new DlcUpdateMeta();
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("EN_US", "English");
        values.put("zh_cn", "Chinese");
        meta.setChangelog(values);

        assertEquals("Chinese", meta.resolveChangelog("zh_cn"));
        assertEquals("English", meta.resolveChangelog("ja_jp"));

        meta.setChangelog(Map.of("zh_cn", "Chinese"));
        assertEquals("Chinese", meta.resolveChangelog("ja_jp"));
    }

    @Test
    void loadReturnsNullForMissingPathAndSetChangelogSkipsBlankValues() {
        assertNull(DlcUpdateMeta.load(tempDir.resolve("missing.dc")));

        DlcUpdateMeta meta = new DlcUpdateMeta();
        meta.setChangelog(Map.of("zh_cn", " "));

        assertTrue(meta.getChangelog().isEmpty());
        assertFalse(meta.isHidden());
        assertEquals("", meta.resolveChangelog("zh_cn"));
    }

    @Test
    void savesCanonicalVersionMetadataAndReadsLegacyBaseMetadata() throws Exception {
        DlcUpdateMeta meta = new DlcUpdateMeta();
        meta.setVersionOld("1.0.0");
        meta.setVersionNew("1.1.0");
        Path path = tempDir.resolve("canonical-meta.dc");
        meta.save(path);

        String canonical = Files.readString(path);
        assertTrue(canonical.contains("version_old = \"1.0.0\""));
        assertTrue(canonical.contains("version_new = \"1.1.0\""));

        Files.writeString(path, "base_old = \"2.0.0\"\nbase_new = \"2.1.0\"\n");
        DlcUpdateMeta legacy = DlcUpdateMeta.load(path);
        assertEquals("2.0.0", legacy.getVersionOld());
        assertEquals("2.1.0", legacy.getVersionNew());
    }
}
