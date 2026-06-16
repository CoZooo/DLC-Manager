package com.cozooo.dlc_manager.platform.fabric

import com.cozooo.dlc_manager.platform.PlatformServices
import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import java.nio.file.Files
import java.nio.file.Path

class FabricPlatformServices : PlatformServices.Provider {
    override fun gameDir(): Path {
        return FabricLoader.getInstance().gameDir
    }

    override fun configDir(): Path {
        return FabricLoader.getInstance().configDir
    }

    override fun isModLoaded(modId: String?): Boolean {
        return FabricLoader.getInstance().isModLoaded(modId)
    }

    override fun isDedicatedServer(): Boolean {
        return FabricLoader.getInstance().environmentType == EnvType.SERVER
    }

    override fun languageCode(): String {
        if (FabricLoader.getInstance().environmentType != EnvType.CLIENT) {
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
        return "Fabric"
    }

    override fun mcVersion(): String {
        return try {
            SharedConstants.getCurrentVersion().name
        } catch (_: Exception) {
            ""
        }
    }

    override fun modVersion(modId: String?): String {
        return FabricLoader.getInstance().getModContainer(modId)
            .map { it.metadata.version.friendlyString }
            .orElse("")
    }

    override fun modFilePath(modId: String?): Path? {
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

    override fun playerName(): String {
        if (FabricLoader.getInstance().environmentType != EnvType.CLIENT) {
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
        if (FabricLoader.getInstance().environmentType != EnvType.CLIENT) {
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
            PlatformServices.setProvider(FabricPlatformServices())
        }
    }
}
