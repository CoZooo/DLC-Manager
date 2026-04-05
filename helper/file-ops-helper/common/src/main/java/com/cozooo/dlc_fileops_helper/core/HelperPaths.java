package com.cozooo.dlc_fileops_helper.core;

import java.lang.reflect.Method;
import java.nio.file.Path;

public final class HelperPaths {
    private static final String ARCH_PLATFORM_CLASS = "dev.architectury.platform.Platform";
    private static final Method PLATFORM_GAME_DIR;

    static {
        Method gameDir = null;
        try {
            Class<?> platformClass = Class.forName(ARCH_PLATFORM_CLASS);
            gameDir = findMethod(platformClass, "getGameFolder", "getGameDir", "getGameDirectory");
        } catch (Throwable ignored) {
            gameDir = null;
        }
        PLATFORM_GAME_DIR = gameDir;
    }

    private HelperPaths() {
    }

    public static Path gameDir() {
        if (PLATFORM_GAME_DIR != null) {
            try {
                Object result = PLATFORM_GAME_DIR.invoke(null);
                if (result instanceof Path path) {
                    return path.toAbsolutePath().normalize();
                }
            } catch (Throwable ignored) {
            }
        }
        return Path.of("").toAbsolutePath().normalize();
    }

    public static Path dlcRoot() {
        return gameDir().resolve("DLC").normalize();
    }

    public static Path tmpRoot() {
        return dlcRoot().resolve("tmp").normalize();
    }

    public static Path pendingOpsRoot() {
        return tmpRoot().resolve("pending_file_ops").normalize();
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
}

