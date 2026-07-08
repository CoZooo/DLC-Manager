package com.cozooo.dlc_manager.platform

import com.cozooo.dlc_manager.client.DlcMinecraftSupport
//? if fabric {
import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.FabricLoader
//?}
//? if forge {
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraftforge.fml.loading.FMLPaths
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo
//?}
//? if neoforge {
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.fml.loading.moddiscovery.ModFileInfo
//?}
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import java.nio.file.Path

class DlcPlatformServicesProvider : DlcPlatformServices.Provider {
    override fun gameDir(): Path {
        //? if fabric {
        if (active()) {
            return FabricLoader.getInstance().gameDir
        }
        //?}
        //? if forge || neoforge {
        if (active()) {
            return FMLPaths.GAMEDIR.get()
        }
        //?}
        return Path.of("").toAbsolutePath().normalize()
    }

    override fun configDir(): Path {
        //? if fabric {
        if (active()) {
            return FabricLoader.getInstance().configDir
        }
        //?}
        //? if forge || neoforge {
        if (active()) {
            return FMLPaths.CONFIGDIR.get()
        }
        //?}
        return gameDir().resolve("config")
    }

    override fun isModLoaded(modId: String?): Boolean {
        //? if fabric {
        if (active()) {
            return FabricLoader.getInstance().isModLoaded(modId)
        }
        //?}
        //? if forge || neoforge {
        if (active()) {
            return ModList.get().isLoaded(modId)
        }
        //?}
        return false
    }

    override fun isDedicatedServer(): Boolean {
        //? if fabric {
        if (active()) {
            return FabricLoader.getInstance().environmentType == EnvType.SERVER
        }
        //?}
        //? if forge || neoforge {
        if (active()) {
            return FMLEnvironment.dist == Dist.DEDICATED_SERVER
        }
        //?}
        return false
    }

    override fun languageCode(): String {
        if (!isClient()) {
            return "en_us"
        }
        return try {
            Minecraft.getInstance().options.languageCode
        } catch (_: Throwable) {
            DlcPlatformServices.readLanguageFromOptions(gameDir()) ?: "en_us"
        }
    }

    override fun loaderName(): String {
        //? if fabric {
        if (active()) {
            return "Fabric"
        }
        //?}
        //? if forge {
        if (active()) {
            return "Forge"
        }
        //?}
        //? if neoforge {
        if (active()) {
            return "NeoForge"
        }
        //?}
        return ""
    }

    override fun mcVersion(): String {
        return try {
            SharedConstants.getCurrentVersion().name
        } catch (_: Exception) {
            ""
        }
    }

    override fun modVersion(modId: String?): String {
        //? if fabric {
        if (active()) {
            return FabricLoader.getInstance().getModContainer(modId)
                .map { it.metadata.version.friendlyString }
                .orElse("")
        }
        //?}
        //? if forge || neoforge {
        if (active()) {
            return ModList.get().getModContainerById(modId)
                .map { it.modInfo.version.toString() }
                .orElse("")
        }
        //?}
        return ""
    }

    override fun modFilePath(modId: String?): Path? {
        //? if fabric {
        if (active()) {
            val container = FabricLoader.getInstance().getModContainer(modId)
            if (container.isEmpty) {
                return null
            }
            return try {
                container.get().origin.paths.stream().findFirst().orElse(null)
            } catch (_: Exception) {
                null
            }
        }
        //?}
        //? if forge || neoforge {
        if (active()) {
            val modFile = ModList.get().getModContainerById(modId)
                .map { it.modInfo.owningFile as ModFileInfo }
            if (modFile.isEmpty) {
                return null
            }
            return try {
                modFile.get().file.filePath
            } catch (_: Exception) {
                null
            }
        }
        //?}
        return null
    }

    override fun isResourcePackSelected(fileName: String?): Boolean {
        return DlcMinecraftSupport.isResourcePackSelected(fileName)
    }

    override fun playerName(): String {
        if (!isClient()) {
            return ""
        }
        return try {
            val player = Minecraft.getInstance().player ?: return ""
            player.gameProfile.name ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    override fun playerUuid(): String {
        if (!isClient()) {
            return ""
        }
        return try {
            val player = Minecraft.getInstance().player ?: return ""
            player.gameProfile.id?.toString() ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    companion object {
        @JvmStatic
        fun init() {
            DlcPlatformServices.setProvider(DlcPlatformServicesProvider())
        }

        private fun isClient(): Boolean {
            //? if fabric {
            if (active()) {
                return FabricLoader.getInstance().environmentType == EnvType.CLIENT
            }
            //?}
            //? if forge || neoforge {
            if (active()) {
                return FMLEnvironment.dist == Dist.CLIENT
            }
            //?}
            return false
        }

        private fun active(): Boolean {
            return System.getProperty("java.vm.name") != null
        }
    }
}
