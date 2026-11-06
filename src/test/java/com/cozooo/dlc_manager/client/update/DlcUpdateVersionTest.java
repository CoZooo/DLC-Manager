package com.cozooo.dlc_manager.client.update;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.test.TestPlatformProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcUpdateVersionTest {
    @TempDir
    Path tempDir;

    private Path gameDir;
    private Path dlcRoot;

    @BeforeEach
    void resetModpackBefore() {
        gameDir = tempDir.resolve("game");
        dlcRoot = gameDir.resolve("DLC");
        try {
            Files.createDirectories(dlcRoot);
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
        PlatformServices.setProvider(new TestPlatformProvider(gameDir, gameDir.resolve("config")));
        DlcConfig.setDlcRoot("DLC");
        DlcConfig.setModpack("");
    }

    @AfterEach
    void resetModpackAfter() {
        DlcConfig.setModpack("");
        PlatformServices.setProvider(null);
    }

    @Test
    void loadMissingVersionCreatesDefaultVersionFile() throws Exception {
        DlcUpdateVersion version = DlcUpdateVersion.load(dlcRoot);

        assertEquals("", version.getModpack());
        assertEquals("1.0.0", version.getBase());
        assertNull(version.getDlc());
        Path path = gameDir.resolve("config/dlc_manager/version.dc");
        assertTrue(Files.exists(path));
        String text = Files.readString(path);
        assertTrue(text.startsWith("version = \"1.0.0\""));
        assertFalse(text.contains("base ="));
        assertFalse(text.contains("dlc"));
        assertFalse(text.contains("modpack"));
    }

    @Test
    void saveAndLoadRoundTripsVersionFields() throws Exception {
        DlcUpdateVersion version = new DlcUpdateVersion();
        version.setModpack("ExamplePack");
        version.setBase("2.0.0");
        version.setDlc("1.1.0");
        version.setRollbackFrom("1.0.0+1.0.0");
        version.setChangelogShown("2.0.0+1.1.0");

        version.save(dlcRoot);
        DlcUpdateVersion loaded = DlcUpdateVersion.load(dlcRoot);

        assertEquals("ExamplePack", loaded.getModpack());
        assertFalse(Files.readString(gameDir.resolve("config/dlc_manager/version.dc")).contains("modpack"));
        assertEquals("2.0.0", loaded.getBase());
        assertEquals("1.1.0", loaded.getDlc());
        assertEquals("1.0.0+1.0.0", loaded.getRollbackFrom());
        assertEquals("2.0.0+1.1.0", loaded.getChangelogShown());
    }

    @Test
    void loadAcceptsLegacyCamelCaseKeysAndDefaultsBlankVersions() throws Exception {
        Path path = dlcRoot.resolve("info").resolve("version.dc");
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
            Base = ""
            Dlc = " "
            rollbackFrom = "1.0.0+1.0.0"
            changelogShown = "1.2.0+1.0.6"
            """);

        DlcUpdateVersion loaded = DlcUpdateVersion.load(dlcRoot);

        assertEquals("", loaded.getModpack());
        assertEquals("1.0.0", loaded.getBase());
        assertNull(loaded.getDlc());
        assertEquals("1.0.0+1.0.0", loaded.getRollbackFrom());
        assertEquals("1.2.0+1.0.6", loaded.getChangelogShown());
    }

    @Test
    void loadPrefersCanonicalVersionAndFallsBackToLegacyBase() throws Exception {
        Path path = dlcRoot.resolve("info").resolve("version.dc");
        Files.createDirectories(path.getParent());
        Files.writeString(path, "base = \"2.0.0\"\nunknown = \"keep\"\n");

        DlcUpdateVersion legacy = DlcUpdateVersion.load(dlcRoot);
        assertEquals("2.0.0", legacy.getVersion());
        Path migratedPath = gameDir.resolve("config/dlc_manager/version.dc");
        assertFalse(Files.exists(path));
        String migrated = Files.readString(migratedPath);
        assertTrue(migrated.contains("version = \"2.0.0\""));
        assertTrue(migrated.contains("unknown = \"keep\""));

        Files.writeString(migratedPath, "version = \"3.0.0\"\nbase = \"2.0.0\"\n");
        DlcUpdateVersion canonical = DlcUpdateVersion.load(dlcRoot);
        assertEquals("3.0.0", canonical.getVersion());
    }
}
