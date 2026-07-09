package com.cozooo.dlc_manager.client.server

class DlcServerRemoteItem {
    @JvmField var key: String? = null
    @JvmField var name: String? = null
    @JvmField var type: String? = null
    @JvmField var folder: String? = null
    @JvmField var hidden: Boolean = false
    @JvmField var displayNames: Map<String, String> = emptyMap()
    @JvmField var tips: Map<String, String> = emptyMap()
    @JvmField var totalBytes: Long = 0
    @JvmField var downloadedBytes: Long = 0
    @JvmField var done: Boolean = false
    @JvmField var success: Boolean = false
    @JvmField var error: String? = null
}
