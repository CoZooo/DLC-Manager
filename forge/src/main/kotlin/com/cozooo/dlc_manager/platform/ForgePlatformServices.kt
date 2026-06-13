package com.cozooo.dlc_manager.platform

import net.minecraft.SharedConstants
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraftforge.fml.loading.FMLPaths
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo
import java.nio.file.Files
import java.nio.file.Path

class ForgePlatformServices : PlatformServices.Provider {
    override fun gameDir(): Path {
        return FMLPaths.GAMEDIR.get()
    }

    override fun configDir(): Path {
        return FMLPaths.CONFIGDIR.get()
    }

    override fun isModLoaded(modId: String?): Boolean {
        return ModList.get().isLoaded(modId)
    }

    override fun languageCode(): String {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return "en_us"
        }
        return try {
            val mcClass = Class.forName("net.minecraft.client.Minecraft")
            val mc = mcClass.getMethod("getInstance").invoke(null)
            val options = mcClass.getField("options").get(mc)
            try {
                options.javaClass.getField("languageCode").get(options) as String
            } catch (_: NoSuchFieldException) {
                options.javaClass.getMethod("getLanguage").invoke(options) as String
            }
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
        return "Forge"
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
            val profile = player.javaClass.getMethod("getGameProfile").invoke(player)
            val value = profile.javaClass.getMethod("getName").invoke(profile)
            value?.toString() ?: ""
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
            val profile = player.javaClass.getMethod("getGameProfile").invoke(player)
            val id = profile.javaClass.getMethod("getId").invoke(profile)
            id?.toString() ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    private fun currentPlayer(): Any? {
        val mcClass = Class.forName("net.minecraft.client.Minecraft")
        val mc = mcClass.getMethod("getInstance").invoke(null)
        return mcClass.getField("player").get(mc)
    }

    companion object {
        @JvmStatic
        fun init() {
            PlatformServices.setProvider(ForgePlatformServices())
        }
    }
}
