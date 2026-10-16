package com.cozooo.dlc_manager.client.server

class DlcServerRequiredItem {
    @JvmField var id: String? = null
    @JvmField var identifier: String? = null
    @JvmField var name: String? = null
    @JvmField var displayNames: MutableMap<String, String> = HashMap()
    @JvmField var tips: MutableMap<String, String> = HashMap()
    @JvmField var dependencies: MutableList<String> = ArrayList()
    @JvmField var hidden: Boolean = false
    @JvmField var hasFile: Boolean = false
    @JvmField var needsApply: Boolean = false
    @JvmField var applied: Boolean = false
    @JvmField var fileName: String? = null
}
