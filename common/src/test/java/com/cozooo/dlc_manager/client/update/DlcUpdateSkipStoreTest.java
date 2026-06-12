package com.cozooo.dlc_manager.client.update;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcUpdateSkipStoreTest {
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
    void writeReadAndRemoveSection() {
        JsonObject section = new JsonObject();
        section.addProperty("version", "1.2.0+1.0.6");

        DlcUpdateSkipStore.writeSection(DlcUpdateSkipStore.SECTION_PACK_UPDATE, section);
        JsonObject loaded = DlcUpdateSkipStore.readSection(DlcUpdateSkipStore.SECTION_PACK_UPDATE);

        assertEquals("1.2.0+1.0.6", loaded.get("version").getAsString());
        assertTrue(Files.exists(tempDir.resolve("config").resolve("dlc_manager").resolve("update_skip.dc")));

        DlcUpdateSkipStore.removeSection(DlcUpdateSkipStore.SECTION_PACK_UPDATE);
        assertNull(DlcUpdateSkipStore.readSection(DlcUpdateSkipStore.SECTION_PACK_UPDATE));
    }

    @Test
    void emptySectionRemovesExistingSection() {
        JsonObject section = new JsonObject();
        section.addProperty("version", "3.9.4");
        DlcUpdateSkipStore.writeSection(DlcUpdateSkipStore.SECTION_SELF_UPDATE, section);

        DlcUpdateSkipStore.writeSection(DlcUpdateSkipStore.SECTION_SELF_UPDATE, new JsonObject());

        assertNull(DlcUpdateSkipStore.readSection(DlcUpdateSkipStore.SECTION_SELF_UPDATE));
    }
}
