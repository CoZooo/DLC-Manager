package com.cozooo.dlc_manager.core;

import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public class DlcPaths {
    public static Path gameDir() {
        return FMLPaths.GAMEDIR.get();
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
