package com.cozooo.dlc_manager.platform;

import java.lang.reflect.Method;
import java.nio.file.Path;

public final class CrossPlatformRuntime {
    private static final String ARCH_PLATFORM_CLASS = "dev.architectury.platform.Platform";
    private static final Method PLATFORM_IS_MOD_LOADED;
    private static final Method PLATFORM_GET_ENV;
    private static final Method PLATFORM_GAME_DIR;
    private static final Method PLATFORM_CONFIG_DIR;

    static {
        Method isModLoaded = null;
        Method getEnv = null;
        Method gameDir = null;
        Method configDir = null;
        try {
            Class<?> platformClass = Class.forName(ARCH_PLATFORM_CLASS);
            isModLoaded = platformClass.getMethod("isModLoaded", String.class);
            getEnv = findMethod(platformClass, "getEnv", "getEnvironment");
            gameDir = findMethod(platformClass, "getGameFolder", "getGameDir", "getGameDirectory");
            configDir = findMethod(platformClass, "getConfigFolder", "getConfigDir", "getConfigDirectory");
        } catch (Throwable ignored) {
            isModLoaded = null;
            getEnv = null;
            gameDir = null;
            configDir = null;
        }
        PLATFORM_IS_MOD_LOADED = isModLoaded;
        PLATFORM_GET_ENV = getEnv;
        PLATFORM_GAME_DIR = gameDir;
        PLATFORM_CONFIG_DIR = configDir;
    }

    private CrossPlatformRuntime() {
    }

    public static boolean isModLoaded(String modId) {
        if (PLATFORM_IS_MOD_LOADED != null) {
            try {
                Object result = PLATFORM_IS_MOD_LOADED.invoke(null, modId);
                if (result instanceof Boolean loaded) {
                    return loaded;
                }
            } catch (Throwable ignored) {
            }
        }
        return PlatformServices.get().isModLoaded(modId);
    }

    public static boolean isDedicatedServer() {
        if (PLATFORM_GET_ENV != null) {
            try {
                Object env = PLATFORM_GET_ENV.invoke(null);
                if (env != null) {
                    String value = env.toString().toUpperCase();
                    if (value.contains("SERVER")) {
                        return true;
                    }
                    if (value.contains("CLIENT")) {
                        return false;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return !isClientByClassPresence();
    }

    public static Path gameDir() {
        if (PLATFORM_GAME_DIR != null) {
            try {
                Object result = PLATFORM_GAME_DIR.invoke(null);
                if (result instanceof Path path) {
                    return path;
                }
            } catch (Throwable ignored) {
            }
        }
        return PlatformServices.get().gameDir();
    }

    public static Path configDir() {
        if (PLATFORM_CONFIG_DIR != null) {
            try {
                Object result = PLATFORM_CONFIG_DIR.invoke(null);
                if (result instanceof Path path) {
                    return path;
                }
            } catch (Throwable ignored) {
            }
        }
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

    private static Method findMethod(Class<?> type, String... names) {
        for (String name : names) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static boolean isClientByClassPresence() {
        try {
            Class.forName("net.minecraft.client.Minecraft");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
