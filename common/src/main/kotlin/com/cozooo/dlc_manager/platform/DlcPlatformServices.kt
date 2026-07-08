package com.cozooo.dlc_manager.platform

import java.nio.file.Files
import java.nio.file.Path

object DlcPlatformServices {
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

    @JvmStatic
    fun isModLoaded(modId: String?): Boolean {
        return get().isModLoaded(modId)
    }

    @JvmStatic
    fun isDedicatedServer(): Boolean {
        return get().isDedicatedServer()
    }

    @JvmStatic
    fun gameDir(): Path {
        return get().gameDir()
    }

    @JvmStatic
    fun configDir(): Path {
        return get().configDir()
    }

    @JvmStatic
    fun languageCode(): String {
        return get().languageCode()
    }

    @JvmStatic
    fun loaderName(): String {
        return get().loaderName()
    }

    @JvmStatic
    fun mcVersion(): String {
        return get().mcVersion()
    }

    @JvmStatic
    fun modVersion(modId: String?): String {
        return get().modVersion(modId)
    }

    @JvmStatic
    fun modFilePath(modId: String?): Path? {
        return get().modFilePath(modId)
    }

    @JvmStatic
    fun isResourcePackSelected(fileName: String?): Boolean {
        return get().isResourcePackSelected(fileName)
    }

    @JvmStatic
    fun playerName(): String {
        return get().playerName()
    }

    @JvmStatic
    fun playerUuid(): String {
        return get().playerUuid()
    }

    @JvmStatic
    fun readLanguageFromOptions(gameDir: Path?): String? {
        if (gameDir == null) {
            return null
        }
        return try {
            val options = gameDir.resolve("options.txt")
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
