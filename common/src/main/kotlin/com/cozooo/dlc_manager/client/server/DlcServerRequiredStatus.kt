package com.cozooo.dlc_manager.client.server

class DlcServerRequiredStatus {
    @JvmField
    var running: Boolean = false

    @JvmField
    var finished: Boolean = false

    @JvmField
    var success: Boolean = false

    @JvmField
    var totalCount: Int = 0

    @JvmField
    var completedCount: Int = 0

    @JvmField
    var totalBytes: Long = 0

    @JvmField
    var downloadedBytes: Long = 0

    @JvmField
    var items: MutableList<DlcServerRequiredProgressItem> = ArrayList()

    @JvmField
    var error: String? = null
}
