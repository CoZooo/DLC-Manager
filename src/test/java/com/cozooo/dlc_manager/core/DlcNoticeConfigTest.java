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
    void loadAndSaveDoNotCreateMissingNoticeFileOrInfoDirectory() {
        DlcNoticeConfig config = DlcNoticeConfig.load();

        assertFalse(config.isNoticeEnabled());
        assertFalse(config.hasContent("en_us"));
        assertTrue(config.getTitle().getValues().isEmpty());
        assertTrue(config.getSubhead().text.getValues().isEmpty());
        assertEquals("", config.resolveContents("en_us"));
        config.setNeverShow(true);
        config.save();
        DlcNoticeConfig.load(dlcRoot.resolve("info").resolve("notice.dc")).save();
        assertFalse(Files.exists(dlcRoot.resolve("info")));
    }

    @Test
    void loadAndSaveLeaveMissingOrEmptyNoticeTextEmpty() throws Exception {
        Path notice = dlcRoot.resolve("notice.dc");
        for (String source : new String[] {
            "isNoticeEnabled = true\n",
            "isNoticeEnabled = true\n[Title]\n[Subhead]\nenabled = false\n[Contents]\n"
        }) {
            Files.writeString(notice, source);
            DlcNoticeConfig config = DlcNoticeConfig.load(notice);
            assertTrue(config.isNoticeEnabled());
            assertTrue(config.getTitle().getValues().isEmpty());
            assertTrue(config.getSubhead().text.getValues().isEmpty());
            assertTrue(config.getContents().getValues().isEmpty());
            config.setNeverShow(true);
            config.save();

            DlcNoticeConfig reloaded = DlcNoticeConfig.load(notice);
            assertEquals("", reloaded.getTitle().resolve("en_us"));
            assertEquals("", reloaded.getSubhead().text.resolve("en_us"));
            assertEquals("", reloaded.resolveContents("en_us"));
            assertFalse(reloaded.hasContent("en_us"));
        }
    }

    @Test
    void saveDoesNotRecreateNoticeRemovedAfterLoading() throws Exception {
        Path notice = dlcRoot.resolve("notice.dc");
        Files.writeString(notice, "isNoticeEnabled = true\n");
        DlcNoticeConfig config = DlcNoticeConfig.load(notice);
        Files.delete(notice);

        config.setNeverShow(true);
        config.save();
        assertFalse(Files.exists(notice));
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

        assertTrue(Files.exists(tempDir.resolve("config/dlc_manager/notice.dc")));
        assertFalse(Files.exists(info.resolve("notice.dc")));
        assertTrue(config.isNoticeEnabled());
        assertFalse(config.getSubhead().enabled);
        assertEquals(DlcNoticeConfig.ShowFrequency.PER_GAME_SESSION, config.getShowFrequency());
        assertEquals("中文标题", config.getTitle().resolve("zh_cn"));
        assertEquals("English Title", config.getTitle().resolve("ja_jp"));
        assertEquals("中文正文", config.resolveContents("zh_cn"));
        assertEquals("", config.getContentUrl("en_us"));
    }

    @Test
    void absentOrBlankBodyDoesNotProducePlaceholderContent() throws Exception {
        Path notice = dlcRoot.resolve("notice.dc");
        Files.writeString(notice, "isNoticeEnabled = true\n[Title]\nen_us = \"Title\"\n");
        DlcNoticeConfig empty = DlcNoticeConfig.load(notice);
        assertFalse(empty.hasContent("en_us"));
        assertEquals("", empty.resolveContents("en_us"));

        Files.writeString(notice, "isNoticeEnabled = true\n[Contents]\nen_us = \"   \"\n");
        assertFalse(DlcNoticeConfig.load(notice).hasContent("en_us"));
    }

    @Test
    void localizedBodyOrConfiguredUrlCountsAsContentWithoutFetching() throws Exception {
        Path notice = dlcRoot.resolve("notice.dc");
        Files.writeString(notice, """
            isNoticeEnabled = true
            [Contents]
            en_us = "English Body"
            zh_cn = "   "
            """);
        DlcNoticeConfig local = DlcNoticeConfig.load(notice);
        assertTrue(local.hasContent("ja_jp"));
        assertFalse(local.hasContent("zh_cn"));

        Files.writeString(notice, """
            isNoticeEnabled = true
            ContentUrl = "https://example.invalid/notice.txt"
            """);
        assertTrue(DlcNoticeConfig.load(notice).hasContent("zh_cn"));
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
