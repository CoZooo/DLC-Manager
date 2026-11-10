package com.cozooo.dlc_manager.client;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.test.TestPlatformProvider;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcFirstRunStoreTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        PlatformServices.setProvider(new TestPlatformProvider(tempDir.resolve("game"), tempDir.resolve("config")));
        DlcConfig.loadClient();
    }

    @AfterEach
    void tearDown() {
        PlatformServices.setProvider(null);
    }

    @Test
    void storesFirstRunOutsideInstance() throws Exception {
        Path dlcRoot = tempDir.resolve("instance").resolve("dlc");
        Path store = tempDir.resolve("user").resolve("state").resolve("first-run.dc");

        assertFalse(DlcFirstRunStore.wasShown(dlcRoot, store));
        assertTrue(DlcFirstRunStore.markShown(dlcRoot, store));
        assertTrue(DlcFirstRunStore.wasShown(dlcRoot, store));
        assertFalse(Files.exists(dlcRoot.resolve("ShownOnFirstRun")));
        assertTrue(Files.exists(store));
    }

    @Test
    void keysByConfiguredModpackAndInstalledVersion() {
        Path dlcRoot = tempDir.resolve("game").resolve("dlc");
        DlcConfig.setModpack("example-pack");
        JsonObject version = new JsonObject();
        version.addProperty("version", "2.3.4");
        Path versionPath = tempDir.resolve("config").resolve("dlc_manager").resolve("version.dc");
        assertTrue(DlcDcConfigIO.write(versionPath, version, DlcDcConfigIO.Layout.GENERIC));

        assertEquals("pack:example-pack@2.3.4", DlcFirstRunStore.keyFor(dlcRoot));
    }

    @Test
    void removesLegacyInstanceMarker() throws Exception {
        Path dlcRoot = tempDir.resolve("instance").resolve("dlc");
        Path store = tempDir.resolve("user").resolve("state").resolve("first-run.dc");
        Files.createDirectories(dlcRoot);
        Files.createFile(dlcRoot.resolve("ShownOnFirstRun"));

        assertTrue(DlcFirstRunStore.wasShown(dlcRoot, store));
        assertFalse(Files.exists(dlcRoot.resolve("ShownOnFirstRun")));
        assertTrue(DlcFirstRunStore.wasShown(dlcRoot, store));
    }
}
