package com.cozooo.dlc_manager.client.server

import com.cozooo.dlc_manager.client.update.DlcUpdateMeta

class DlcServerUpdateState {
    @JvmField var baseVersion: String? = null
    @JvmField var dlcVersion: String? = null
    @JvmField var rollbackFrom: String? = null
    @JvmField var modpackUrl: String? = null
    @JvmField var updateGitRepo: String? = null
    @JvmField var history: MutableList<DlcUpdateMeta?> = ArrayList()
    @JvmField var updates: MutableList<DlcServerUpdateInfo?> = ArrayList()
}
