package com.cozooo.dlc_manager.client.server

class DlcServerItem {
    @JvmField
    var id: String? = null

    @JvmField
    var name: String? = null

    @JvmField
    var identifier: String? = null

    @JvmField
    var path: String? = null

    @JvmField
    var type: String? = null

    @JvmField
    var enabled: Boolean = false

    @JvmField
    var displayNames: Map<String, String>? = null

    @JvmField
    var tips: Map<String, String>? = null

    @JvmField
    var dependencies: List<String>? = null

    @JvmField
    var conflicts: List<String>? = null

    @JvmField
    var allowEnableByAll: Boolean = false

    @JvmField
    var alarm: Boolean = false
}
