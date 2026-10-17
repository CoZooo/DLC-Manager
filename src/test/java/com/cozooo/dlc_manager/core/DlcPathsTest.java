package com.cozooo.dlc_manager.core;

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

class DlcPathsTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void setPlatform() {
        PlatformServices.setProvider(new TestPlatformProvider(tempDir.resolve("game"), tempDir.resolve("config")));
    }

    @AfterEach
    void clearPlatform() {
        PlatformServices.setProvider(null);
    }

    @Test
    void resolvesRelativeTargetsAgainstGameDir() {
        assertEquals(tempDir.resolve("game").resolve("mods/a.jar").normalize(), DlcPaths.resolveAgainstGameDir("mods/a.jar"));
        assertEquals(tempDir.resolve("game").resolve("config/a.toml").normalize(), DlcPaths.resolveAppliedTarget("config/a.toml"));
        assertNull(DlcPaths.resolveAppliedTarget(" "));
    }

    @Test
    void parsePathLenientHandlesNullBlankAndNormalPaths() {
        Path parsed = DlcPaths.parsePathLenient("config/a.toml");

        assertEquals(Path.of("config/a.toml"), parsed);
        assertEquals(Path.of("config/a.toml"), DlcPaths.parsePathLenient("config\\a.toml"));
        assertNull(DlcPaths.parsePathLenient(null));
        assertNull(DlcPaths.parsePathLenient(" "));
    }

    @Test
    void appliedTargetDirectoryUsesRawSuffixSourceDirectoryAndResolvedState() throws Exception {
        Path sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);
        Path existingFile = tempDir.resolve("file.txt");
        Files.writeString(existingFile, "x");

        assertTrue(DlcPaths.isAppliedTargetDirectory("config/", null, null));
        assertTrue(DlcPaths.isAppliedTargetDirectory("config", null, sourceDir));
        assertTrue(DlcPaths.isAppliedTargetDirectory("missing", tempDir.resolve("missing"), null));
        assertFalse(DlcPaths.isAppliedTargetDirectory("file.txt", existingFile, null));
    }
}
