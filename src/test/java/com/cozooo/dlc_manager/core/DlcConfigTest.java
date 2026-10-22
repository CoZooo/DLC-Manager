package com.cozooo.dlc_manager.core;

import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.test.TestPlatformProvider;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcConfigTest {
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
    void taczTargetsResolveAgainstGameDirIndependentlyOfDlcSources() throws Exception {
        Path gameDir = tempDir.resolve("game");
        DlcConfig.setDlcRoot("optional,other-dlc");
        Files.createDirectories(gameDir.resolve("other-dlc/tacz"));
        assertEquals(gameDir.resolve("tacz"), DlcConfig.getTaczRoot());
        assertEquals(DlcConfig.getTaczRoot(), DlcPaths.taczDir());

        Path external = tempDir.resolve("external-gunpacks");
        DlcConfig.setTaczRoot("custom\\guns," + external);
        assertEquals(java.util.List.of(gameDir.resolve("custom/guns"), external), DlcConfig.getTaczRoots());
        assertEquals(gameDir.resolve("custom/guns"), DlcPaths.taczDir());

        DlcConfig.saveShared();
        DlcConfig.loadClient();
        assertEquals("custom\\guns," + external, DlcConfig.getTaczRootRaw());
        assertEquals(java.util.List.of(gameDir.resolve("custom/guns"), external), DlcConfig.getTaczRoots());

        DlcConfig.setTaczRoot(" , ");
        assertEquals(java.util.List.of(gameDir.resolve("tacz")), DlcConfig.getTaczRoots());
    }

    @Test
    void anchoredPositionsUseConfiguredAnchorAndOffsets() {
        DlcConfig.setTitleAnchor("bottom_right");
        DlcConfig.setTitleOffsetX(-5);
        DlcConfig.setTitleOffsetY(-7);

        assertArrayEquals(new int[] { 175, 83 }, DlcConfig.computeTitleButtonPosition(200, 100, 20, 10, 50, 40));

        DlcConfig.setTitleAnchor("auto");
        assertArrayEquals(new int[] { 45, 33 }, DlcConfig.computeTitleButtonPosition(200, 100, 20, 10, 50, 40));

        DlcConfig.setTitleAnchor("hidden");
        assertTrue(DlcConfig.isTitleAnchorHidden());
    }

    @Test
    void dependencyReminderDefaultsOffAndPersistsInUiSection() throws IOException {
        assertFalse(DlcConfig.isShowDependencyReminder());
        assertFalse(DlcConfig.buildSharedConfigJson().getAsJsonObject("ui")
            .get("showDependencyReminder").getAsBoolean());

        DlcConfig.setShowDependencyReminder(true);
        DlcConfig.saveClient();
        DlcConfig.setShowDependencyReminder(false);
        DlcConfig.loadClient();
        assertTrue(DlcConfig.isShowDependencyReminder());

        Path config = tempDir.resolve("config/dlc_manager/config.dc");
        Files.writeString(config, "[ui]\nShowOnFirstRun = true\n");
        DlcConfig.loadClient();
        assertFalse(DlcConfig.isShowDependencyReminder());
        assertTrue(DlcConfig.isShowOnFirstRun());
    }

    @Test
    void noticeDisplayModeAliasesAndPanelBoundsAreNormalized() {
        DlcConfig.setNoticeDisplayMode("title-panel");
        DlcConfig.setNoticeButtonOffsetX(9999);
        DlcConfig.setNoticeButtonOffsetY(-9999);
        DlcConfig.setNoticePanelWidth(9999);
        DlcConfig.setNoticePanelHeight(-1);

        assertTrue(DlcConfig.isNoticeDisplayModeMainMenuPanel());
        assertFalse(DlcConfig.isNoticeDisplayModeManagerButton());
        assertEquals(1000, DlcConfig.getNoticeButtonOffsetX());
        assertEquals(-1000, DlcConfig.getNoticeButtonOffsetY());
        assertEquals(1000, DlcConfig.getNoticePanelWidth());
        assertEquals(0, DlcConfig.getNoticePanelHeight());
    }

    @Test
    void downloadSettingsClampAndConvertUnits() {
        DlcConfig.setMaxConcurrentDownloads(0);
        DlcConfig.setMaxSegmentsPerFile(0);
        DlcConfig.setMaxRetryCount(0);
        DlcConfig.setMinSegmentBytes(1024L * 1024L + 1L);
        DlcConfig.setConnectTimeoutMs(1500);
        DlcConfig.setTaskTimeoutMs(1);

        assertEquals(1, DlcConfig.getMaxConcurrentDownloads());
        assertEquals(1, DlcConfig.getMaxSegmentsPerFile());
        assertEquals(1, DlcConfig.getMaxRetryCount());
        assertEquals(2L * 1024L * 1024L, DlcConfig.getMinSegmentBytes());
        assertEquals(2000, DlcConfig.getConnectTimeoutMs());
        assertEquals(1000, DlcConfig.getTaskTimeoutMs());
    }

    @Test
    void downloadSourceModeKeepsMirrorsAndProbeOptionsAreNotPersisted() {
        DlcConfig.setDownloadMirrorMode(null);
        assertEquals(DlcConfig.DOWNLOAD_MIRROR_OFFICIAL_ONLY, DlcConfig.getDownloadMirrorMode());
        DlcConfig.setDownloadMirrorMode("smart_precheck");
        assertEquals(DlcConfig.DOWNLOAD_MIRROR_OFFICIAL_ONLY, DlcConfig.getDownloadMirrorMode());

        DlcConfig.setDownloadMirrorMode("mirror_only");

        assertEquals(DlcConfig.DOWNLOAD_MIRROR_MIRROR_ONLY, DlcConfig.getDownloadMirrorMode());
        JsonObject downloads = DlcConfig.buildSharedConfigJson().getAsJsonObject("downloads");
        assertEquals(DlcConfig.DOWNLOAD_MIRROR_MIRROR_ONLY, downloads.get("downloadMirrorMode").getAsString());
        assertFalse(downloads.has("downloadMirrorProbeCount"));
        assertFalse(downloads.has("downloadMirrorProbeStrategy"));
    }

    @Test
    void serverConfigJsonRoundTripsServerAndDownloadSettings() {
        DlcConfig.setServerSupportOnServer(false);
        DlcConfig.setApiEnabled(true);
        DlcConfig.setApiPort(10087);
        DlcConfig.setServerRconHost("127.0.0.1");
        DlcConfig.setServerRconPort(25576);
        DlcConfig.setMaxRetryCount(3);

        var json = DlcConfig.buildServerConfigJson();
        assertFalse(json.getAsJsonObject("server").has("serverSupport"));
        assertFalse(json.getAsJsonObject("ui").has("serverSupport"));
        DlcConfig.loadClient();
        DlcConfig.applySharedConfigJson(json);
        DlcConfig.applyServerConfigJson(json);

        assertTrue(DlcConfig.isServerSupportOnServer());
        assertTrue(DlcConfig.isApiEnabled());
        assertEquals(10087, DlcConfig.getApiPort());
        assertEquals("127.0.0.1", DlcConfig.getServerRconHost());
        assertEquals(25576, DlcConfig.getServerRconPort());
        assertEquals(3, DlcConfig.getMaxRetryCount());
    }

    @Test
    void serverLoadsModpackAutoUpdateFlagsFromSharedConfig() {
        DlcConfig.setModpackAutoUpdateOn(true);
        DlcConfig.setModpackForceUpdateOn(true);

        var json = DlcConfig.buildServerConfigJson();
        var modpack = json.getAsJsonObject("modpack");
        assertTrue(modpack.get("autoUpdate").getAsBoolean());
        assertTrue(modpack.get("forceUpdate").getAsBoolean());

        DlcConfig.saveShared();
        DlcConfig.setModpackAutoUpdateOn(false);
        DlcConfig.setModpackForceUpdateOn(false);
        DlcConfig.loadServer();
        assertTrue(DlcConfig.isModpackAutoUpdateOn());
        assertTrue(DlcConfig.isModpackForceUpdateOn());
    }
}
