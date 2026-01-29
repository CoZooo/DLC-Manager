package com.cozooo.dlc_manager.platform;

import java.nio.file.Path;
import java.util.Optional;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
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
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return "en_us";
        }
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Object mc = mcClass.getMethod("getInstance").invoke(null);
            Object options = mcClass.getField("options").get(mc);
            try {
                return (String) options.getClass().getField("languageCode").get(options);
            } catch (NoSuchFieldException ignored) {
                return (String) options.getClass().getMethod("getLanguage").invoke(options);
            }
        } catch (Throwable ignored) {
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
