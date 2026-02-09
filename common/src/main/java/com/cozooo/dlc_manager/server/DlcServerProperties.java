package com.cozooo.dlc_manager.server;

import com.cozooo.dlc_manager.core.DlcPaths;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class DlcServerProperties {
    private DlcServerProperties() {
    }

    public static RconInfo readRconInfo() {
        return readRconInfo(DlcPaths.gameDir());
    }

    static RconInfo readRconInfo(Path gameDir) {
        Properties props = new Properties();
        Path path = gameDir.resolve("server.properties");
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                props.load(reader);
            } catch (IOException ignored) {
            }
        }
        boolean enabled = asBoolean(props.getProperty("enable-rcon"), false);
        int port = clampInt(asInt(props.getProperty("rcon.port"), 25575), 1, 65535, 25575);
        String password = props.getProperty("rcon.password", "");
        boolean passwordSet = password != null && !password.isBlank();
        return new RconInfo(enabled, port, passwordSet);
    }

    private static boolean asBoolean(String value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value.trim());
    }

    private static int asInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int clampInt(int value, int min, int max, int fallback) {
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }

    public static final class RconInfo {
        public final boolean enabled;
        public final int port;
        public final boolean passwordSet;

        private RconInfo(boolean enabled, int port, boolean passwordSet) {
            this.enabled = enabled;
            this.port = port;
            this.passwordSet = passwordSet;
        }
    }
}
