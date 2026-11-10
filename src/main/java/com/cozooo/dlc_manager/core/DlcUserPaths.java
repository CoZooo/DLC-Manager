package com.cozooo.dlc_manager.core;

import java.nio.file.Path;

public final class DlcUserPaths {
    private static final String OVERRIDE_PROPERTY = "dlc_manager.user.dir";
    private static final String DIRECTORY = ".dlc_manager";
    private static final String CONFIG_DIRECTORY = "config";
    private static final String STATE_DIRECTORY = "state";

    private DlcUserPaths() {
    }

    public static Path userDir() {
        String override = System.getProperty(OVERRIDE_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        String home = System.getProperty("user.home");
        Path base;
        if (home == null || home.isBlank()) {
            base = Path.of(System.getProperty("java.io.tmpdir", "."));
        } else {
            base = Path.of(home);
        }
        return base.resolve(DIRECTORY).toAbsolutePath().normalize();
    }

    public static Path configDir() {
        return userDir().resolve(CONFIG_DIRECTORY);
    }

    public static Path stateDir() {
        return userDir().resolve(STATE_DIRECTORY);
    }

    public static Path configFile(String fileName) {
        return configDir().resolve(fileName);
    }

    public static Path stateFile(String fileName) {
        return stateDir().resolve(fileName);
    }
}
