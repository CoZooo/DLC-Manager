package com.cozooo.dlc_manager.client.preset

import java.util.Locale

enum class DlcPresetStatus {
    ON,
    PART,
    OFF,
    ;

    fun isEnabled(): Boolean {
        return this == ON || this == PART
    }

    fun toConfigValue(): String {
        return when (this) {
            ON -> "On"
            PART -> "Part"
            OFF -> "Off"
        }
    }

    companion object {
        @JvmStatic
        fun fromString(raw: String?): DlcPresetStatus {
            if (raw == null) {
                return OFF
            }
            return when (raw.trim().lowercase(Locale.ROOT)) {
                "on" -> ON
                "part" -> PART
                "off" -> OFF
                else -> OFF
            }
        }
    }
}
