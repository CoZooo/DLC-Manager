package com.cozooo.dlc_manager.client.preset;

import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.test.TestPlatformProvider;
import com.cozooo.dlc_manager.core.DlcDcConfigIO;
import com.google.gson.JsonObject;
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
        assertTrue(Files.exists(statePath()));
    }

    @Test
    void savePresetStatusReplacesExistingPresetsSectionCaseInsensitively() throws Exception {
        Path path = statePath();
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
            [Presets]
            old = "On"
            """);

        DlcPresetStateStore.savePresetStatus(Map.of("new", DlcPresetStatus.OFF));
        Map<String, DlcPresetStatus> loaded = DlcPresetStateStore.loadPresetStatus();

        assertEquals(Map.of("new", DlcPresetStatus.OFF), loaded);
    }

    @Test
    void savePresetStatusKeepsExistingDlcSection() throws Exception {
        Path path = statePath();
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
            [DLC]
            "mod:DLC|a.jar" = { enabled = true }
            """);

        DlcPresetStateStore.savePresetStatus(Map.of("preset-a", DlcPresetStatus.ON));

        JsonObject root = DlcDcConfigIO.read(path, DlcDcConfigIO.Layout.GENERIC);
        assertEquals(1, DlcDcConfigIO.getObjectIgnoreCase(root, "DLC").size());
    }

    @Test
    void loadReadsPresetsFromLegacyConfigState() throws Exception {
        Path legacy = tempDir.resolve("config").resolve("dlc_manager").resolve("state.dc");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, """
            [presets]
            old-preset = "On"
            """);

        assertEquals(DlcPresetStatus.ON, DlcPresetStateStore.loadPresetStatus().get("old-preset"));
    }

    private Path statePath() {
        return tempDir.resolve("game").resolve("DLC").resolve("tmp").resolve("state.dc");
    }
}
