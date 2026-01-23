package com.cozooo.dlc_manager.platform;

import java.nio.file.Path;
import java.util.Optional;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.SharedConstants;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;

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

    @Override
    public String loaderName() {
        return "NeoForge";
    }

    @Override
    public String mcVersion() {
        try {
            return SharedConstants.getCurrentVersion().getName();
        } catch (Exception ignored) {
            return "";
        }
    }

    @Override
    public String modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
            .map(container -> container.getModInfo().getVersion().toString())
            .orElse("");
    }

    @Override
    public Path modFilePath(String modId) {
        Optional<ModFileInfo> modFile = ModList.get().getModContainerById(modId)
            .map(container -> (ModFileInfo) container.getModInfo().getOwningFile());
        if (modFile.isEmpty()) {
            return null;
        }
        try {
            return modFile.get().getFile().getFilePath();
        } catch (Exception ignored) {
            return null;
        }
    }
}
