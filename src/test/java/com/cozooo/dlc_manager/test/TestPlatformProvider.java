package com.cozooo.dlc_manager.test;

import com.cozooo.dlc_manager.platform.PlatformServices;

import java.nio.file.Path;

public final class TestPlatformProvider implements PlatformServices.Provider {
    private final Path gameDir;
    private final Path configDir;
    private final boolean taczLoaded;

    public TestPlatformProvider(Path gameDir, Path configDir) {
        this(gameDir, configDir, false);
    }

    public TestPlatformProvider(Path gameDir, Path configDir, boolean taczLoaded) {
        this.gameDir = gameDir;
        this.configDir = configDir;
        this.taczLoaded = taczLoaded;
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
        return taczLoaded && "tacz".equals(modId);
    }

    @Override
    public boolean isDedicatedServer() {
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
