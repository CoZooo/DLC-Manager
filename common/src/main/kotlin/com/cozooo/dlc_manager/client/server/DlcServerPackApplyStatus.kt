package com.cozooo.dlc_manager.client.server

class DlcServerPackApplyStatus {
    @JvmField var running: Boolean = false
    @JvmField var finished: Boolean = false
    @JvmField var success: Boolean = false
    @JvmField var canceled: Boolean = false
    @JvmField var paused: Boolean = false
    @JvmField var totalCount: Int = 0
    @JvmField var completedCount: Int = 0
    @JvmField var progress: Float = -1f
    @JvmField var message: String? = null
    @JvmField var error: String? = null
}
