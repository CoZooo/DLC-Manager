package com.cozooo.dlc_manager.core;

import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.test.TestPlatformProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcPathConfigTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        PlatformServices.setProvider(new TestPlatformProvider(tempDir.resolve("game"), tempDir.resolve("config")));
        DlcPathConfig.loadClient();
    }

    @AfterEach
    void tearDown() {
        PlatformServices.setProvider(null);
    }

    @Test
    void anchoredPositionsUseConfiguredAnchorAndOffsets() {
        DlcPathConfig.setTitleAnchor("bottom_right");
        DlcPathConfig.setTitleOffsetX(-5);
        DlcPathConfig.setTitleOffsetY(-7);

        assertArrayEquals(new int[] { 175, 83 }, DlcPathConfig.computeTitleButtonPosition(200, 100, 20, 10, 50, 40));

        DlcPathConfig.setTitleAnchor("auto");
        assertArrayEquals(new int[] { 45, 33 }, DlcPathConfig.computeTitleButtonPosition(200, 100, 20, 10, 50, 40));
    }

    @Test
    void noticeDisplayModeAliasesAndPanelBoundsAreNormalized() {
        DlcPathConfig.setNoticeDisplayMode("title-panel");
        DlcPathConfig.setNoticeButtonOffsetX(9999);
        DlcPathConfig.setNoticeButtonOffsetY(-9999);
        DlcPathConfig.setNoticePanelWidth(9999);
        DlcPathConfig.setNoticePanelHeight(-1);

        assertTrue(DlcPathConfig.isNoticeDisplayModeMainMenuPanel());
        assertFalse(DlcPathConfig.isNoticeDisplayModeManagerButton());
        assertEquals(1000, DlcPathConfig.getNoticeButtonOffsetX());
        assertEquals(-1000, DlcPathConfig.getNoticeButtonOffsetY());
        assertEquals(1000, DlcPathConfig.getNoticePanelWidth());
        assertEquals(0, DlcPathConfig.getNoticePanelHeight());
    }

    @Test
    void downloadSettingsClampAndConvertUnits() {
        DlcPathConfig.setMaxConcurrentDownloads(0);
        DlcPathConfig.setMaxSegmentsPerFile(0);
        DlcPathConfig.setMaxRetryCount(0);
        DlcPathConfig.setMinSegmentBytes(1024L * 1024L + 1L);
        DlcPathConfig.setConnectTimeoutMs(1500);
        DlcPathConfig.setTaskTimeoutMs(1);

        assertEquals(1, DlcPathConfig.getMaxConcurrentDownloads());
        assertEquals(1, DlcPathConfig.getMaxSegmentsPerFile());
        assertEquals(1, DlcPathConfig.getMaxRetryCount());
        assertEquals(2L * 1024L * 1024L, DlcPathConfig.getMinSegmentBytes());
        assertEquals(2000, DlcPathConfig.getConnectTimeoutMs());
        assertEquals(1000, DlcPathConfig.getTaskTimeoutMs());
    }

    @Test
    void serverConfigJsonRoundTripsServerAndDownloadSettings() {
        DlcPathConfig.setServerSupportOnServer(true);
        DlcPathConfig.setServerPort(29691);
        DlcPathConfig.setApiEnabled(true);
        DlcPathConfig.setApiPort(10087);
        DlcPathConfig.setServerRconHost("127.0.0.1");
        DlcPathConfig.setServerRconPort(25576);
        DlcPathConfig.setMaxRetryCount(3);

        var json = DlcPathConfig.buildServerConfigJson();
        DlcPathConfig.loadClient();
        DlcPathConfig.applyServerConfigJson(json);

        assertTrue(DlcPathConfig.isServerSupportOnServer());
        assertEquals(29691, DlcPathConfig.getServerPort());
        assertTrue(DlcPathConfig.isApiEnabled());
        assertEquals(10087, DlcPathConfig.getApiPort());
        assertEquals("127.0.0.1", DlcPathConfig.getServerRconHost());
        assertEquals(25576, DlcPathConfig.getServerRconPort());
        assertEquals(3, DlcPathConfig.getMaxRetryCount());
    }
}
