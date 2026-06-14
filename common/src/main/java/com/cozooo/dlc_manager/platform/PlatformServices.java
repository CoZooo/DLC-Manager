package com.cozooo.dlc_manager.platform;

import java.nio.file.Path;

public final class PlatformServices {
    public interface Provider {
        Path gameDir();
        Path configDir();
        boolean isModLoaded(String modId);
        boolean isDedicatedServer();
        String languageCode();
        String loaderName();
        String mcVersion();
        String modVersion(String modId);
        Path modFilePath(String modId);
        String playerName();
        String playerUuid();
    }

    private static Provider provider;

    private PlatformServices() {
    }

    public static void setProvider(Provider provider) {
        PlatformServices.provider = provider;
    }

    public static Provider get() {
        if (provider == null) {
            return DefaultProvider.INSTANCE;
        }
        return provider;
    }

    private static final class DefaultProvider implements Provider {
        private static final DefaultProvider INSTANCE = new DefaultProvider();

        @Override
        public Path gameDir() {
            return Path.of("").toAbsolutePath().normalize();
        }

        @Override
        public Path configDir() {
            return gameDir().resolve("config");
        }

        @Override
        public boolean isModLoaded(String modId) {
            return false;
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
            return "";
        }

        @Override
        public String mcVersion() {
            return "";
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
            return "";
        }

        @Override
        public String playerUuid() {
            return "";
        }
    }
}
