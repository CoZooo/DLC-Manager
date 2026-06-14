package com.cozooo.dlc_manager.platform;

import java.nio.file.Path;

public final class CrossPlatformRuntime {
    private CrossPlatformRuntime() {
    }

    public static boolean isModLoaded(String modId) {
        return PlatformServices.get().isModLoaded(modId);
    }

    public static boolean isDedicatedServer() {
        return PlatformServices.get().isDedicatedServer();
    }

    public static Path gameDir() {
        return PlatformServices.get().gameDir();
    }

    public static Path configDir() {
        return PlatformServices.get().configDir();
    }

    public static String languageCode() {
        return PlatformServices.get().languageCode();
    }

    public static String loaderName() {
        return PlatformServices.get().loaderName();
    }

    public static String mcVersion() {
        return PlatformServices.get().mcVersion();
    }

    public static String modVersion(String modId) {
        return PlatformServices.get().modVersion(modId);
    }

    public static Path modFilePath(String modId) {
        return PlatformServices.get().modFilePath(modId);
    }

    public static String playerName() {
        return PlatformServices.get().playerName();
    }

    public static String playerUuid() {
        return PlatformServices.get().playerUuid();
    }
}
