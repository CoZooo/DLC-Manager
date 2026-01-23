package com.cozooo.dlc_manager.platform;

import java.nio.file.Path;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.SharedConstants;

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

    @Override
    public String loaderName() {
        return "Fabric";
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
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(modId);
        return container.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("");
    }

    @Override
    public Path modFilePath(String modId) {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(modId);
        if (container.isEmpty()) {
            return null;
        }
        try {
            return container.get().getOrigin().getPaths().stream().findFirst().orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }
}
