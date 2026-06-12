package com.cozooo.dlc_manager.test;

import com.cozooo.dlc_manager.platform.PlatformServices;

import java.nio.file.Path;

public final class TestPlatformProvider implements PlatformServices.Provider {
    private final Path gameDir;
    private final Path configDir;

    public TestPlatformProvider(Path gameDir, Path configDir) {
        this.gameDir = gameDir;
        this.configDir = configDir;
    }

    @Override
    public Path gameDir() {
        return gameDir;
    }

    @Override
    public Path configDir() {
        return configDir;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return false;
    }

    @Override
    public String languageCode() {
        return "en_us";
    }

    @Override
    public String loaderName() {
        return "fabric";
    }

    @Override
    public String mcVersion() {
        return "1.20.1";
    }

    @Override
    public String modVersion(String modId) {
        return "";
    }

    @Override
    public Path modFilePath(String modId) {
        return null;
    }

    @Override
    public String playerName() {
        return "Player";
    }

    @Override
    public String playerUuid() {
        return "00000000-0000-0000-0000-000000000000";
    }
}
