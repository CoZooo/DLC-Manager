package com.cozooo.dlc_manager.platform;

import java.nio.file.Files;
import java.nio.file.Path;

public final class DlcPlatformServices {
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
        default boolean isResourcePackSelected(String fileName) {
            return false;
        }
        String playerName();
        String playerUuid();
    }

    private static Provider provider;

    private DlcPlatformServices() {
    }

    public static void setProvider(Provider provider) {
        DlcPlatformServices.provider = provider;
    }

    public static Provider get() {
        if (provider == null) {
            return DefaultProvider.INSTANCE;
        }
        return provider;
    }

    public static boolean isModLoaded(String modId) {
        return get().isModLoaded(modId);
    }

    public static boolean isDedicatedServer() {
        return get().isDedicatedServer();
    }

    public static Path gameDir() {
        return get().gameDir();
    }

    public static Path configDir() {
        return get().configDir();
    }

    public static String languageCode() {
        return get().languageCode();
    }

    public static String loaderName() {
        return get().loaderName();
    }

    public static String mcVersion() {
        return get().mcVersion();
    }

    public static String modVersion(String modId) {
        return get().modVersion(modId);
    }

    public static Path modFilePath(String modId) {
        return get().modFilePath(modId);
    }

    public static boolean isResourcePackSelected(String fileName) {
        return get().isResourcePackSelected(fileName);
    }

    public static String playerName() {
        return get().playerName();
    }

    public static String playerUuid() {
        return get().playerUuid();
    }

    public static String readLanguageFromOptions(Path gameDir) {
        if (gameDir == null) {
            return null;
        }
        try {
            Path options = gameDir.resolve("options.txt");
            if (!Files.exists(options)) {
                return null;
            }
            for (String line : Files.readAllLines(options)) {
                if (line != null && line.startsWith("lang:")) {
                    String value = line.substring("lang:".length()).trim();
                    return value.isEmpty() ? null : value;
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
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
