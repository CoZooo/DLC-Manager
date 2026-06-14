package com.cozooo.dlc_manager.client.remote;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcRedirectLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadReadsOnlyRedirectEntriesWithRedirectUrl() throws Exception {
        Path redirect = tempDir.resolve("remote").resolve("redirect");
        Files.createDirectories(redirect);
        Files.writeString(redirect.resolve("main.dc"), """
            RedirectUrl = "https://example.com/modpack"
            available = ["zh_cn", "en_us"]

            [DisplayName]
            zh_cn = "中文"
            en_us = "English"

            [notice]
            en_us = "Notice"

            [tip]
            en_us = "Tip"
            """);
        Files.writeString(redirect.resolve("ignored.dc"), """
            DisplayName = { en_us = "No redirect" }
            """);
        Files.writeString(redirect.resolve("ignored.txt"), """
            RedirectUrl = "https://example.com/ignored"
            """);

        List<DlcRedirectEntry> entries = DlcRedirectLoader.load(tempDir);

        assertEquals(1, entries.size());
        DlcRedirectEntry entry = entries.get(0);
        assertEquals("https://example.com/modpack", entry.getRedirectUrl());
        assertEquals("中文", entry.resolveDisplayName("zh_cn"));
        assertEquals("Notice", entry.resolveNotice("zh_cn"));
        assertEquals("Tip", entry.resolveTip("zh_cn"));
        assertTrue(entry.isAvailableFor("en_us"));
        assertEquals(redirect.resolve("main.dc"), entry.getConfigPath());
    }

    @Test
    void missingRootReturnsEmptyList() {
        assertEquals(List.of(), DlcRedirectLoader.load(tempDir));
        assertEquals(List.of(), DlcRedirectLoader.load(null));
    }
}
