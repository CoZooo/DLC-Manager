package com.cozooo.dlc_manager.platform

import com.cozooo.dlc_manager.bootstrap.DlcDistributionPaths

import com.cozooo.dlc_manager.client.DlcMinecraftSupport
import dev.architectury.platform.Platform
import dev.architectury.utils.Env
import net.minecraft.client.Minecraft
import java.nio.file.Path

class DlcPlatformServicesProvider : DlcPlatformServices.Provider {
    override fun gameDir(): Path {
        return platformPath(Platform::getGameFolder) ?: Path.of("").toAbsolutePath().normalize()
    }

    override fun configDir(): Path {
        return platformPath(Platform::getConfigFolder) ?: gameDir().resolve("config")
    }

    override fun isModLoaded(modId: String?): Boolean {
        if (modId.isNullOrBlank()) {
            return false
        }
        return try {
            Platform.isModLoaded(modId)
        } catch (_: Throwable) {
            false
        }
    }

    override fun isDedicatedServer(): Boolean {
        return platformEnvironment() == Env.SERVER
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
        return try {
            when {
                Platform.isFabric() -> "Fabric"
                //? if >=1.21.1 {
                /*Platform.isNeoForge() -> "NeoForge"
                Platform.isMinecraftForge() -> "Forge"
                *///?} else {
                Platform.isForge() -> "Forge"
                //?}
                else -> ""
            }
        } catch (_: Throwable) {
            ""
        }
    }

    override fun mcVersion(): String {
        return try {
            Platform.getMinecraftVersion()
        } catch (_: Throwable) {
            ""
        }
    }

    override fun modVersion(modId: String?): String {
        if (modId.isNullOrBlank()) {
            return ""
        }
        return try {
            Platform.getOptionalMod(modId).map { it.version }.orElse("")
        } catch (_: Throwable) {
            ""
        }
    }

    override fun modFilePath(modId: String?): Path? {
        if (modId.isNullOrBlank()) {
            return null
        }
        return try {
            DlcDistributionPaths.originalModFile(modId, Platform.getOptionalMod(modId)
                .flatMap { mod -> mod.filePaths.stream().findFirst() }
                .orElse(null))
        } catch (_: Throwable) {
            null
        }
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
            return platformEnvironment() == Env.CLIENT
        }

        private fun platformEnvironment(): Env? {
            return try {
                Platform.getEnvironment()
            } catch (_: Throwable) {
                null
            }
        }

        private fun platformPath(resolve: () -> Path): Path? {
            return try {
                resolve()
            } catch (_: Throwable) {
                null
            }
        }
    }
}
