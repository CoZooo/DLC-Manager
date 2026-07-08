package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.client.server.DlcServerRemoteItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcServerDownloadServiceRemoteListTest {
    @TempDir
    Path tempDir;

    @Test
    void collectRemoteItemsReturnsPresentAndMissingEntriesWithMetadata() throws Exception {
        Path remote = tempDir.resolve("remote");
        Files.createDirectories(remote.resolve("mods"));
        Files.writeString(remote.resolve("present.dc"), """
            identifier = "present"
            file_name = "present.jar"
            type = "mod"
            directUrl = "https://example.com/present.jar"

            [DisplayName]
            en_us = "Present Mod"
            zh_cn = "已存在模组"

            [tip]
            en_us = "Present tooltip"
            """);
        Files.writeString(remote.resolve("mods").resolve("missing.dc"), """
            identifier = "missing"
            file_name = "missing.jar"
            type = "resourcepack"
            directUrl = "https://example.com/missing.jar"

            [DisplayName]
            en_us = "Missing Pack"

            [tip]
            en_us = "Missing tooltip"
            """);
        Files.writeString(tempDir.resolve("present.jar"), "already here");

        List<DlcServerRemoteItem> items = DlcServerDownloadService.collectRemoteItems(tempDir, "en_us");

        assertEquals(2, items.size());
        DlcServerRemoteItem present = items.stream().filter(item -> "present".equals(item.key)).findFirst().orElseThrow();
        assertEquals("Present Mod", present.name);
        assertEquals("MOD", present.type);
        assertEquals("__root__", present.folder);
        assertEquals("Present tooltip", present.tips.get("en_us"));
        assertEquals("已存在模组", present.displayNames.get("zh_cn"));
        assertTrue(present.done);
        assertTrue(present.success);

        DlcServerRemoteItem missing = items.stream().filter(item -> "missing".equals(item.key)).findFirst().orElseThrow();
        assertEquals("Missing Pack", missing.name);
        assertEquals("RESOURCE_PACK", missing.type);
        assertEquals("mods", missing.folder);
        assertEquals("Missing tooltip", missing.tips.get("en_us"));
        assertFalse(missing.done);
        assertFalse(missing.success);
    }
}
