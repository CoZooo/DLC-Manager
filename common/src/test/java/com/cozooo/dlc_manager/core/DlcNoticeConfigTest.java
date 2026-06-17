package com.cozooo.dlc_manager.core;

import com.cozooo.dlc_manager.client.notice.DlcNoticeConfig;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcNoticeConfigTest {
    @TempDir
    Path tempDir;

    private Path gameDir;
    private Path dlcRoot;

    @BeforeEach
    void setUp() throws Exception {
        gameDir = tempDir.resolve("game");
        dlcRoot = gameDir.resolve("DLC");
        Files.createDirectories(dlcRoot);
        PlatformServices.setProvider(new TestPlatformProvider(gameDir, tempDir.resolve("config")));
        DlcConfig.setDlcRoot("DLC");
    }

    @AfterEach
    void tearDown() {
        PlatformServices.setProvider(null);
    }

    @Test
    void loadCreatesDisabledDefaultNoticeFileWhenDlcRootExists() {
        DlcNoticeConfig config = DlcNoticeConfig.load();

        assertFalse(config.isNoticeEnabled());
        assertEquals(DlcNoticeConfig.ShowFrequency.PER_LAUNCH_MANAGER, config.getShowFrequency());
        assertEquals("Title", config.getTitle().resolve("en_us"));
        assertEquals("Contents", config.resolveContents("en_us"));
        assertTrue(Files.exists(dlcRoot.resolve("info").resolve("notice.dc")));
    }

    @Test
    void loadReadsLocalizedNoticeAndFallsBackToEnglish() throws Exception {
        Path info = dlcRoot.resolve("info");
        Files.createDirectories(info);
        Files.writeString(info.resolve("notice.dc"), """
            isNoticeEnabled = true
            neverShow = false
            ShowFrequency = "pergamesession"
            ContentUrl = "   "

            [Title]
            en_us = "English Title"
            zh_cn = "中文标题"

            [Subhead]
            enabled = false
            en_us = "English Subhead"

            [Contents]
            en_us = "English Body"
            zh_cn = "中文正文"
            """);

        DlcNoticeConfig config = DlcNoticeConfig.load();

        assertTrue(config.isNoticeEnabled());
        assertFalse(config.getSubhead().enabled);
        assertEquals(DlcNoticeConfig.ShowFrequency.PER_GAME_SESSION, config.getShowFrequency());
        assertEquals("中文标题", config.getTitle().resolve("zh_cn"));
        assertEquals("English Title", config.getTitle().resolve("ja_jp"));
        assertEquals("中文正文", config.resolveContents("zh_cn"));
        assertEquals("", config.getContentUrl("en_us"));
    }

    @Test
    void savePersistsNeverShowAndFrequency() throws Exception {
        Path info = dlcRoot.resolve("info");
        Files.createDirectories(info);
        Files.writeString(info.resolve("notice.dc"), """
            isNoticeEnabled = true
            neverShow = false
            ShowFrequency = "once"

            [Title]
            en_us = "Title"

            [Subhead]
            enabled = true
            en_us = "Subhead"

            [Contents]
            en_us = "Contents"
            """);
        DlcNoticeConfig config = DlcNoticeConfig.load();

        config.setNeverShow(true);
        config.save();

        DlcNoticeConfig reloaded = DlcNoticeConfig.load();
        assertTrue(reloaded.isNeverShow());
        assertEquals(DlcNoticeConfig.ShowFrequency.ONCE, reloaded.getShowFrequency());
    }
}
