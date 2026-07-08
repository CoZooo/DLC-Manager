package com.cozooo.dlc_manager.platform;

import com.cozooo.dlc_manager.client.DlcMinecraftSupport;
import java.nio.file.Path;
import java.util.Optional;
//? if fabric {
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
//?}
//? if forge {
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
//?}
//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
//?}
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;

public final class DlcPlatformServicesProvider implements DlcPlatformServices.Provider {
    public static void init() {
        DlcPlatformServices.setProvider(new DlcPlatformServicesProvider());
    }

    @Override
    public Path gameDir() {
        //? if fabric {
        if (active()) {
            return FabricLoader.getInstance().getGameDir();
        }
        //?}
        //? if forge || neoforge {
        if (active()) {
            return FMLPaths.GAMEDIR.get();
        }
        //?}
        return Path.of("").toAbsolutePath().normalize();
    }

    @Override
    public Path configDir() {
        //? if fabric {
        if (active()) {
            return FabricLoader.getInstance().getConfigDir();
        }
        //?}
        //? if forge || neoforge {
        if (active()) {
            return FMLPaths.CONFIGDIR.get();
        }
        //?}
        return gameDir().resolve("config");
    }

    @Override
    public boolean isModLoaded(String modId) {
        //? if fabric {
        if (active()) {
            return FabricLoader.getInstance().isModLoaded(modId);
        }
        //?}
        //? if forge || neoforge {
        if (active()) {
            return ModList.get().isLoaded(modId);
        }
        //?}
        return false;
    }

    @Override
    public boolean isDedicatedServer() {
        //? if fabric {
        if (active()) {
            return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
        }
        //?}
        //? if forge || neoforge {
        if (active()) {
            return FMLEnvironment.dist == Dist.DEDICATED_SERVER;
        }
        //?}
        return false;
    }

    @Override
    public String languageCode() {
        if (!isClient()) {
            return "en_us";
        }
        try {
            return Minecraft.getInstance().options.languageCode;
        } catch (Throwable ignored) {
            String fromOptions = DlcPlatformServices.readLanguageFromOptions(gameDir());
            return fromOptions == null ? "en_us" : fromOptions;
        }
    }

    @Override
    public String loaderName() {
        //? if fabric {
        if (active()) {
            return "Fabric";
        }
        //?}
        //? if forge {
        if (active()) {
            return "Forge";
        }
        //?}
        //? if neoforge {
        if (active()) {
            return "NeoForge";
        }
        //?}
        return "";
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
        //? if fabric {
        if (active()) {
            Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(modId);
            return container.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("");
        }
        //?}
        //? if forge || neoforge {
        if (active()) {
            return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("");
        }
        //?}
        return "";
    }

    @Override
    public Path modFilePath(String modId) {
        //? if fabric {
        if (active()) {
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
        //?}
        //? if forge || neoforge {
        if (active()) {
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
        //?}
        return null;
    }

    @Override
    public boolean isResourcePackSelected(String fileName) {
        return DlcMinecraftSupport.isResourcePackSelected(fileName);
    }

    @Override
    public String playerName() {
        if (!isClient()) {
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
        if (!isClient()) {
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

    private static boolean isClient() {
        //? if fabric {
        if (active()) {
            return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
        }
        //?}
        //? if forge || neoforge {
        if (active()) {
            return FMLEnvironment.dist == Dist.CLIENT;
        }
        //?}
        return false;
    }

    private static boolean active() {
        return System.getProperty("java.vm.name") != null;
    }
}
