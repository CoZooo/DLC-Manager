package com.cozooo.dlc_manager.client.preset;

import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.test.TestPlatformProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcPresetStateStoreTest {
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
    void saveAndLoadPresetStatuses() {
        LinkedHashMap<String, DlcPresetStatus> statuses = new LinkedHashMap<>();
        statuses.put("preset-a", DlcPresetStatus.ON);
        statuses.put("preset-b", DlcPresetStatus.PART);
        statuses.put("", DlcPresetStatus.OFF);
        statuses.put("preset-null", null);

        DlcPresetStateStore.savePresetStatus(statuses);
        Map<String, DlcPresetStatus> loaded = DlcPresetStateStore.loadPresetStatus();

        assertEquals(DlcPresetStatus.ON, loaded.get("preset-a"));
        assertEquals(DlcPresetStatus.PART, loaded.get("preset-b"));
        assertEquals(2, loaded.size());
        assertTrue(Files.exists(tempDir.resolve("config").resolve("dlc_manager").resolve("state.dc")));
    }

    @Test
    void savePresetStatusReplacesExistingPresetsSectionCaseInsensitively() throws Exception {
        Path path = tempDir.resolve("config").resolve("dlc_manager").resolve("state.dc");
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
            [Presets]
            old = "On"
            """);

        DlcPresetStateStore.savePresetStatus(Map.of("new", DlcPresetStatus.OFF));
        Map<String, DlcPresetStatus> loaded = DlcPresetStateStore.loadPresetStatus();

        assertEquals(Map.of("new", DlcPresetStatus.OFF), loaded);
    }
}
