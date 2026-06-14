package com.cozooo.dlc_manager.platform

import java.nio.file.Path

object CrossPlatformRuntime {
    @JvmStatic
    fun isModLoaded(modId: String?): Boolean {
        return PlatformServices.get().isModLoaded(modId)
    }

    @JvmStatic
    fun isDedicatedServer(): Boolean {
        return PlatformServices.get().isDedicatedServer()
    }

    @JvmStatic
    fun gameDir(): Path {
        return PlatformServices.get().gameDir()
    }

    @JvmStatic
    fun configDir(): Path {
        return PlatformServices.get().configDir()
    }

    @JvmStatic
    fun languageCode(): String {
        return PlatformServices.get().languageCode()
    }

    @JvmStatic
    fun loaderName(): String {
        return PlatformServices.get().loaderName()
    }

    @JvmStatic
    fun mcVersion(): String {
        return PlatformServices.get().mcVersion()
    }

    @JvmStatic
    fun modVersion(modId: String?): String {
        return PlatformServices.get().modVersion(modId)
    }

    @JvmStatic
    fun modFilePath(modId: String?): Path? {
        return PlatformServices.get().modFilePath(modId)
    }

    @JvmStatic
    fun playerName(): String {
        return PlatformServices.get().playerName()
    }

    @JvmStatic
    fun playerUuid(): String {
        return PlatformServices.get().playerUuid()
    }
}
