package com.cozooo.dlc_manager.platform.fabric;

import com.cozooo.dlc_manager.platform.PlatformServices;
import java.nio.file.Path;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.api.EnvType;
import net.minecraft.SharedConstants;
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
    public boolean isDedicatedServer() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
    }

    @Override
    public String languageCode() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return "en_us";
        }
        try {
            return Minecraft.getInstance().options.languageCode;
        } catch (Throwable ignored) {
            String fromOptions = readLangFromOptions();
            return fromOptions == null ? "en_us" : fromOptions;
        }
    }

    private String readLangFromOptions() {
        try {
            Path options = gameDir().resolve("options.txt");
            if (!java.nio.file.Files.exists(options)) {
                return null;
            }
            for (String line : java.nio.file.Files.readAllLines(options)) {
                if (line != null && line.startsWith("lang:")) {
                    String value = line.substring("lang:".length()).trim();
                    return value.isEmpty() ? null : value;
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
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

    @Override
    public String playerName() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return "";
        }
        try {
            if (Minecraft.getInstance().player == null || Minecraft.getInstance().player.getGameProfile() == null) {
                return "";
            }
            String value = Minecraft.getInstance().player.getGameProfile().getName();
            return value == null ? "" : value;
        } catch (Throwable ignored) {
            return "";
        }
    }

    @Override
    public String playerUuid() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return "";
        }
        try {
            if (Minecraft.getInstance().player == null || Minecraft.getInstance().player.getGameProfile() == null
                || Minecraft.getInstance().player.getGameProfile().getId() == null) {
                return "";
            }
            return Minecraft.getInstance().player.getGameProfile().getId().toString();
        } catch (Throwable ignored) {
            return "";
        }
    }
}
