package com.cozooo.dlc_manager.client.server

class DlcServerInfo {
    @JvmField var baseVersion: String? = null
    @JvmField var dlcVersion: String? = null
    @JvmField var items: List<DlcServerItem> = emptyList()
    @JvmField var remoteMissing: Int = 0
    @JvmField var requiredMissing: Int = 0
    @JvmField var hasRequired: Boolean = false
    @JvmField var hasTaczMod: Boolean = false
    @JvmField var modpackUrl: String? = null
    @JvmField var updateGitRepo: String? = null
}
