package com.cozooo.dlc_manager.client.update;

import com.cozooo.dlc_manager.core.DlcPathConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcUpdateVersionTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void resetModpackBefore() {
        DlcPathConfig.setModpack("");
    }

    @AfterEach
    void resetModpackAfter() {
        DlcPathConfig.setModpack("");
    }

    @Test
    void loadMissingVersionCreatesDefaultVersionFile() throws Exception {
        DlcUpdateVersion version = DlcUpdateVersion.load(tempDir);

        assertEquals("", version.getModpack());
        assertEquals("1.0.0", version.getBase());
        assertEquals("1.0.0", version.getDlc());
        assertTrue(Files.exists(tempDir.resolve("info").resolve("version.dc")));
        String text = Files.readString(tempDir.resolve("info").resolve("version.dc"));
        assertTrue(text.startsWith("base = \"1.0.0\""));
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

        version.save(tempDir);
        DlcUpdateVersion loaded = DlcUpdateVersion.load(tempDir);

        assertEquals("ExamplePack", loaded.getModpack());
        assertFalse(Files.readString(tempDir.resolve("info").resolve("version.dc")).contains("modpack"));
        assertEquals("2.0.0", loaded.getBase());
        assertEquals("1.1.0", loaded.getDlc());
        assertEquals("1.0.0+1.0.0", loaded.getRollbackFrom());
        assertEquals("2.0.0+1.1.0", loaded.getChangelogShown());
    }

    @Test
    void loadAcceptsLegacyCamelCaseKeysAndDefaultsBlankVersions() throws Exception {
        Path path = tempDir.resolve("info").resolve("version.dc");
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
            Base = ""
            Dlc = " "
            rollbackFrom = "1.0.0+1.0.0"
            changelogShown = "1.2.0+1.0.6"
            """);

        DlcUpdateVersion loaded = DlcUpdateVersion.load(tempDir);

        assertEquals("", loaded.getModpack());
        assertEquals("1.0.0", loaded.getBase());
        assertEquals("1.0.0", loaded.getDlc());
        assertEquals("1.0.0+1.0.0", loaded.getRollbackFrom());
        assertEquals("1.2.0+1.0.6", loaded.getChangelogShown());
    }
}
