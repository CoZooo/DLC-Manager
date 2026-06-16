package com.cozooo.dlc_manager.platform.neoforge

import com.cozooo.dlc_manager.platform.PlatformServices
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.fml.loading.moddiscovery.ModFileInfo
import java.nio.file.Files
import java.nio.file.Path

class NeoForgePlatformServices : PlatformServices.Provider {
    override fun gameDir(): Path {
        return FMLPaths.GAMEDIR.get()
    }

    override fun configDir(): Path {
        return FMLPaths.CONFIGDIR.get()
    }

    override fun isModLoaded(modId: String?): Boolean {
        return ModList.get().isLoaded(modId)
    }

    override fun isDedicatedServer(): Boolean {
        return FMLEnvironment.dist == Dist.DEDICATED_SERVER
    }

    override fun languageCode(): String {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return "en_us"
        }
        return try {
            Minecraft.getInstance().options.languageCode
        } catch (_: Throwable) {
            readLangFromOptions() ?: "en_us"
        }
    }

    private fun readLangFromOptions(): String? {
        return try {
            val options = gameDir().resolve("options.txt")
            if (!Files.exists(options)) {
                return null
            }
            for (line in Files.readAllLines(options)) {
                if (line != null && line.startsWith("lang:")) {
                    val value = line.substring("lang:".length).trim()
                    return value.ifEmpty { null }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    override fun loaderName(): String {
        return "NeoForge"
    }

    override fun mcVersion(): String {
        return try {
            SharedConstants.getCurrentVersion().name
        } catch (_: Exception) {
            ""
        }
    }

    override fun modVersion(modId: String?): String {
        return ModList.get().getModContainerById(modId)
            .map { it.modInfo.version.toString() }
            .orElse("")
    }

    override fun modFilePath(modId: String?): Path? {
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

    override fun playerName(): String {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return ""
        }
        return try {
            val player = currentPlayer() ?: return ""
            player.gameProfile.name ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    override fun playerUuid(): String {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return ""
        }
        return try {
            val player = currentPlayer() ?: return ""
            player.gameProfile.id?.toString() ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    private fun currentPlayer() = Minecraft.getInstance().player

    companion object {
        @JvmStatic
        fun init() {
            PlatformServices.setProvider(NeoForgePlatformServices())
        }
    }
}
