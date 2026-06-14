package com.cozooo.dlc_manager.client.server

import java.util.regex.Pattern

object DlcServerVersionUtil {
    private val versionPattern: Pattern = Pattern.compile("([0-9][A-Za-z0-9._-]*)\\+([0-9][A-Za-z0-9._-]*)")

    @JvmStatic
    fun parse(text: String?): VersionPair? {
        if (text.isNullOrBlank()) {
            return null
        }
        val matcher = versionPattern.matcher(text)
        if (!matcher.find()) {
            return null
        }
        val base = matcher.group(1)
        val dlc = matcher.group(2)
        if (base.isNullOrBlank() || dlc.isNullOrBlank()) {
            return null
        }
        return VersionPair(base, dlc)
    }

    class VersionPair {
        @JvmField
        val base: String

        @JvmField
        val dlc: String

        constructor(base: String, dlc: String) {
            this.base = base
            this.dlc = dlc
        }
    }
}
