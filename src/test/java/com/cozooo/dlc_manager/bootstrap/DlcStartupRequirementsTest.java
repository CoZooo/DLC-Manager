package com.cozooo.dlc_manager.bootstrap;

import com.cozooo.dlc_manager.core.DlcConfig;
import com.cozooo.dlc_manager.platform.DlcPlatformServices;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class DlcStartupRequirementsTest {
    @TempDir Path game;

    @AfterEach void reset() {
        System.clearProperty(DlcStartupRequirements.READY_PROPERTY);
        DlcPlatformServices.setProvider(null);
        DlcConfig.setDlcRoot("DLC");
    }

    private void platform(String loader) {
        DlcPlatformServices.setProvider(new DlcPlatformServices.Provider() {
            @Override public Path gameDir() { return game; }
            @Override public Path configDir() { return game.resolve("config"); }
            @Override public String loaderName() { return loader; }
            @Override public boolean isDedicatedServer() { return false; }
            @Override public boolean isModLoaded(String mod) { return false; }
            @Override public String languageCode() { return "en_us"; }
            @Override public String mcVersion() { return "1.20.1"; }
            @Override public String modVersion(String mod) { return ""; }
            @Override public Path modFilePath(String mod) { return null; }
            @Override public String playerName() { return ""; }
            @Override public String playerUuid() { return ""; }
        });
        DlcConfig.setDlcRoot("DLC");
    }

    private void resource() throws Exception {
        Path required = game.resolve("DLC/required");
        Files.createDirectories(required);
        Files.writeString(required.resolve("main.dc"), "identifier = \"main\"\nfile_name = \"main.jar\"\nappliedTarget = \"mods/main.jar\"\n");
        Files.writeString(required.resolve("main.jar"), "main");
    }

    @ParameterizedTest @ValueSource(strings = {"Forge", "NeoForge"})
    void missingOrWrongInstanceCompletionBlocksGameInitialization(String loader) {
        platform(loader);
        assertTrue(DlcStartupRequirements.usesEarlyInstallation());
        var failure = assertThrows(IllegalStateException.class, DlcStartupRequirements::verifyBeforeGameLoad);
        assertTrue(failure.getMessage().startsWith(DlcBootstrapI18n.text("failure.summary")));
        DlcStartupRequirements.markComplete(game.resolve("other-instance"));
        assertThrows(IllegalStateException.class, DlcStartupRequirements::verifyBeforeGameLoad);
    }

    @ParameterizedTest @ValueSource(strings = {"Forge", "NeoForge"})
    void successfulEarlyInstallAllowsLoadingButDeletedResourceStillBlocks(String loader) throws Exception {
        platform(loader);
        resource();
        DlcEarlyInstaller.install(game, game.resolve("config"), loader, "1.20.1", false, log -> {});
        DlcStartupRequirements.markComplete(game);
        assertDoesNotThrow(DlcStartupRequirements::verifyBeforeGameLoad);
        Files.delete(game.resolve("mods/main.jar"));
        var failure = assertThrows(IllegalStateException.class, DlcStartupRequirements::verifyBeforeGameLoad);
        assertTrue(failure.getMessage().startsWith(DlcBootstrapI18n.text("failure.summary")));
        assertFalse(Files.exists(game.resolve("mods/main.jar")));
        assertNull(System.getProperty(DlcStartupRequirements.READY_PROPERTY));
    }

    @ParameterizedTest @ValueSource(strings = {"Forge", "NeoForge"})
    void completedMarkerCannotBypassPendingInstallationOrInvalidFile(String loader) throws Exception {
        platform(loader);
        resource();
        DlcStartupRequirements.markComplete(game);
        assertThrows(IllegalStateException.class, DlcStartupRequirements::verifyBeforeGameLoad);
        Files.createDirectories(game.resolve("mods/main.jar"));
        assertThrows(IllegalStateException.class, DlcStartupRequirements::verifyBeforeGameLoad);
    }

    @ParameterizedTest @ValueSource(strings = {"fabric", "Fabric"})
    void fabricKeepsInGameInstallation(String loader) {
        platform(loader);
        assertFalse(DlcStartupRequirements.usesEarlyInstallation());
        assertDoesNotThrow(DlcStartupRequirements::verifyBeforeGameLoad);
    }
}
