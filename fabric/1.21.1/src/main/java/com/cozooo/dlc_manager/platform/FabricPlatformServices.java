package com.cozooo.dlc_manager.platform;

import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public final class FabricPlatformServices implements PlatformServices.Provider {
    public static void init() {
        PlatformServices.setProvider(new FabricPlatformServices());
    }

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
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
