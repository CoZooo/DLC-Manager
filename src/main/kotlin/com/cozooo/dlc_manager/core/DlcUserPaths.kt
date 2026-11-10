package com.cozooo.dlc_manager.core

import java.nio.file.Path

object DlcUserPaths {
    private const val OVERRIDE_PROPERTY = "dlc_manager.user.dir"
    private const val DIRECTORY = ".dlc_manager"
    private const val CONFIG_DIRECTORY = "config"
    private const val STATE_DIRECTORY = "state"

    @JvmStatic
    fun userDir(): Path {
        val override = System.getProperty(OVERRIDE_PROPERTY)
        if (!override.isNullOrBlank()) {
            return Path.of(override).toAbsolutePath().normalize()
        }
        val home = System.getProperty("user.home")
        val base = if (home.isNullOrBlank()) {
            Path.of(System.getProperty("java.io.tmpdir", "."))
        } else {
            Path.of(home)
        }
        return base.resolve(DIRECTORY).toAbsolutePath().normalize()
    }

    @JvmStatic
    fun configDir(): Path = userDir().resolve(CONFIG_DIRECTORY)

    @JvmStatic
    fun stateDir(): Path = userDir().resolve(STATE_DIRECTORY)

    @JvmStatic
    fun configFile(fileName: String): Path = configDir().resolve(fileName)

    @JvmStatic
    fun stateFile(fileName: String): Path = stateDir().resolve(fileName)
}
