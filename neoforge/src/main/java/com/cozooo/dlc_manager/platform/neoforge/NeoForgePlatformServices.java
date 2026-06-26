package com.cozooo.dlc_manager.platform.neoforge;

import com.cozooo.dlc_manager.platform.PlatformServices;
import com.cozooo.dlc_manager.client.DlcMinecraftSupport;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
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
    public boolean isDedicatedServer() {
        return FMLEnvironment.dist == Dist.DEDICATED_SERVER;
    }

    @Override
    public String languageCode() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
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

    @Override
    public boolean isResourcePackSelected(String fileName) {
        return DlcMinecraftSupport.isResourcePackSelected(fileName);
    }

    @Override
    public String playerName() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
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
        if (FMLEnvironment.dist != Dist.CLIENT) {
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
