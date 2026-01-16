package com.cozooo.dlc_manager.platform;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

public final class ForgePlatformServices implements PlatformServices.Provider {
    public static void init() {
        PlatformServices.setProvider(new ForgePlatformServices());
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
