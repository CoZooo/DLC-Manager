package com.cozooo.dlc_manager.platform;

import com.cozooo.dlc_manager.client.DlcMinecraftSupport;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;

public final class DlcPlatformServicesProvider implements DlcPlatformServices.Provider {
    public static void init() {
        DlcPlatformServices.setProvider(new DlcPlatformServicesProvider());
    }

    @Override
    public Path gameDir() {
        Path path = platformPath(Platform::getGameFolder);
        return path == null ? Path.of("").toAbsolutePath().normalize() : path;
    }

    @Override
    public Path configDir() {
        Path path = platformPath(Platform::getConfigFolder);
        return path == null ? gameDir().resolve("config") : path;
    }

    @Override
    public boolean isModLoaded(String modId) {
        if (modId == null || modId.isBlank()) {
            return false;
        }
        try {
            return Platform.isModLoaded(modId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean isDedicatedServer() {
        return platformEnvironment() == Env.SERVER;
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
        try {
            if (Platform.isFabric()) {
                return "Fabric";
            }
            //? if >=1.21.1 {
            /*if (Platform.isNeoForge()) {
                return "NeoForge";
            }
            if (Platform.isMinecraftForge()) {
                return "Forge";
            }
            *///?} else {
            if (Platform.isForge()) {
                return "Forge";
            }
            //?}
        } catch (Throwable ignored) {
        }
        return "";
    }

    @Override
    public String mcVersion() {
        try {
            return Platform.getMinecraftVersion();
        } catch (Throwable ignored) {
            return "";
        }
    }

    @Override
    public String modVersion(String modId) {
        if (modId == null || modId.isBlank()) {
            return "";
        }
        try {
            return Platform.getOptionalMod(modId).map(dev.architectury.platform.Mod::getVersion).orElse("");
        } catch (Throwable ignored) {
            return "";
        }
    }

    @Override
    public Path modFilePath(String modId) {
        if (modId == null || modId.isBlank()) {
            return null;
        }
        try {
            return Platform.getOptionalMod(modId)
                .flatMap(mod -> mod.getFilePaths().stream().findFirst())
                .orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
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
        return platformEnvironment() == Env.CLIENT;
    }

    private static Env platformEnvironment() {
        try {
            return Platform.getEnvironment();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Path platformPath(PathSupplier supplier) {
        try {
            return supplier.get();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @FunctionalInterface
    private interface PathSupplier {
        Path get();
    }
}
