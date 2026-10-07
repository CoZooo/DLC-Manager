package com.cozooo.dlc_manager.client.screen

import java.util.Locale

object DlcUiSearch {
    @JvmStatic
    fun matches(query: String?, vararg fields: String?): Boolean {
        if (query.isNullOrBlank()) return true
        val haystack = fields.filterNotNull().joinToString(" ").lowercase(Locale.ROOT)
        return query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).all { haystack.contains(it) }
    }
}
