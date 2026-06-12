package com.cozooo.dlc_manager.platform

import java.lang.reflect.Method
import java.nio.file.Path
import java.util.Locale

object CrossPlatformRuntime {
    private const val ARCH_PLATFORM_CLASS = "dev.architectury.platform.Platform"

    private val platformIsModLoaded: Method?
    private val platformGetEnv: Method?
    private val platformGameDir: Method?
    private val platformConfigDir: Method?

    init {
        var isModLoaded: Method? = null
        var getEnv: Method? = null
        var gameDir: Method? = null
        var configDir: Method? = null
        try {
            val platformClass = Class.forName(ARCH_PLATFORM_CLASS)
            isModLoaded = platformClass.getMethod("isModLoaded", String::class.java)
            getEnv = findMethod(platformClass, "getEnv", "getEnvironment")
            gameDir = findMethod(platformClass, "getGameFolder", "getGameDir", "getGameDirectory")
            configDir = findMethod(platformClass, "getConfigFolder", "getConfigDir", "getConfigDirectory")
        } catch (_: Throwable) {
            isModLoaded = null
            getEnv = null
            gameDir = null
            configDir = null
        }
        platformIsModLoaded = isModLoaded
        platformGetEnv = getEnv
        platformGameDir = gameDir
        platformConfigDir = configDir
    }

    @JvmStatic
    fun isModLoaded(modId: String?): Boolean {
        if (platformIsModLoaded != null) {
            try {
                val result = platformIsModLoaded.invoke(null, modId)
                if (result is Boolean) {
                    return result
                }
            } catch (_: Throwable) {
            }
        }
        return PlatformServices.get().isModLoaded(modId)
    }

    @JvmStatic
    fun isDedicatedServer(): Boolean {
        if (platformGetEnv != null) {
            try {
                val env = platformGetEnv.invoke(null)
                if (env != null) {
                    val value = env.toString().uppercase(Locale.ROOT)
                    if (value.contains("SERVER")) {
                        return true
                    }
                    if (value.contains("CLIENT")) {
                        return false
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return !isClientByClassPresence()
    }

    @JvmStatic
    fun gameDir(): Path {
        if (platformGameDir != null) {
            try {
                val result = platformGameDir.invoke(null)
                if (result is Path) {
                    return result
                }
            } catch (_: Throwable) {
            }
        }
        return PlatformServices.get().gameDir()
    }

    @JvmStatic
    fun configDir(): Path {
        if (platformConfigDir != null) {
            try {
                val result = platformConfigDir.invoke(null)
                if (result is Path) {
                    return result
                }
            } catch (_: Throwable) {
            }
        }
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

    private fun findMethod(type: Class<*>, vararg names: String): Method? {
        for (name in names) {
            try {
                return type.getMethod(name)
            } catch (_: NoSuchMethodException) {
            }
        }
        return null
    }

    private fun isClientByClassPresence(): Boolean {
        return try {
            Class.forName("net.minecraft.client.Minecraft")
            true
        } catch (_: Throwable) {
            false
        }
    }
}
