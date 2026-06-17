package com.cozooo.dlc_manager.client.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcPackMetaTest {
    @TempDir
    Path tempDir;

    @Test
    void saveAndLoadRoundTripsInstalledPathsAsDc() throws Exception {
        Path metaPath = tempDir.resolve("DLC").resolve("tmp").resolve("pack").resolve("Example").resolve("meta.dc");
        DlcPackMeta meta = new DlcPackMeta("Example", "1.0.0", "STANDARD", "2026-06-15T00:00:00Z", List.of("mods/a.jar", "\\config\\b.toml", "../bad"));
        meta.addInstalledPath("mods/a.jar");

        assertTrue(meta.save(metaPath));
        try (var stream = Files.list(metaPath.getParent())) {
            assertEquals(List.of("meta.dc"), stream.map(path -> path.getFileName().toString()).sorted().toList());
        }
        DlcPackMeta loaded = DlcPackMeta.load(metaPath);

        assertEquals("Example", loaded.identifier);
        assertEquals("1.0.0", loaded.version);
        assertEquals("STANDARD", loaded.format);
        assertEquals("2026-06-15T00:00:00Z", loaded.timestamp);
        assertEquals(List.of("mods/a.jar", "config/b.toml"), loaded.installedPaths);
    }

    @Test
    void loadRejectsInvalidIdentifierOrMissingPath() {
        assertNull(DlcPackMeta.load(tempDir.resolve("missing.dc")));
        assertNull(DlcPackMeta.normalizeRelativePath("../bad"));
        assertNull(DlcPackMeta.normalizeRelativePath("mods//a.jar"));
        assertEquals("mods/a.jar", DlcPackMeta.normalizeRelativePath("/mods/a.jar/"));
    }

    @Test
    void saveReturnsFalseForNullPath() {
        DlcPackMeta meta = new DlcPackMeta("Example", "1.0.0", "STANDARD", "", List.of());

        assertFalse(meta.save(null));
    }
}
