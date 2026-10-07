package com.cozooo.dlc_manager.client.server

class DlcServerFileEntry {
    @JvmField var name: String? = null
    @JvmField var dir: Boolean = false
    @JvmField var size: Long = 0
    @JvmField var modified: Long = 0
    @JvmField var hash: String? = null
}
