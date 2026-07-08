package com.cozooo.dlc_manager.client.server

class DlcServerUpdateInfo {
    @JvmField var name: String? = null
    @JvmField var baseOld: String? = null
    @JvmField var baseNew: String? = null
    @JvmField var dlcOld: String? = null
    @JvmField var dlcNew: String? = null
    @JvmField var hidden: Boolean = false
    @JvmField var changelog: MutableMap<String, String>? = null
}
