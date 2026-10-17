package com.cozooo.dlc_manager.client.server

class DlcServerEntry {
    @JvmField var name: String? = null
    @JvmField var host: String? = null
    @JvmField var port: Int = 0
    @JvmField var password: String? = null
    @JvmField var useTcp: Boolean = true
    @JvmField var usePortMultiplexer: Boolean = false
    @JvmField var rconPort: Int = 25575
    @JvmField var rconPassword: String? = ""

    fun getDisplayName(): String {
        if (name != null && !name!!.isBlank()) {
            return name!!
        }
        val hostValue = host ?: ""
        return "$hostValue:$port"
    }

    fun getKey(): String {
        val hostValue = host ?: ""
        return "$hostValue:$port"
    }
}
