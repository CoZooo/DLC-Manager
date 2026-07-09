package com.cozooo.dlc_manager.server

import com.cozooo.dlc_manager.core.DlcPaths
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

object DlcServerProperties {
    @JvmStatic
    fun readRconInfo(): RconInfo {
        return readRconInfo(DlcPaths.gameDir())
    }

    @JvmStatic
    fun readRconInfo(gameDir: Path): RconInfo {
        val props = Properties()
        val path = gameDir.resolve("server.properties")
        if (Files.exists(path)) {
            try {
                Files.newBufferedReader(path).use { reader -> props.load(reader) }
            } catch (_: IOException) {
            }
        }
        val enabled = asBoolean(props.getProperty("enable-rcon"), false)
        val port = clampInt(asInt(props.getProperty("rcon.port"), 25575), 1, 65535, 25575)
        val password = props.getProperty("rcon.password", "")
        val passwordSet = !password.isNullOrBlank()
        return RconInfo(enabled, port, passwordSet)
    }

    private fun asBoolean(value: String?, fallback: Boolean): Boolean {
        if (value == null) {
            return fallback
        }
        return "true".equals(value, ignoreCase = true) || value.trim() == "1"
    }

    private fun asInt(value: String?, fallback: Int): Int {
        if (value == null) {
            return fallback
        }
        return try {
            value.trim().toInt()
        } catch (_: NumberFormatException) {
            fallback
        }
    }

    private fun clampInt(value: Int, min: Int, max: Int, fallback: Int): Int {
        return if (value < min || value > max) fallback else value
    }

    class RconInfo(
        @JvmField val enabled: Boolean,
        @JvmField val port: Int,
        @JvmField val passwordSet: Boolean,
    )
}
