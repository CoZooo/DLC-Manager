package com.cozooo.dlc_manager.platform;

import java.nio.file.Path;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.client.Minecraft;

public final class NeoForgePlatformServices implements PlatformServices.Provider {
    public static void init() {
        PlatformServices.setProvider(new NeoForgePlatformServices());
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public String languageCode() {
        try {
            return Minecraft.getInstance().options.languageCode;
        } catch (Exception ignored) {
            return "en_us";
        }
    }
}
