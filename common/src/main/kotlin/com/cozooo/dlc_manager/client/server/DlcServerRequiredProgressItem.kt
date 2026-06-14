package com.cozooo.dlc_manager.client.server

class DlcServerRequiredProgressItem {
    @JvmField
    var id: String? = null

    @JvmField
    var name: String? = null

    @JvmField
    var totalBytes: Long = 0

    @JvmField
    var downloadedBytes: Long = 0

    @JvmField
    var done: Boolean = false

    @JvmField
    var success: Boolean = false

    @JvmField
    var error: String? = null
}
