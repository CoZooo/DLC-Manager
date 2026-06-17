package com.cozooo.dlc_manager.core;

import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.test.TestPlatformProvider;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlcStateConfigTest {
    @TempDir
    Path tempDir;

    private Path gameDir;
    private Path configDir;

    @BeforeEach
    void setUp() {
        gameDir = tempDir.resolve("game");
        configDir = tempDir.resolve("config");
        PlatformServices.setProvider(new TestPlatformProvider(gameDir, configDir));
    }

    @AfterEach
    void tearDown() {
        PlatformServices.setProvider(null);
    }

    @Test
    void saveAndLoadPersistsStateUnderDlcSection() throws Exception {
        Files.createDirectories(gameDir.resolve("mods"));
        Files.writeString(gameDir.resolve("mods").resolve("a.jar"), "present");
        DlcStateConfig.load();
        DlcStateConfig.DlcState state = new DlcStateConfig.DlcState();
        state.enabled = true;
        state.appliedTargets = List.of("mods/a.jar");
        DlcStateConfig.setState("mod:DLC|a.jar", state);

        DlcStateConfig.save();
        DlcStateConfig.load();

        DlcStateConfig.DlcState loaded = DlcStateConfig.getState("mod:DLC|a.jar");
        assertNotNull(loaded, () -> readStateFile());
        assertTrue(loaded.enabled);
        assertEquals(List.of("mods/a.jar"), loaded.appliedTargets);
    }

    @Test
    void loadClearsInMemoryStateWhenStateFileDoesNotExist() {
        DlcStateConfig.load();
        DlcStateConfig.DlcState state = new DlcStateConfig.DlcState();
        state.enabled = true;
        state.appliedTargets = List.of("mods/a.jar");
        DlcStateConfig.setState("mod:DLC|a.jar", state);

        PlatformServices.setProvider(new TestPlatformProvider(tempDir.resolve("other-game"), tempDir.resolve("other-config")));
        DlcStateConfig.load();

        assertTrue(DlcStateConfig.getAllStates().isEmpty());
    }

    @Test
    void loadRecoversFlattenedLegacyStateMapAndNormalizesMissingEnabledTargets() throws Exception {
        Path stateFile = configDir.resolve("dlc_manager").resolve("state.dc");
        Files.createDirectories(stateFile.getParent());
        JsonObject root = new JsonObject();
        root.add("mod:DLC|missing.jar", stateObject("mods/missing.jar"));
        root.add("mod:DLC|present.jar", stateObject("mods/present.jar"));
        DlcDcFormatUtil.writeGeneric(stateFile, root);
        Files.createDirectories(gameDir.resolve("mods"));
        Files.writeString(gameDir.resolve("mods").resolve("present.jar"), "present");

        DlcStateConfig.load();

        assertFalse(DlcStateConfig.getState("mod:DLC|missing.jar").enabled);
        assertTrue(DlcStateConfig.getState("mod:DLC|present.jar").enabled);
    }

    private String readStateFile() {
        Path stateFile = configDir.resolve("dlc_manager").resolve("state.dc");
        if (!Files.exists(stateFile)) {
            return "missing state.dc";
        }
        try {
            return Files.readString(stateFile);
        } catch (Exception exception) {
            return exception.toString();
        }
    }

    private static JsonObject stateObject(String appliedTarget) {
        JsonObject state = new JsonObject();
        state.addProperty("enabled", true);
        JsonArray targets = new JsonArray();
        targets.add(appliedTarget);
        state.add("appliedTargets", targets);
        return state;
    }
}
