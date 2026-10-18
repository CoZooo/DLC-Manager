package com.cozooo.dlc_bootstrap.launch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DlcEarlyFailureTest {
    @TempDir Path game;
    private final Locale originalLocale = Locale.getDefault();

    @AfterEach void reset() {
        System.clearProperty(DlcEarlyProgress.READY_PROPERTY);
        Thread.interrupted();
        Locale.setDefault(originalLocale);
    }

    @Test void usesOnlySystemLanguageAndFallsBackToEnglishForOtherLanguages() throws Exception {
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
        Files.writeString(game.resolve("options.txt"), "lang:en_us\n");
        assertEquals("关闭", new DlcEarlyI18n().text("failure.close"));
        assertTrue(new DlcEarlyI18n(Locale.SIMPLIFIED_CHINESE).text("downloading", "中文资源.jar").contains("中文资源.jar"));
        Locale.setDefault(Locale.GERMAN);
        Files.writeString(game.resolve("options.txt"), "lang:zh_cn\n");
        assertEquals("Close", new DlcEarlyI18n().text("failure.close"));
        assertEquals("Downloading", DlcEarlyI18n.nativeText("stage.DOWNLOADING"));
    }

    @Test void clientReceivesLocalizedResourceContextAndRootCauseBeforeStartupFails() throws Exception {
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
        Files.writeString(game.resolve("options.txt"), "lang:en_us\n");
        IOException failure = new IOException("无法安装必要资源“资源包” (pack), config/pack.dc", new IOException("HTTP 403"));
        System.setProperty(DlcEarlyProgress.READY_PROPERTY, "must not survive");
        AtomicInteger notices = new AtomicInteger();
        IllegalStateException stopped = DlcEarlyFailure.stop(game, true, failure, (messages, details) -> {
            notices.incrementAndGet();
            assertNull(System.getProperty(DlcEarlyProgress.READY_PROPERTY));
            assertTrue(details.contains("已停止游戏启动"));
            assertTrue(details.contains("资源包"));
            assertTrue(details.contains("config/pack.dc"));
            assertTrue(details.contains("HTTP 403"));
            assertTrue(details.contains("dlc-bootstrap-error.log"));
        });
        assertSame(failure, stopped.getCause());
        assertEquals(1, notices.get());
        assertNull(System.getProperty(DlcEarlyProgress.READY_PROPERTY));
        String report = Files.readString(game.resolve("logs/dlc-bootstrap-error.log"));
        assertTrue(report.contains("HTTP 403"));
        assertTrue(report.contains("java.io.IOException"));
    }

    @Test void dedicatedServerDoesNotOpenAnyDialog() {
        IllegalStateException stopped = DlcEarlyFailure.stop(game, false, new IOException("download failed"),
            (messages, details) -> fail("Server opened a dialog"));
        assertTrue(stopped.getMessage().contains("download failed"));
        assertNull(System.getProperty(DlcEarlyProgress.READY_PROPERTY));
    }

    @Test void brokenDialogAndUnwritableReportCannotRestoreReadyOrHideTheInstallationFailure() throws Exception {
        Files.writeString(game.resolve("logs"), "not a directory");
        IOException failure = new IOException("disk full");
        Thread.currentThread().interrupt();
        IllegalStateException stopped = DlcEarlyFailure.stop(game, true, failure, (messages, details) -> {
            assertFalse(Thread.currentThread().isInterrupted());
            System.setProperty(DlcEarlyProgress.READY_PROPERTY, "late success");
            throw new IllegalStateException("display unavailable");
        });
        assertSame(failure, stopped.getCause());
        assertTrue(stopped.getMessage().contains("disk full"));
        assertNull(System.getProperty(DlcEarlyProgress.READY_PROPERTY));
        assertTrue(Thread.currentThread().isInterrupted());
    }
}
