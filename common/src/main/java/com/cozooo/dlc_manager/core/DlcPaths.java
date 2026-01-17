package com.cozooo.dlc_manager.core;

import java.nio.file.Path;
import com.cozooo.dlc_manager.platform.PlatformServices;

public class DlcPaths {
    public static Path gameDir() {
        return PlatformServices.get().gameDir();
    }

    public static Path dlcRoot() {
        return DlcPathConfig.getDlcRoot();
    }

    public static Path modsDir() {
        return gameDir().resolve("mods");
    }

    public static Path resourcePacksDir() {
        return DlcPathConfig.getResourcepackRoot();
    }

    public static Path taczDir() {
        return gameDir().resolve("tacz");
    }

    public static Path datapackStagingDir() {
        return DlcPathConfig.getDatapackRoot();
    }
}
