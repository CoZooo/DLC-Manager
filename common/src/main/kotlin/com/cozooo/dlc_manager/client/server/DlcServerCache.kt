package com.cozooo.dlc_manager.client.server

import java.util.concurrent.ConcurrentHashMap

object DlcServerCache {
    private val cache = ConcurrentHashMap<String, DlcServerInfo>()

    @JvmStatic
    fun get(key: String?): DlcServerInfo? {
        if (key == null) {
            return null
        }
        return cache[key]
    }

    @JvmStatic
    fun put(key: String?, info: DlcServerInfo?) {
        if (key == null || info == null) {
            return
        }
        cache[key] = info
    }
}
