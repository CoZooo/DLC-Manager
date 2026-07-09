package com.cozooo.dlc_manager.client.screen

import com.cozooo.dlc_manager.core.DlcItem
import com.cozooo.dlc_manager.core.DlcRuntimeState
import com.cozooo.dlc_manager.platform.DlcPlatformServices

object DlcDisplayNameUtil {
    @JvmStatic
    fun resolveDisplayName(item: DlcItem?): String {
        if (item == null) {
            return ""
        }
        return resolveDisplayName(item, item.getName())
    }

    @JvmStatic
    fun resolveDisplayName(identifier: String?): String {
        if (identifier.isNullOrBlank()) {
            return ""
        }
        val item = DlcRuntimeState.getItemByIdentifier(identifier)
        if (item == null) {
            return identifier
        }
        return resolveDisplayName(item, identifier)
    }

    private fun resolveDisplayName(item: DlcItem, fallback: String?): String {
        val names = item.getDisplayNames()
        if (names.isNullOrEmpty()) {
            return fallback ?: ""
        }
        val lang = DlcPlatformServices.languageCode()
        if (names.containsKey(lang)) {
            return names[lang] ?: ""
        }
        if (names.containsKey("en_us")) {
            return names["en_us"] ?: ""
        }
        return names.values.firstOrNull() ?: (fallback ?: "")
    }
}
