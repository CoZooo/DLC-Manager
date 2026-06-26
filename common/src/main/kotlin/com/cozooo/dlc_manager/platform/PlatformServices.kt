package com.cozooo.dlc_manager.platform

import java.nio.file.Path

object PlatformServices {
    interface Provider {
        fun gameDir(): Path
        fun configDir(): Path
        fun isModLoaded(modId: String?): Boolean
        fun isDedicatedServer(): Boolean
        fun languageCode(): String
        fun loaderName(): String
        fun mcVersion(): String
        fun modVersion(modId: String?): String
        fun modFilePath(modId: String?): Path?
        fun isResourcePackSelected(fileName: String?): Boolean {
            return false
        }
        fun playerName(): String
        fun playerUuid(): String
    }

    private var provider: Provider? = null

    @JvmStatic
    fun setProvider(provider: Provider?) {
        this.provider = provider
    }

    @JvmStatic
    fun get(): Provider {
        return provider ?: DefaultProvider
    }

    private object DefaultProvider : Provider {
        override fun gameDir(): Path {
            return Path.of("").toAbsolutePath().normalize()
        }

        override fun configDir(): Path {
            return gameDir().resolve("config")
        }

        override fun isModLoaded(modId: String?): Boolean {
            return false
        }

        override fun isDedicatedServer(): Boolean {
            return false
        }

        override fun languageCode(): String {
            return "en_us"
        }

        override fun loaderName(): String {
            return ""
        }

        override fun mcVersion(): String {
            return ""
        }

        override fun modVersion(modId: String?): String {
            return ""
        }

        override fun modFilePath(modId: String?): Path? {
            return null
        }

        override fun playerName(): String {
            return ""
        }

        override fun playerUuid(): String {
            return ""
        }
    }
}
